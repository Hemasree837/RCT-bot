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
public class Resume {
    private String id;
    private String filename;
    private String name;
    private String email;
    private String phone;
    private String linkedIn;
    private String gitHub;
    private String summary;

    @Builder.Default
    private List<String> education = new ArrayList<>();

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Builder.Default
    private List<String> workExperience = new ArrayList<>();

    @Builder.Default
    private List<String> projects = new ArrayList<>();

    @Builder.Default
    private List<String> certifications = new ArrayList<>();

    @Builder.Default
    private List<String> achievements = new ArrayList<>();

    private String rawText;
}
