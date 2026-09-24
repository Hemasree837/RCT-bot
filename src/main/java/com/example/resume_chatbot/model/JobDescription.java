package com.example.resume_chatbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescription {
    private String roleTitle;
    private String rawText;

    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();

    @Builder.Default
    private List<String> keywords = new ArrayList<>();
}
