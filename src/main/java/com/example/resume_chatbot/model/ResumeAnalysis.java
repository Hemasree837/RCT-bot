package com.example.resume_chatbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysis {
    private String id;
    private Resume resume;
    private JobDescription jobDescription;
    private SkillMatch skillMatch;
    private ScoreBreakdown scoreBreakdown;

    @Builder.Default
    private List<String> suggestions = new ArrayList<>();

    @Builder.Default
    private List<CourseRecommendation> recommendedCourses = new ArrayList<>();

    @Builder.Default
    private LocalDateTime analyzedAt = LocalDateTime.now();
}
