package com.example.resume_chatbot.service;

import com.example.resume_chatbot.exception.ResumeParsingException;
import com.example.resume_chatbot.model.Resume;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ooxml.POIXMLDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ResumeParserService {

    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB limit

    // Contact Information Regex Patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}|(?:\\+91[-.\\s]?)?[6-9]\\d{9}");
    private static final Pattern LINKEDIN_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?linkedin\\.com/in/[a-zA-Z0-9_\\-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/[a-zA-Z0-9_\\-]+", Pattern.CASE_INSENSITIVE);

    // Section Header Regex Patterns
    private static final Pattern EDUCATION_HEADER = Pattern.compile(
            "^(?:education|academic qualifications|academics|qualifications)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILLS_HEADER = Pattern.compile(
            "^(?:technical skills|skills & technologies|skills|technologies|core competencies|programming skills)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPERIENCE_HEADER = Pattern.compile(
            "^(?:work experience|professional experience|experience|employment history|internships|internship experience)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECTS_HEADER = Pattern.compile(
            "^(?:projects|academic projects|personal projects|technical projects|key projects)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CERTIFICATIONS_HEADER = Pattern.compile(
            "^(?:certifications|certificates|licenses & certifications|training & certifications)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACHIEVEMENTS_HEADER = Pattern.compile(
            "^(?:achievements|honors & awards|awards|extracurricular activities|accomplishments)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_HEADER = Pattern.compile(
            "^(?:summary|professional summary|career objective|objective|about me|profile)\\b", Pattern.CASE_INSENSITIVE);

    // Common Curated Skills Dictionary for Extraction
    private static final List<String> KNOWN_SKILLS = Arrays.asList(
            "Java", "Spring Boot", "Spring", "Spring MVC", "Spring Security", "Hibernate", "JPA",
            "Python", "Django", "Flask", "FastAPI",
            "C", "C++", "C#", ".NET", "ASP.NET",
            "JavaScript", "TypeScript", "React", "React.js", "Angular", "Vue", "Vue.js", "Node.js", "Express", "Next.js",
            "SQL", "MySQL", "PostgreSQL", "Oracle", "MongoDB", "Redis", "Cassandra", "DynamoDB", "Elasticsearch", "SQLite",
            "Docker", "Kubernetes", "AWS", "Azure", "GCP", "Google Cloud", "CI/CD", "Jenkins", "Git", "GitHub", "GitLab",
            "Linux", "Unix", "Bash", "Shell Scripting", "Terraform", "Ansible",
            "REST APIs", "REST", "RESTful", "GraphQL", "Microservices", "Kafka", "RabbitMQ", "ActiveMQ",
            "HTML", "HTML5", "CSS", "CSS3", "Bootstrap", "Tailwind CSS",
            "Data Structures", "Algorithms", "Object-Oriented Programming", "OOP", "System Design", "Design Patterns",
            "Unit Testing", "JUnit", "Mockito", "Selenium", "Postman", "Maven", "Gradle", "Agile", "Scrum", "Jira"
    );

    /**
     * Extracts raw text from a PDF input stream.
     */
    public String extractTextFromPdf(InputStream pdfInputStream) {
        if (pdfInputStream == null) {
            throw new ResumeParsingException("PDF input stream is null.");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            int totalBytes = 0;

            while ((nRead = pdfInputStream.read(data, 0, data.length)) != -1) {
                totalBytes += nRead;
                if (totalBytes > MAX_FILE_SIZE_BYTES) {
                    throw new ResumeParsingException("PDF file exceeds the maximum allowed size of 10MB.");
                }
                buffer.write(data, 0, nRead);
            }

            byte[] pdfBytes = buffer.toByteArray();
            if (pdfBytes.length == 0) {
                throw new ResumeParsingException("The uploaded file is empty.");
            }

            try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
                if (document.isEncrypted()) {
                    throw new ResumeParsingException("The uploaded PDF is password-protected. Please upload an unprotected PDF.");
                }

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String text = stripper.getText(document);

                if (text == null || text.trim().isEmpty() || text.trim().length() < 20) {
                    throw new ResumeParsingException("I couldn't extract readable text from this PDF. It might be scanned or image-only. Please upload a text-based PDF resume.");
                }

                log.info("Successfully extracted {} characters from PDF", text.length());
                return text;
            }
        } catch (ResumeParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse PDF document: {}", e.getMessage(), e);
            throw new ResumeParsingException("Failed to read the PDF file: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts raw text from a DOCX or DOC input stream.
     */
    public String extractTextFromDoc(InputStream docInputStream, String filename) {
        if (docInputStream == null) {
            throw new ResumeParsingException("Document input stream is null.");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            int totalBytes = 0;

            while ((nRead = docInputStream.read(data, 0, data.length)) != -1) {
                totalBytes += nRead;
                if (totalBytes > MAX_FILE_SIZE_BYTES) {
                    throw new ResumeParsingException("Uploaded file exceeds the maximum allowed size of 10MB.");
                }
                buffer.write(data, 0, nRead);
            }

            byte[] bytes = buffer.toByteArray();
            if (bytes.length == 0) {
                throw new ResumeParsingException("The uploaded file is empty.");
            }

            String text = null;
            String lower = filename != null ? filename.toLowerCase() : "";

            if (lower.endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(POIXMLDocument.openPackage(new java.io.ByteArrayInputStream(bytes)))) {
                    XWPFWordExtractor extractor = new XWPFWordExtractor(document);
                    text = extractor.getText();
                }
            } else if (lower.endsWith(".doc")) {
                try (HWPFDocument document = new HWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
                    WordExtractor extractor = new WordExtractor(document);
                    text = extractor.getText();
                }
            } else {
                // fallback: try to read as UTF-8 plain text
                text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }

            if (text == null || text.trim().isEmpty() || text.trim().length() < 20) {
                throw new ResumeParsingException("I couldn't extract readable text from the uploaded document. Please upload a text-based DOC/DOCX/TXT or PDF resume.");
            }

            log.info("Successfully extracted {} characters from document", text.length());
            return text;
        } catch (ResumeParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse document: {}", e.getMessage(), e);
            throw new ResumeParsingException("Failed to read the document: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to detect input type by filename and extract text accordingly.
     */
    public String extractTextFromInputStream(InputStream is, String filename, String contentType) {
        if (filename == null) filename = "";
        String lower = filename.toLowerCase();

        try {
            if (lower.endsWith(".pdf") || (contentType != null && contentType.toLowerCase().contains("pdf"))) {
                return extractTextFromPdf(is);
            } else if (lower.endsWith(".docx") || lower.endsWith(".doc") || (contentType != null && (contentType.toLowerCase().contains("word") || contentType.toLowerCase().contains("officedocument")))) {
                return extractTextFromDoc(is, filename);
            } else if (lower.endsWith(".txt") || (contentType != null && contentType.toLowerCase().startsWith("text"))) {
                // read as plain text
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[8192];
                int nRead;
                int totalBytes = 0;
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    totalBytes += nRead;
                    if (totalBytes > MAX_FILE_SIZE_BYTES) {
                        throw new ResumeParsingException("Uploaded file exceeds the maximum allowed size of 10MB.");
                    }
                    buffer.write(data, 0, nRead);
                }
                String text = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                if (text == null || text.trim().isEmpty()) {
                    throw new ResumeParsingException("The uploaded text file is empty or unreadable.");
                }
                return text;
            } else {
                // fallback: try DOCX/DOC first then PDF
                try {
                    return extractTextFromDoc(is, filename);
                } catch (Exception ex) {
                    // rewind not possible; best-effort: try reading as text
                    try (InputStream is2 = new java.io.ByteArrayInputStream(new byte[0])) {
                        return extractTextFromDoc(is, filename);
                    } catch (Exception ex2) {
                        throw new ResumeParsingException("Unsupported file format. Please upload PDF, DOCX, DOC, or TXT.");
                    }
                }
            }
        } catch (ResumeParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new ResumeParsingException("Failed to extract text from file: " + e.getMessage(), e);
        }
    }

    /**
     * Parses raw resume text into structured Resume model.
     */
    public Resume parseResume(String rawText, String filename) {
        if (rawText == null || rawText.isBlank()) {
            throw new ResumeParsingException("Resume text is empty.");
        }

        String normalizedText = rawText.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalizedText.split("\n");

        Resume.ResumeBuilder builder = Resume.builder()
                .id(UUID.randomUUID().toString())
                .filename(filename != null ? filename : "resume.pdf")
                .rawText(normalizedText);

        // Extract Contacts
        builder.email(extractFirstMatch(normalizedText, EMAIL_PATTERN));
        builder.phone(extractFirstMatch(normalizedText, PHONE_PATTERN));
        builder.linkedIn(extractFirstMatch(normalizedText, LINKEDIN_PATTERN));
        builder.gitHub(extractFirstMatch(normalizedText, GITHUB_PATTERN));
        builder.name(extractCandidateName(lines));

        // Segment Sections
        Map<String, List<String>> sections = segmentSections(lines);

        builder.summary(String.join("\n", sections.getOrDefault("SUMMARY", Collections.emptyList())));
        builder.education(sections.getOrDefault("EDUCATION", Collections.emptyList()));
        builder.workExperience(sections.getOrDefault("EXPERIENCE", Collections.emptyList()));
        builder.projects(sections.getOrDefault("PROJECTS", Collections.emptyList()));
        builder.certifications(sections.getOrDefault("CERTIFICATIONS", Collections.emptyList()));
        builder.achievements(sections.getOrDefault("ACHIEVEMENTS", Collections.emptyList()));

        // Extract Skills from explicit skills section + full text scan
        List<String> rawSkillsLines = sections.getOrDefault("SKILLS", Collections.emptyList());
        List<String> extractedSkills = extractSkills(rawSkillsLines, normalizedText);
        builder.skills(extractedSkills);

        return builder.build();
    }

    private String extractCandidateName(String[] lines) {
        for (int i = 0; i < Math.min(lines.length, 10); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("resume") || line.equalsIgnoreCase("curriculum vitae") || line.equalsIgnoreCase("cv")) {
                continue;
            }
            if (EMAIL_PATTERN.matcher(line).find() || PHONE_PATTERN.matcher(line).find()) {
                continue;
            }
            if (line.startsWith("http") || line.contains("linkedin.com") || line.contains("github.com")) {
                continue;
            }
            // Candidate name is usually 2-4 words, starts with letters, length between 3 and 40 chars
            if (line.matches("^[A-Za-z]+([ .'-][A-Za-z]+){1,4}$") && line.length() >= 3 && line.length() <= 40) {
                return line;
            }
        }
        return "Candidate";
    }

    private String extractFirstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    private Map<String, List<String>> segmentSections(String[] lines) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String currentSection = "HEADER";
        sections.put(currentSection, new ArrayList<>());

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            String cleanLine = line.replaceAll("[:\\-_=]+$", "").trim();

            if (EDUCATION_HEADER.matcher(cleanLine).matches()) {
                currentSection = "EDUCATION";
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else if (SKILLS_HEADER.matcher(cleanLine).matches()) {
                currentSection = "SKILLS";
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else if (EXPERIENCE_HEADER.matcher(cleanLine).matches()) {
                currentSection = "EXPERIENCE";
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else if (PROJECTS_HEADER.matcher(cleanLine).matches()) {
                currentSection = "PROJECTS";
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else if (CERTIFICATIONS_HEADER.matcher(cleanLine).matches()) {
                currentSection = "CERTIFICATIONS";
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else if (ACHIEVEMENTS_HEADER.matcher(cleanLine).matches()) {
                currentSection = "ACHIEVEMENTS";
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else if (SUMMARY_HEADER.matcher(cleanLine).matches()) {
                currentSection = "SUMMARY";
                sections.putIfAbsent(currentSection, new ArrayList<>());
            } else {
                sections.computeIfAbsent(currentSection, k -> new ArrayList<>()).add(line);
            }
        }

        return sections;
    }

    private List<String> extractSkills(List<String> skillsSectionLines, String fullText) {
        Set<String> detectedSkills = new LinkedHashSet<>();
        String skillsText = String.join(" ", skillsSectionLines);

        for (String knownSkill : KNOWN_SKILLS) {
            if (containsSkill(skillsText, knownSkill) || containsSkill(fullText, knownSkill)) {
                detectedSkills.add(knownSkill);
            }
        }

        // Also parse comma/bullet separated items in skills section
        for (String line : skillsSectionLines) {
            String[] tokens = line.split("[,|•;•/]");
            for (String token : tokens) {
                String clean = token.replaceAll("^[-*•\\s]+", "").replaceAll("[:\\-_]+.*$", "").trim();
                if (clean.length() >= 2 && clean.length() <= 30 && !clean.contains("http")) {
                    for (String known : KNOWN_SKILLS) {
                        if (clean.equalsIgnoreCase(known)) {
                            detectedSkills.add(known);
                        }
                    }
                }
            }
        }

        return new ArrayList<>(detectedSkills);
    }

    private boolean containsSkill(String text, String skill) {
        if (text == null || skill == null) return false;
        // Exact boundary matching to prevent Java matching JavaScript, C matching C++, etc.
        String escaped = Pattern.quote(skill);
        Pattern pattern = Pattern.compile("(?i)(?<=^|[^a-zA-Z0-9#+])" + escaped + "(?=[^a-zA-Z0-9#+]|$)");
        return pattern.matcher(text).find();
    }
}
