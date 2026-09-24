package com.example.resume_chatbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreBreakdown {
    private int overallScore; // 0 - 100

    private double skillScore;
    private double maxSkillScore;

    private double keywordScore;
    private double maxKeywordScore;

    private double experienceScore;
    private double maxExperienceScore;

    private double educationScore;
    private double maxEducationScore;

    private double completenessScore;
    private double maxCompletenessScore;

    private String label; // "ATS-like compatibility score (application-generated estimate)"
    private String explanation;
}
