package com.example.resume_chatbot.controller;

import com.example.resume_chatbot.exception.InvalidInputException;
import com.example.resume_chatbot.model.*;
import com.example.resume_chatbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
@Slf4j
public class ResumeApiController {

    private final ResumeParserService resumeParserService;
    private final SkillMatchingService skillMatchingService;
    private final ResumeAnalysisService resumeAnalysisService;
    private final ResumeComparisonService resumeComparisonService;
    private final CourseRecommendationService courseRecommendationService;
    private final ChatbotService chatbotService;

    /**
     * Upload and analyze a single resume against a target role or job description.
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeAnalysis> analyzeResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "jobDescription", defaultValue = "Software Engineer") String jobDescriptionText) {

        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("Please upload a valid non-empty resume file (PDF, DOCX, DOC or TXT).");
        }

        try (InputStream is = file.getInputStream()) {
            String text = resumeParserService.extractTextFromInputStream(is, file.getOriginalFilename(), file.getContentType());
            Resume resume = resumeParserService.parseResume(text, file.getOriginalFilename());
            JobDescription jd = skillMatchingService.parseJobDescription(jobDescriptionText);
            ResumeAnalysis analysis = resumeAnalysisService.analyze(resume, jd);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Failed to analyze resume: {}", e.getMessage());
            throw new InvalidInputException(e.getMessage());
        }
    }

    /**
     * Upload and compare multiple resumes against a job description.
     */
    @PostMapping(value = "/compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeComparison> compareResumes(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "jobDescription", defaultValue = "Software Engineer") String jobDescriptionText) {

        if (files == null || files.isEmpty()) {
            throw new InvalidInputException("Please upload at least two resume files for comparison.");
        }

        List<Resume> resumes = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try (InputStream is = file.getInputStream()) {
                String text = resumeParserService.extractTextFromInputStream(is, file.getOriginalFilename(), file.getContentType());
                resumes.add(resumeParserService.parseResume(text, file.getOriginalFilename()));
            } catch (Exception e) {
                log.error("Failed parsing file {}: {}", file.getOriginalFilename(), e.getMessage());
            }
        }

        if (resumes.isEmpty()) {
            throw new InvalidInputException("No readable resumes could be extracted from the uploaded files. Please upload PDF, DOCX, DOC or TXT files.");
        }

        JobDescription jd = skillMatchingService.parseJobDescription(jobDescriptionText);
        ResumeComparison comparison = resumeComparisonService.compareResumes(resumes, jd);
        return ResponseEntity.ok(comparison);
    }

    /**
     * Compare skills directly.
     */
    @PostMapping("/skills")
    public ResponseEntity<SkillMatch> matchSkills(@RequestBody Map<String, Object> request) {
        Object skillsObj = request.get("skills");
        String jdText = (String) request.getOrDefault("jobDescription", "Software Engineer");

        List<String> resumeSkills = new ArrayList<>();
        if (skillsObj instanceof List<?>) {
            for (Object item : (List<?>) skillsObj) {
                if (item != null) resumeSkills.add(item.toString());
            }
        }

        JobDescription jd = skillMatchingService.parseJobDescription(jdText);
        SkillMatch match = skillMatchingService.matchSkills(resumeSkills, jd.getRequiredSkills(), null);
        return ResponseEntity.ok(match);
    }

    /**
     * Get course recommendations for missing skills.
     */
    @PostMapping("/courses")
    public ResponseEntity<List<CourseRecommendation>> getCourses(@RequestBody Map<String, List<String>> request) {
        List<String> skills = request.getOrDefault("skills", List.of("Docker", "AWS"));
        List<CourseRecommendation> courses = courseRecommendationService.recommendCourses(skills);
        return ResponseEntity.ok(courses);
    }

    /**
     * Ask a career question via REST API.
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> request) {
        Long chatId = request.containsKey("chatId") ? Long.valueOf(request.get("chatId").toString()) : 1L;
        String question = (String) request.getOrDefault("question", "How can I improve my resume?");

        String answer = chatbotService.askQuestion(chatId, question);
        return ResponseEntity.ok(Map.of("question", question, "answer", answer));
    }
}
