package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.ChatMessage;
import com.example.resume_chatbot.model.ResumeAnalysis;

import java.util.List;

public interface AIService {
    /**
     * Answers career and resume questions with conversational context and analysis data.
     *
     * @param question The user's query
     * @param contextAnalysis The latest analyzed resume context (if any)
     * @param history Prior conversational messages
     * @return Formatted answer suitable for Telegram/Chat
     */
    String answerCareerQuestion(String question, ResumeAnalysis contextAnalysis, List<ChatMessage> history);
}
