package com.example.resume_chatbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    private Long chatId;

    @Builder.Default
    private UserState state = UserState.IDLE;

    private Resume activeResume;

    @Builder.Default
    private List<Resume> comparisonResumes = new ArrayList<>();

    private ResumeAnalysis lastAnalysis;

    @Builder.Default
    private List<ChatMessage> conversationHistory = new ArrayList<>();

    @Builder.Default
    private Instant lastActiveTime = Instant.now();

    public void reset() {
        this.state = UserState.IDLE;
        this.activeResume = null;
        this.comparisonResumes.clear();
        this.lastActiveTime = Instant.now();
    }

    public void addMessage(String role, String content) {
        if (conversationHistory == null) {
            conversationHistory = new ArrayList<>();
        }
        conversationHistory.add(ChatMessage.builder().role(role).content(content).build());
        if (conversationHistory.size() > 20) {
            conversationHistory.remove(0); // keep recent context
        }
        this.lastActiveTime = Instant.now();
    }
}
