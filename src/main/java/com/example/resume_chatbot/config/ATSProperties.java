package com.example.resume_chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ats.weights")
@Getter
@Setter
public class ATSProperties {
    private double skill = 0.50;
    private double keyword = 0.20;
    private double experience = 0.15;
    private double education = 0.10;
    private double completeness = 0.05;
}
