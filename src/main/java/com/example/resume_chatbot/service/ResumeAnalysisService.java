package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeAnalysisService {

    private final SkillMatchingService skillMatchingService;
    private final ATSScoreService atsScoreService;
    private final CourseRecommendationService courseRecommendationService;

    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "\\b(?:\\d+%|\\d+x|\\$\\d+|\\d+\\s*(?:users|requests|ms|seconds|qps|queries))\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Performs end-to-end resume analysis against a JobDescription.
     */
    public ResumeAnalysis analyze(Resume resume, JobDescription jobDescription) {
        log.info("Analyzing resume [{}] against role [{}]", resume.getFilename(), jobDescription.getRoleTitle());

        // 1. Skill Matching
        SkillMatch skillMatch = skillMatchingService.matchSkills(
                resume.getSkills(),
                jobDescription.getRequiredSkills(),
                resume.getRawText()
        );

        // 2. ATS-like Compatibility Score
        ScoreBreakdown scoreBreakdown = atsScoreService.calculateScore(resume, jobDescription, skillMatch);

        // 3. Actionable Resume Suggestions
        List<String> suggestions = generateSuggestions(resume, jobDescription, skillMatch);

        // 4. Course Recommendations (1-3 courses for missing skills)
        List<CourseRecommendation> recommendedCourses = courseRecommendationService.recommendCourses(skillMatch.getMissingSkills());

        return ResumeAnalysis.builder()
                .id(UUID.randomUUID().toString())
                .resume(resume)
                .jobDescription(jobDescription)
                .skillMatch(skillMatch)
                .scoreBreakdown(scoreBreakdown)
                .suggestions(suggestions)
                .recommendedCourses(recommendedCourses)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private List<String> generateSuggestions(Resume resume, JobDescription jobDescription, SkillMatch skillMatch) {
        List<String> suggestions = new ArrayList<>();

        // 1. Missing Skills Suggestions
        if (!skillMatch.getMissingSkills().isEmpty()) {
            List<String> topMissing = skillMatch.getMissingSkills().subList(0, Math.min(3, skillMatch.getMissingSkills().size()));
            suggestions.add(String.format(
                    "Missing Target Skills: The role explicitly looks for %s. If you have hands-on experience with them, make sure to add them. If not, consider prioritizing them in your learning roadmap.",
                    String.join(", ", topMissing)
            ));
        }

        // 2. Metrics and Measurable Outcomes in Projects
        boolean hasMetrics = false;
        if (resume.getProjects() != null && !resume.getProjects().isEmpty()) {
            String projectText = String.join(" ", resume.getProjects());
            hasMetrics = METRIC_PATTERN.matcher(projectText).find();
        } else if (resume.getRawText() != null) {
            hasMetrics = METRIC_PATTERN.matcher(resume.getRawText()).find();
        }

        if (!hasMetrics) {
            suggestions.add("Add Measurable Outcomes: Strengthen your project bullet points with metrics (e.g., 'reduced API response time by 35%', 'processed 10k+ records', 'served 500+ active users').");
        }

        // 3. Technical Skills Contextualization
        if (resume.getSkills() != null && !resume.getSkills().isEmpty() && resume.getProjects() != null) {
            String projectContent = String.join(" ", resume.getProjects()).toLowerCase();
            List<String> unsupported = new ArrayList<>();
            for (String s : resume.getSkills()) {
                if (!projectContent.contains(s.toLowerCase())) {
                    unsupported.add(s);
                }
            }
            if (!unsupported.isEmpty() && unsupported.size() <= 4) {
                suggestions.add(String.format(
                        "Demonstrate Skills in Projects: You listed %s in your skills section. Mention specifically how you utilized them inside your project descriptions.",
                        String.join(", ", unsupported)
                ));
            }
        }

        // 4. Contact & Online Presence
        if (resume.getGitHub() == null) {
            suggestions.add("Include GitHub: Adding a direct link to your active GitHub profile allows hiring managers to inspect your code quality.");
        }
        if (resume.getLinkedIn() == null) {
            suggestions.add("Include LinkedIn: Add your customized LinkedIn profile link for quick recruiter verification.");
        }

        // 5. Action Verbs & Structure
        if (resume.getProjects() == null || resume.getProjects().isEmpty()) {
            suggestions.add("Highlight Key Projects: Add a dedicated 'Projects' section detailing 2-3 substantial full-stack or backend projects showcasing architecture and technologies used.");
        }

        return suggestions;
    }
}
