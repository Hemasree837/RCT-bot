package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.ChatMessage;
import com.example.resume_chatbot.model.ResumeAnalysis;
import com.example.resume_chatbot.model.UserSession;
import com.example.resume_chatbot.model.UserState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final AIService aiService;
    private final Map<Long, UserSession> sessionStore = new ConcurrentHashMap<>();

    /**
     * Gets or creates a user session for a specific Telegram chat ID.
     */
    public UserSession getOrCreateSession(Long chatId) {
        cleanupOldSessions();
        return sessionStore.computeIfAbsent(chatId, id -> UserSession.builder()
                .chatId(id)
                .state(UserState.IDLE)
                .lastActiveTime(Instant.now())
                .build());
    }

    /**
     * Resets a user session back to IDLE.
     */
    public void resetSession(Long chatId) {
        UserSession session = sessionStore.get(chatId);
        if (session != null) {
            session.reset();
        }
    }

    /**
     * Handles career questions with context and saves history.
     */
    public String askQuestion(Long chatId, String question) {
        UserSession session = getOrCreateSession(chatId);
        session.addMessage("user", question);

        ResumeAnalysis context = session.getLastAnalysis();
        String answer = aiService.answerCareerQuestion(question, context, session.getConversationHistory());

        session.addMessage("assistant", answer);
        return answer;
    }

    private void cleanupOldSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        sessionStore.entrySet().removeIf(entry -> entry.getValue().getLastActiveTime().isBefore(cutoff));
    }
}
