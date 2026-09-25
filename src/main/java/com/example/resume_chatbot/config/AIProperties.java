package com.example.resume_chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai")
@Getter
@Setter
public class AIProperties {
    private String provider = "mock";
    private String apiKey = "";
    private String model = "gemini-1.5-flash";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("YOUR_AI_API_KEY_HERE");
    }
}
