package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.JobDescription;
import com.example.resume_chatbot.model.SkillMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillMatchingServiceTest {

    private SkillMatchingService skillMatchingService;

    @BeforeEach
    void setUp() {
        skillMatchingService = new SkillMatchingService();
    }

    @Test
    @DisplayName("Should accurately identify matched and missing skills without false positives")
    void testExactSkillMatching() {
        List<String> resumeSkills = List.of("Java", "Spring Boot", "SQL", "Git");
        List<String> requiredSkills = List.of("Java", "Spring Boot", "SQL", "Docker", "AWS");
        String resumeText = "Experienced Java developer with Spring Boot and SQL.";

        SkillMatch match = skillMatchingService.matchSkills(resumeSkills, requiredSkills, resumeText);

        assertNotNull(match);
        assertEquals(3, match.getMatchedSkills().size());
        assertTrue(match.getMatchedSkills().contains("Java"));
        assertTrue(match.getMatchedSkills().contains("Spring Boot"));
        assertTrue(match.getMatchedSkills().contains("SQL"));

        assertEquals(2, match.getMissingSkills().size());
        assertTrue(match.getMissingSkills().contains("Docker"));
        assertTrue(match.getMissingSkills().contains("AWS"));

        assertEquals(60.0, match.getMatchPercentage(), 0.1);
    }

    @Test
    @DisplayName("Should prevent substring false positives: 'Java' should not match 'JavaScript'")
    void testNoSubstringFalsePositive() {
        String text = "I am a JavaScript frontend engineer with Node.js expertise.";
        assertFalse(skillMatchingService.containsExactSkill(text, "Java"));
        assertTrue(skillMatchingService.containsExactSkill(text, "JavaScript"));
    }

    @Test
    @DisplayName("Should parse predefined role title into expected technical skills")
    void testParseJobDescriptionRole() {
        JobDescription jd = skillMatchingService.parseJobDescription("Java Backend Developer");

        assertNotNull(jd);
        assertTrue(jd.getRequiredSkills().contains("Java"));
        assertTrue(jd.getRequiredSkills().contains("Spring Boot"));
        assertTrue(jd.getRequiredSkills().contains("SQL"));
        assertTrue(jd.getRequiredSkills().contains("REST APIs"));
    }

    @Test
    @DisplayName("Should detect related skills when exact skill is missing")
    void testRelatedSkillsDetection() {
        List<String> resumeSkills = List.of("Java", "Spring", "MySQL");
        List<String> requiredSkills = List.of("Java", "Spring Boot", "Docker");

        SkillMatch match = skillMatchingService.matchSkills(resumeSkills, requiredSkills, "Java and Spring developer");

        assertTrue(match.getMissingSkills().contains("Spring Boot"));
        assertFalse(match.getRelatedSkills().isEmpty());
        assertTrue(match.getRelatedSkills().stream().anyMatch(s -> s.contains("related to Spring Boot")));
    }
}
