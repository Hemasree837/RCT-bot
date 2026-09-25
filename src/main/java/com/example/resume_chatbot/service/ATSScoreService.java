package com.example.resume_chatbot.service;

import com.example.resume_chatbot.config.ATSProperties;
import com.example.resume_chatbot.model.JobDescription;
import com.example.resume_chatbot.model.Resume;
import com.example.resume_chatbot.model.ScoreBreakdown;
import com.example.resume_chatbot.model.SkillMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ATSScoreService {

    private final ATSProperties properties;

    private static final Pattern METRICS_PATTERN = Pattern.compile(
            "\\b(?:\\d+[%+]?|\\$\\d+|increased|reduced|improved|scaled|optimized|delivered)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DEGREE_PATTERN = Pattern.compile(
            "\\b(?:b\\.tech|b\\.e|bca|mca|b\\.s|m\\.s|m\\.tech|bachelor|master|degree|computer science|information technology|engineering)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Calculates an ATS-like compatibility score (0-100) with a transparent breakdown.
     */
    public ScoreBreakdown calculateScore(Resume resume, JobDescription jobDescription, SkillMatch skillMatch) {
        double maxSkill = properties.getSkill() * 100.0;
        double maxKeyword = properties.getKeyword() * 100.0;
        double maxExp = properties.getExperience() * 100.0;
        double maxEdu = properties.getEducation() * 100.0;
        double maxComp = properties.getCompleteness() * 100.0;

        // 1. Skill Score (Weight: 50%)
        int totalReq = jobDescription.getRequiredSkills() == null ? 0 : jobDescription.getRequiredSkills().size();
        int matchedCount = skillMatch.getMatchedSkills() == null ? 0 : skillMatch.getMatchedSkills().size();
        double skillRatio = totalReq > 0 ? (double) matchedCount / totalReq : 1.0;
        double skillScore = Math.min(maxSkill, skillRatio * maxSkill);

        // 2. Keyword / JD Alignment Score (Weight: 20%)
        double keywordScore = 0.0;
        List<String> keywords = jobDescription.getKeywords();
        if (keywords != null && !keywords.isEmpty() && resume.getRawText() != null) {
            String lowerText = resume.getRawText().toLowerCase();
            long foundKeywords = keywords.stream().filter(lowerText::contains).count();
            double kwRatio = (double) foundKeywords / Math.min(keywords.size(), 15);
            keywordScore = Math.min(maxKeyword, kwRatio * maxKeyword);
        } else {
            keywordScore = maxKeyword * 0.5; // default moderate score
        }

        // 3. Experience / Project Relevance Score (Weight: 15%)
        double expScore = 0.0;
        boolean hasWork = resume.getWorkExperience() != null && !resume.getWorkExperience().isEmpty();
        boolean hasProjects = resume.getProjects() != null && !resume.getProjects().isEmpty();

        if (hasWork && hasProjects) {
            expScore = maxExp * 0.8;
        } else if (hasWork || hasProjects) {
            expScore = maxExp * 0.6;
        } else {
            expScore = maxExp * 0.2;
        }

        // Check for action metrics/outcomes in resume text (numbers, percentages, scale)
        if (resume.getRawText() != null && METRICS_PATTERN.matcher(resume.getRawText()).find()) {
            expScore = Math.min(maxExp, expScore + (maxExp * 0.2));
        }

        // 4. Education Relevance Score (Weight: 10%)
        double eduScore = 0.0;
        boolean hasEdu = resume.getEducation() != null && !resume.getEducation().isEmpty();
        if (hasEdu) {
            eduScore = maxEdu * 0.7;
            String eduText = String.join(" ", resume.getEducation());
            if (DEGREE_PATTERN.matcher(eduText).find()) {
                eduScore = maxEdu;
            }
        } else if (resume.getRawText() != null && DEGREE_PATTERN.matcher(resume.getRawText()).find()) {
            eduScore = maxEdu * 0.8;
        }

        // 5. Completeness & Readability Score (Weight: 5%)
        double compScore = 0.0;
        double compStep = maxComp / 5.0;
        if (resume.getName() != null && !resume.getName().equalsIgnoreCase("Candidate")) compScore += compStep;
        if (resume.getEmail() != null) compScore += compStep;
        if (resume.getPhone() != null) compScore += compStep;
        if (resume.getGitHub() != null || resume.getLinkedIn() != null) compScore += compStep;
        if (hasProjects || hasWork) compScore += compStep;

        double total = skillScore + keywordScore + expScore + eduScore + compScore;
        int overall = (int) Math.round(Math.min(100.0, Math.max(0.0, total)));

        return ScoreBreakdown.builder()
                .overallScore(overall)
                .skillScore(Math.round(skillScore * 10.0) / 10.0)
                .maxSkillScore(maxSkill)
                .keywordScore(Math.round(keywordScore * 10.0) / 10.0)
                .maxKeywordScore(maxKeyword)
                .experienceScore(Math.round(expScore * 10.0) / 10.0)
                .maxExperienceScore(maxExp)
                .educationScore(Math.round(eduScore * 10.0) / 10.0)
                .maxEducationScore(maxEdu)
                .completenessScore(Math.round(compScore * 10.0) / 10.0)
                .maxCompletenessScore(maxComp)
                .label("ATS-like compatibility score (application-generated estimate)")
                .explanation("This score is an application-generated estimate evaluating your resume against the target role requirements using configurable weighted factors.")
                .build();
    }
}
