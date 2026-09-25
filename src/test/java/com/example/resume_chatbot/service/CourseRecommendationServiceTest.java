package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.CourseRecommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseRecommendationServiceTest {

    private CourseRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new CourseRecommendationService();
    }

    @Test
    @DisplayName("Should recommend 1 to 3 verified courses with valid hyperlinks")
    void testCourseRecommendations() {
        List<String> missingSkills = List.of("Docker", "AWS", "Kubernetes", "Redis");
        List<CourseRecommendation> recs = service.recommendCourses(missingSkills);

        assertNotNull(recs);
        assertTrue(recs.size() >= 1 && recs.size() <= 3, "Recommendations must be between 1 and 3");

        for (CourseRecommendation r : recs) {
            assertNotNull(r.getTitle(), "Title must not be null");
            assertNotNull(r.getProvider(), "Provider must not be null");
            assertNotNull(r.getUrl(), "URL must not be null");
            assertTrue(r.getUrl().startsWith("https://"), "URL must be a valid HTTPS link: " + r.getUrl());
            assertNotNull(r.getDescription(), "Description must not be null");
        }
    }

    @Test
    @DisplayName("Should match official Docker tutorial when Docker is missing")
    void testDockerRecommendation() {
        List<CourseRecommendation> recs = service.recommendCourses(List.of("Docker"));
        assertFalse(recs.isEmpty());
        CourseRecommendation docker = recs.get(0);
        assertEquals("Docker", docker.getTargetSkill());
        assertTrue(docker.getUrl().contains("docs.docker.com"));
    }
}
