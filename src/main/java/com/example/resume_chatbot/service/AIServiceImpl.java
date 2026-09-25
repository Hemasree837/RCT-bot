package com.example.resume_chatbot.service;

import com.example.resume_chatbot.config.AIProperties;
import com.example.resume_chatbot.model.ChatMessage;
import com.example.resume_chatbot.model.CourseRecommendation;
import com.example.resume_chatbot.model.ResumeAnalysis;
import com.example.resume_chatbot.model.ScoreBreakdown;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceImpl implements AIService {

    private final AIProperties aiProperties;
    private final CourseRecommendationService courseRecommendationService;

    @Override
    public String answerCareerQuestion(String question, ResumeAnalysis contextAnalysis, List<ChatMessage> history) {
        if (question == null || question.isBlank()) {
            return "Please ask a career, resume, or skill-related question.";
        }

        // If external AI provider is configured with an API key, attempt LLM call
        if (aiProperties.isConfigured()) {
            try {
                return callExternalAi(question, contextAnalysis, history);
            } catch (Exception e) {
                log.warn("External AI call failed ({}), falling back to deterministic career advisor.", e.getMessage());
            }
        }

        // Deterministic Fallback Career Advisor Engine
        return generateDeterministicAnswer(question, contextAnalysis);
    }

    private String generateDeterministicAnswer(String question, ResumeAnalysis contextAnalysis) {
        String q = question.toLowerCase();

        // 1. Missing skills queries
        if (q.contains("missing") || q.contains("lack") || q.contains("gap")) {
            if (contextAnalysis != null && contextAnalysis.getSkillMatch() != null) {
                List<String> missing = contextAnalysis.getSkillMatch().getMissingSkills();
                if (missing.isEmpty()) {
                    return "Great news! Based on your target role, your resume matches all the core required technical skills identified in the job description.";
                }
                return String.format(
                        "Based on your latest analysis against '%s', your missing key skills are:\n• %s\n\nTip: You can use /courses to view verified learning paths for these topics.",
                        contextAnalysis.getJobDescription().getRoleTitle(),
                        String.join("\n• ", missing)
                );
            }
            return "You haven't uploaded or analyzed a resume yet. Send me your resume (PDF, DOCX, DOC or TXT) and a target job role to identify your skill gaps!";
        }

        // 2. Score explanation
        if (q.contains("why") && (q.contains("score") || q.contains("rating") || q.contains("compatibility"))
                || q.contains("explain score") || q.contains("how was my score")) {
            if (contextAnalysis != null && contextAnalysis.getScoreBreakdown() != null) {
                ScoreBreakdown b = contextAnalysis.getScoreBreakdown();
                return String.format(
                        "Here is why your ATS-like Compatibility Score is %d/100:\n\n" +
                                "• Skill Match: %.1f / %.1f (Matched %d of %d required skills)\n" +
                                "• Keyword Alignment: %.1f / %.1f\n" +
                                "• Experience & Projects: %.1f / %.1f\n" +
                                "• Education: %.1f / %.1f\n" +
                                "• Readability & Completeness: %.1f / %.1f\n\n" +
                                "Note: This is an application-generated estimate, not an official ATS score.",
                        b.getOverallScore(),
                        b.getSkillScore(), b.getMaxSkillScore(),
                        contextAnalysis.getSkillMatch().getMatchedSkills().size(),
                        contextAnalysis.getJobDescription().getRequiredSkills().size(),
                        b.getKeywordScore(), b.getMaxKeywordScore(),
                        b.getExperienceScore(), b.getMaxExperienceScore(),
                        b.getEducationScore(), b.getMaxEducationScore(),
                        b.getCompletenessScore(), b.getMaxCompletenessScore()
                );
            }
            return "Please analyze a resume first to see a detailed score breakdown.";
        }

        // 3. How to improve resume
        if (q.contains("improve") || q.contains("suggestion") || q.contains("better") || q.contains("feedback")) {
            if (contextAnalysis != null && !contextAnalysis.getSuggestions().isEmpty()) {
                return "Key suggestions to improve your resume for " + contextAnalysis.getJobDescription().getRoleTitle() + ":\n\n" +
                        contextAnalysis.getSuggestions().stream().map(s -> "• " + s).collect(Collectors.joining("\n\n"));
            }
            return "To get tailored improvement suggestions, please upload your resume first.";
        }

        // 4. What to learn next / learning order
        if (q.contains("learn next") || q.contains("learn first") || q.contains("what should i learn") || q.contains("roadmap")) {
            if (contextAnalysis != null && !contextAnalysis.getSkillMatch().getMissingSkills().isEmpty()) {
                List<String> missing = contextAnalysis.getSkillMatch().getMissingSkills();
                String first = missing.get(0);
                String rest = missing.size() > 1 ? missing.get(1) : null;

                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Recommended Learning Sequence:\n\n1. Prioritize **%s** first. It is directly tied to the primary requirements of the %s role.\n",
                        first, contextAnalysis.getJobDescription().getRoleTitle()));
                if (rest != null) {
                    sb.append(String.format("2. Next, focus on **%s** to round out your development workflow and architecture skills.\n", rest));
                }
                sb.append("\nType /courses to get verified official tutorials and guides for these skills.");
                return sb.toString();
            }
            return "For modern software development, master these foundational pillars in order:\n1. Core Language (e.g. Java / Python) & Data Structures\n2. Web Framework & REST APIs (e.g. Spring Boot)\n3. Relational Database & SQL (e.g. PostgreSQL / MySQL)\n4. Containerization (Docker) & CI/CD basics.";
        }

        // 5. Course recommendations
        if (q.contains("course") || q.contains("resource") || q.contains("tutorial") || q.contains("study")) {
            List<String> targetSkills = (contextAnalysis != null && !contextAnalysis.getSkillMatch().getMissingSkills().isEmpty())
                    ? contextAnalysis.getSkillMatch().getMissingSkills()
                    : List.of("Docker", "Spring Boot", "Git");

            List<CourseRecommendation> recs = courseRecommendationService.recommendCourses(targetSkills);
            StringBuilder sb = new StringBuilder("Here are verified, official educational resources for you:\n\n");
            for (int i = 0; i < recs.size(); i++) {
                CourseRecommendation r = recs.get(i);
                sb.append(String.format("%d. %s (%s)\n   Link: %s\n   %s\n\n",
                        i + 1, r.getTitle(), r.getProvider(), r.getUrl(), r.getDescription()));
            }
            return sb.toString().trim();
        }

        // 6. Projects to highlight
        if (q.contains("project") || q.contains("portfolio")) {
            if (contextAnalysis != null) {
                return String.format(
                        "For a %s role, build and highlight a project that demonstrates:\n" +
                                "1. A RESTful API built with Spring Boot / Java.\n" +
                                "2. Database design with MySQL/PostgreSQL including indexed queries and transactions.\n" +
                                "3. Containerization using a Dockerfile and docker-compose.\n" +
                                "4. Clear measurable outcomes (e.g., 'achieved 99%% test coverage with JUnit', 'reduced query latency by 40%%').",
                        contextAnalysis.getJobDescription().getRoleTitle()
                );
            }
            return "Top project advice for software engineering resumes:\n• Build 2-3 production-quality applications with clean git commits and a comprehensive README.\n• Deploy your project on the cloud (Render/AWS/Railway).\n• Write automated unit & integration tests.\n• Quantify results and architecture decisions.";
        }

        // 7. Skill sufficiency (e.g., "Do I have enough Java skills?")
        if (q.contains("enough") || q.contains("ready") || q.contains("eligible")) {
            if (contextAnalysis != null) {
                int score = contextAnalysis.getScoreBreakdown().getOverallScore();
                if (score >= 75) {
                    return String.format("Yes! Your profile has a strong %d/100 compatibility with the %s role. Focus on practicing interview questions, system design basics, and presenting your project architecture clearly.",
                            score, contextAnalysis.getJobDescription().getRoleTitle());
                } else {
                    return String.format("You have a good start (%d/100), but closing your missing skill gaps (%s) will significantly increase your callback rate for %s roles.",
                            score, String.join(", ", contextAnalysis.getSkillMatch().getMissingSkills()), contextAnalysis.getJobDescription().getRoleTitle());
                }
            }
            return "Upload your resume and target role to get an exact compatibility assessment!";
        }

        // Default Fallback
        return "I can help you review your resume, suggest high-impact improvements, explain your compatibility score, identify missing skills, or recommend courses. What specific question do you have about your resume or target role?";
    }

    private String callExternalAi(String question, ResumeAnalysis contextAnalysis, List<ChatMessage> history) {
        // Generic LLM caller via Spring RestClient for OpenAI-compatible or Gemini endpoints
        String prompt = buildSystemPrompt(question, contextAnalysis, history);
        log.info("Dispatching prompt to external AI provider: {}", aiProperties.getProvider());

        // For OpenAI or Gemini compatible API
        RestClient restClient = RestClient.builder().build();

        if ("gemini".equalsIgnoreCase(aiProperties.getProvider())) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + aiProperties.getModel() + ":generateContent?key=" + aiProperties.getApiKey();
            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            Map<?, ?> response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("candidates")) {
                List<?> candidates = (List<?>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<?, ?> cand = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> content = (Map<?, ?>) cand.get("content");
                    List<?> parts = (List<?>) content.get("parts");
                    Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                    return (String) firstPart.get("text");
                }
            }
        }

        throw new IllegalStateException("Unhandled AI provider or empty response from " + aiProperties.getProvider());
    }

    private String buildSystemPrompt(String question, ResumeAnalysis contextAnalysis, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert Career Assistant and ATS Resume Reviewer.\n");
        sb.append("Answer the user's career or resume question concisely (max 3-4 paragraphs), optimized for Telegram reading.\n");

        if (contextAnalysis != null) {
            sb.append("\nCandidate Resume Context:\n");
            sb.append("Target Role: ").append(contextAnalysis.getJobDescription().getRoleTitle()).append("\n");
            sb.append("ATS-like Score: ").append(contextAnalysis.getScoreBreakdown().getOverallScore()).append("/100\n");
            sb.append("Matched Skills: ").append(String.join(", ", contextAnalysis.getSkillMatch().getMatchedSkills())).append("\n");
            sb.append("Missing Skills: ").append(String.join(", ", contextAnalysis.getSkillMatch().getMissingSkills())).append("\n");
        }

        sb.append("\nUser Question: ").append(question).append("\n");
        return sb.toString();
    }
}
