package com.example.resume_chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@Getter
@Setter
public class TelegramProperties {
    private String token = "";
    private String username = "Rezzhbot";
    private boolean pollingEnabled = true;
    private int pollingTimeoutSeconds = 30;

    public boolean isConfigured() {
        return token != null && !token.isBlank() && !token.equalsIgnoreCase("YOUR_TELEGRAM_BOT_TOKEN_HERE");
    }
}
