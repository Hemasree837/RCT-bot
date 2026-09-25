package com.example.resume_chatbot.service;

import com.example.resume_chatbot.exception.ResumeParsingException;
import com.example.resume_chatbot.model.Resume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResumeParserServiceTest {

    private ResumeParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new ResumeParserService();
    }

    @Test
    @DisplayName("Should extract contact info, education, skills, and projects from raw text")
    void testParseResumeSections() {
        String sampleResumeText = """
                Jane Doe
                Email: jane.doe@example.com
                Phone: +1-555-987-6543
                linkedin.com/in/janedoe
                github.com/janedoe

                SUMMARY
                Passionate software engineer building resilient backend systems.

                EDUCATION
                B.Tech in Computer Science and Engineering
                XYZ University, CGPA 8.8

                TECHNICAL SKILLS
                Java, Spring Boot, SQL, PostgreSQL, Docker, Git, REST APIs, Microservices

                EXPERIENCE
                Software Engineer Intern at ABC Solutions
                • Built microservices using Spring Boot and PostgreSQL.
                • Improved API latency by 25%.

                PROJECTS
                Distributed Task Queue
                • Implemented in Java using Redis and Spring Boot.
                • Processed 50,000 tasks per hour.
                """;

        Resume resume = parserService.parseResume(sampleResumeText, "jane_doe_resume.pdf");

        assertNotNull(resume);
        assertEquals("Jane Doe", resume.getName());
        assertEquals("jane.doe@example.com", resume.getEmail());
        assertEquals("+1-555-987-6543", resume.getPhone());
        assertTrue(resume.getLinkedIn().contains("linkedin.com/in/janedoe"));
        assertTrue(resume.getGitHub().contains("github.com/janedoe"));

        assertFalse(resume.getSkills().isEmpty());
        assertTrue(resume.getSkills().contains("Java"));
        assertTrue(resume.getSkills().contains("Spring Boot"));
        assertTrue(resume.getSkills().contains("SQL"));
        assertTrue(resume.getSkills().contains("Docker"));

        assertFalse(resume.getEducation().isEmpty());
        assertFalse(resume.getProjects().isEmpty());
        assertFalse(resume.getWorkExperience().isEmpty());
    }

    @Test
    @DisplayName("Should throw ResumeParsingException when text is null or blank")
    void testParseEmptyResume() {
        assertThrows(ResumeParsingException.class, () -> parserService.parseResume("", "empty.pdf"));
        assertThrows(ResumeParsingException.class, () -> parserService.parseResume(null, "null.pdf"));
    }
}
