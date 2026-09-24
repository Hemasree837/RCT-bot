package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.JobDescription;
import com.example.resume_chatbot.model.SkillMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SkillMatchingService {

    // Predefined role profiles when user inputs a target role title
    private static final Map<String, List<String>> ROLE_SKILLS_MAP = new HashMap<>();

    // Partial/related skill relationships: Parent/General -> Related Technologies
    private static final Map<String, List<String>> RELATED_SKILLS_MAP = new HashMap<>();

    // Master list of recognized skills for extraction from free-text Job Descriptions
    private static final List<String> ALL_TECH_SKILLS = Arrays.asList(
            "Java", "Spring Boot", "Spring MVC", "Spring Security", "Spring", "Hibernate", "JPA",
            "Python", "Django", "Flask", "FastAPI",
            "C", "C++", "C#", ".NET",
            "JavaScript", "TypeScript", "React", "React.js", "Angular", "Vue", "Node.js", "Express", "Next.js",
            "SQL", "MySQL", "PostgreSQL", "Oracle", "MongoDB", "Redis", "Cassandra", "DynamoDB", "Elasticsearch",
            "Docker", "Kubernetes", "AWS", "Azure", "GCP", "CI/CD", "Jenkins", "Git", "GitHub", "GitLab",
            "Linux", "Bash", "Terraform", "Ansible",
            "REST APIs", "REST", "RESTful", "GraphQL", "Microservices", "Kafka", "RabbitMQ",
            "Data Structures", "Algorithms", "System Design", "Design Patterns", "OOP",
            "Unit Testing", "JUnit", "Mockito", "Maven", "Gradle", "Agile", "Scrum"
    );

    static {
        // Role-based profiles
        ROLE_SKILLS_MAP.put("java backend developer", Arrays.asList("Java", "Spring Boot", "REST APIs", "SQL", "Git", "Microservices", "Docker", "Unit Testing"));
        ROLE_SKILLS_MAP.put("java developer", Arrays.asList("Java", "Spring Boot", "REST APIs", "SQL", "Git", "OOP", "Maven"));
        ROLE_SKILLS_MAP.put("backend developer", Arrays.asList("REST APIs", "SQL", "Git", "Docker", "Microservices", "Unit Testing", "System Design"));
        ROLE_SKILLS_MAP.put("frontend developer", Arrays.asList("JavaScript", "TypeScript", "React", "HTML", "CSS", "Git", "REST APIs"));
        ROLE_SKILLS_MAP.put("full stack developer", Arrays.asList("Java", "Spring Boot", "React", "JavaScript", "SQL", "REST APIs", "Git", "Docker"));
        ROLE_SKILLS_MAP.put("python developer", Arrays.asList("Python", "Django", "FastAPI", "SQL", "Git", "REST APIs", "Docker"));
        ROLE_SKILLS_MAP.put("devops engineer", Arrays.asList("Docker", "Kubernetes", "AWS", "CI/CD", "Linux", "Terraform", "Git", "Bash"));
        ROLE_SKILLS_MAP.put("data engineer", Arrays.asList("Python", "SQL", "Kafka", "PostgreSQL", "Docker", "Git", "AWS"));
        ROLE_SKILLS_MAP.put("android developer", Arrays.asList("Java", "Kotlin", "Git", "REST APIs", "OOP"));
        ROLE_SKILLS_MAP.put("software engineer", Arrays.asList("Data Structures", "Algorithms", "Git", "SQL", "OOP", "REST APIs", "Unit Testing"));

        // Related skills mappings
        RELATED_SKILLS_MAP.put("Spring Boot", Arrays.asList("Spring", "Spring MVC", "Hibernate"));
        RELATED_SKILLS_MAP.put("Spring", Arrays.asList("Spring Boot", "Spring MVC"));
        RELATED_SKILLS_MAP.put("SQL", Arrays.asList("MySQL", "PostgreSQL", "Oracle", "SQLite"));
        RELATED_SKILLS_MAP.put("MySQL", Arrays.asList("SQL", "PostgreSQL"));
        RELATED_SKILLS_MAP.put("PostgreSQL", Arrays.asList("SQL", "MySQL"));
        RELATED_SKILLS_MAP.put("Docker", Arrays.asList("Kubernetes", "CI/CD"));
        RELATED_SKILLS_MAP.put("Kubernetes", Arrays.asList("Docker"));
        RELATED_SKILLS_MAP.put("AWS", Arrays.asList("Azure", "GCP", "Google Cloud"));
        RELATED_SKILLS_MAP.put("React", Arrays.asList("React.js", "JavaScript", "TypeScript"));
        RELATED_SKILLS_MAP.put("Node.js", Arrays.asList("Express", "JavaScript", "TypeScript"));
        RELATED_SKILLS_MAP.put("REST APIs", Arrays.asList("REST", "RESTful", "Microservices"));
    }

    /**
     * Parses raw user input (role title or job description text) into JobDescription model.
     */
    public JobDescription parseJobDescription(String input) {
        if (input == null || input.isBlank()) {
            input = "Software Engineer";
        }

        String trimmed = input.trim();
        String lowerInput = trimmed.toLowerCase();

        Set<String> requiredSkills = new LinkedHashSet<>();
        String detectedRole = trimmed;

        // Sort role keys by length descending to ensure the most specific role matches first
        List<String> sortedRoles = new ArrayList<>(ROLE_SKILLS_MAP.keySet());
        sortedRoles.sort((a, b) -> Integer.compare(b.length(), a.length()));

        for (String roleKey : sortedRoles) {
            if (lowerInput.contains(roleKey)) {
                requiredSkills.addAll(ROLE_SKILLS_MAP.get(roleKey));
                detectedRole = roleKey;
                break;
            }
        }

        // Also scan the input for explicit technical skill keywords
        for (String skill : ALL_TECH_SKILLS) {
            if (containsExactSkill(trimmed, skill)) {
                requiredSkills.add(skill);
            }
        }

        // Default fallback if no specific skills were found
        if (requiredSkills.isEmpty()) {
            requiredSkills.addAll(Arrays.asList("Java", "SQL", "Git", "REST APIs", "Data Structures"));
        }

        return JobDescription.builder()
                .roleTitle(detectedRole)
                .rawText(trimmed)
                .requiredSkills(new ArrayList<>(requiredSkills))
                .keywords(extractKeywords(trimmed))
                .build();
    }

    /**
     * Performs skill matching between resume skills and job requirements.
     */
    public SkillMatch matchSkills(List<String> resumeSkills, List<String> requiredSkills, String resumeFullText) {
        if (resumeSkills == null) resumeSkills = Collections.emptyList();
        if (requiredSkills == null) requiredSkills = Collections.emptyList();

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> related = new LinkedHashSet<>();

        for (String req : requiredSkills) {
            boolean exactMatch = false;

            // 1. Direct or case-insensitive match with resumeSkills list
            for (String rSkill : resumeSkills) {
                if (req.equalsIgnoreCase(rSkill)) {
                    exactMatch = true;
                    break;
                }
            }

            // 2. Exact word-boundary match in resume full text
            if (!exactMatch && resumeFullText != null && containsExactSkill(resumeFullText, req)) {
                exactMatch = true;
            }

            if (exactMatch) {
                matched.add(req);
            } else {
                missing.add(req);

                // Check for related skills
                List<String> relatedCandidates = RELATED_SKILLS_MAP.get(req);
                if (relatedCandidates != null) {
                    for (String candidate : relatedCandidates) {
                        for (String rSkill : resumeSkills) {
                            if (candidate.equalsIgnoreCase(rSkill)) {
                                related.add(candidate + " (related to " + req + ")");
                            }
                        }
                    }
                }
            }
        }

        double percentage = requiredSkills.isEmpty() ? 100.0 :
                Math.round(((double) matched.size() / requiredSkills.size()) * 100.0 * 10.0) / 10.0;

        return SkillMatch.builder()
                .matchedSkills(matched)
                .missingSkills(missing)
                .relatedSkills(new ArrayList<>(related))
                .matchPercentage(percentage)
                .build();
    }

    public boolean containsExactSkill(String text, String skill) {
        if (text == null || skill == null) return false;
        String escaped = Pattern.quote(skill);
        Pattern pattern = Pattern.compile("(?i)(?<=^|[^a-zA-Z0-9#+])" + escaped + "(?=[^a-zA-Z0-9#+]|$)");
        return pattern.matcher(text).find();
    }

    private List<String> extractKeywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        String[] words = text.split("[\\s,;:.()\\[\\]\\-]+");
        for (String w : words) {
            String clean = w.trim().toLowerCase();
            if (clean.length() > 3 && !isStopWord(clean)) {
                keywords.add(clean);
            }
        }
        return new ArrayList<>(keywords);
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
                "with", "that", "this", "from", "have", "will", "your", "must", "should", "could",
                "about", "their", "there", "these", "those", "would", "which", "where", "while",
                "experience", "looking", "developer", "engineer", "candidate", "responsibilities", "requirements"
        );
        return stopWords.contains(word);
    }
}
