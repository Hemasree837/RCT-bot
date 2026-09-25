package com.example.resume_chatbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role; // "user", "assistant", "system"
    private String content;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
