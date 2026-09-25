package com.example.resume_chatbot.service;

import com.example.resume_chatbot.config.ATSProperties;
import com.example.resume_chatbot.model.JobDescription;
import com.example.resume_chatbot.model.Resume;
import com.example.resume_chatbot.model.ResumeComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeComparisonServiceTest {

    private ResumeComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        ATSProperties props = new ATSProperties();
        SkillMatchingService skillMatching = new SkillMatchingService();
        ATSScoreService scoreService = new ATSScoreService(props);
        CourseRecommendationService courseService = new CourseRecommendationService();
        ResumeAnalysisService analysisService = new ResumeAnalysisService(skillMatching, scoreService, courseService);
        comparisonService = new ResumeComparisonService(analysisService);
    }

    @Test
    @DisplayName("Should compare multiple resumes objectively without subjective bias")
    void testCompareResumes() {
        Resume resumeA = Resume.builder()
                .filename("alice.pdf")
                .name("Alice")
                .skills(List.of("Java", "Spring Boot", "SQL", "Docker"))
                .projects(List.of("Backend API with Spring Boot"))
                .rawText("Java Spring Boot SQL Docker developer")
                .build();

        Resume resumeB = Resume.builder()
                .filename("bob.pdf")
                .name("Bob")
                .skills(List.of("Java", "SQL"))
                .projects(List.of("Java console app"))
                .rawText("Java SQL developer")
                .build();

        JobDescription jd = JobDescription.builder()
                .roleTitle("Java Backend Engineer")
                .requiredSkills(List.of("Java", "Spring Boot", "SQL", "Docker"))
                .build();

        ResumeComparison comparison = comparisonService.compareResumes(List.of(resumeA, resumeB), jd);

        assertNotNull(comparison);
        assertEquals(2, comparison.getAnalyses().size());
        assertEquals(2, comparison.getComparisonInsights().size());
        assertNotNull(comparison.getSummary());

        // Alice should have more matched skills
        assertTrue(comparison.getAnalyses().get(0).getSkillMatch().getMatchedSkills().size() >
                comparison.getAnalyses().get(1).getSkillMatch().getMatchedSkills().size());

        // Check that summary does not claim an absolute "best"
        assertFalse(comparison.getSummary().toLowerCase().contains("best candidate"));
    }
}
