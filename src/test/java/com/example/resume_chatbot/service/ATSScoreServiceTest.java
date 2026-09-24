package com.example.resume_chatbot.service;

import com.example.resume_chatbot.config.ATSProperties;
import com.example.resume_chatbot.model.JobDescription;
import com.example.resume_chatbot.model.Resume;
import com.example.resume_chatbot.model.ScoreBreakdown;
import com.example.resume_chatbot.model.SkillMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ATSScoreServiceTest {

    private ATSScoreService atsScoreService;

    @BeforeEach
    void setUp() {
        ATSProperties props = new ATSProperties();
        props.setSkill(0.50);
        props.setKeyword(0.20);
        props.setExperience(0.15);
        props.setEducation(0.10);
        props.setCompleteness(0.05);
        atsScoreService = new ATSScoreService(props);
    }

    @Test
    @DisplayName("Should calculate score between 0 and 100 with accurate breakdown")
    void testScoreCalculation() {
        Resume resume = Resume.builder()
                .name("Alex Johnson")
                .email("alex@example.com")
                .phone("+1-555-123-4567")
                .gitHub("https://github.com/alex")
                .education(List.of("B.Tech in Computer Science"))
                .projects(List.of("E-commerce REST API backend with Spring Boot. Reduced query latency by 35%."))
                .workExperience(List.of("Backend Developer Intern at TechCorp"))
                .rawText("Java Spring Boot REST API developer with MySQL. Reduced latency by 35%.")
                .build();

        JobDescription jd = JobDescription.builder()
                .roleTitle("Java Developer")
                .requiredSkills(List.of("Java", "Spring Boot", "SQL", "Docker"))
                .keywords(List.of("backend", "api", "database", "spring"))
                .build();

        SkillMatch match = SkillMatch.builder()
                .matchedSkills(List.of("Java", "Spring Boot", "SQL"))
                .missingSkills(List.of("Docker"))
                .matchPercentage(75.0)
                .build();

        ScoreBreakdown breakdown = atsScoreService.calculateScore(resume, jd, match);

        assertNotNull(breakdown);
        assertTrue(breakdown.getOverallScore() >= 0 && breakdown.getOverallScore() <= 100);
        assertEquals(50.0, breakdown.getMaxSkillScore());
        assertEquals(20.0, breakdown.getMaxKeywordScore());
        assertEquals(15.0, breakdown.getMaxExperienceScore());
        assertEquals(10.0, breakdown.getMaxEducationScore());
        assertEquals(5.0, breakdown.getMaxCompletenessScore());

        // Check skill score calculation: 3/4 matched * 50 = 37.5
        assertEquals(37.5, breakdown.getSkillScore(), 0.1);

        // Verification of disclaimer labeling
        assertNotNull(breakdown.getLabel());
        assertTrue(breakdown.getLabel().contains("ATS-like compatibility score"));
    }
}
