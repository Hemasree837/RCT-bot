package com.example.resume_chatbot.controller;

import com.example.resume_chatbot.model.CourseRecommendation;
import com.example.resume_chatbot.model.SkillMatch;
import com.example.resume_chatbot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResumeApiControllerTest {

    private ResumeApiController controller;

    @BeforeEach
    void setUp() {
        ResumeParserService parserService = new ResumeParserService();
        SkillMatchingService skillMatchingService = new SkillMatchingService();
        ATSScoreService atsScoreService = new ATSScoreService(new com.example.resume_chatbot.config.ATSProperties());
        CourseRecommendationService courseService = new CourseRecommendationService();
        ResumeAnalysisService analysisService = new ResumeAnalysisService(skillMatchingService, atsScoreService, courseService);
        ResumeComparisonService comparisonService = new ResumeComparisonService(analysisService);
        ChatbotService chatbotService = new ChatbotService(new AIServiceImpl(new com.example.resume_chatbot.config.AIProperties(), courseService));

        controller = new ResumeApiController(
                parserService,
                skillMatchingService,
                analysisService,
                comparisonService,
                courseService,
                chatbotService
        );
    }

    @Test
    @DisplayName("POST /api/resume/skills should return matched and missing skills")
    void testMatchSkillsEndpoint() {
        Map<String, Object> request = Map.of(
                "skills", List.of("Java", "Spring Boot", "SQL"),
                "jobDescription", "Java Backend Developer with Docker and AWS"
        );

        ResponseEntity<SkillMatch> response = controller.matchSkills(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        SkillMatch match = response.getBody();
        assertNotNull(match);
        assertTrue(match.getMatchedSkills().contains("Java"));
        assertTrue(match.getMatchedSkills().contains("Spring Boot"));
        assertTrue(match.getMissingSkills().contains("Docker"));
    }

    @Test
    @DisplayName("POST /api/resume/courses should return verified recommendations")
    void testCoursesEndpoint() {
        Map<String, List<String>> request = Map.of(
                "skills", List.of("Docker", "Kubernetes")
        );

        ResponseEntity<List<CourseRecommendation>> response = controller.getCourses(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        List<CourseRecommendation> courses = response.getBody();
        assertNotNull(courses);
        assertFalse(courses.isEmpty());
        assertTrue(courses.get(0).getUrl().startsWith("https://"));
    }

    @Test
    @DisplayName("POST /api/resume/chat should answer career questions")
    void testChatEndpoint() {
        Map<String, Object> request = Map.of(
                "chatId", 12345L,
                "question", "What should I learn next for a backend developer role?"
        );

        ResponseEntity<Map<String, String>> response = controller.chat(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        Map<String, String> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("answer"));
        assertNotNull(body.get("answer"));
        assertFalse(body.get("answer").isBlank());
    }
}
