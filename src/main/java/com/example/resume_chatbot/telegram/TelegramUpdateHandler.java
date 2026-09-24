package com.example.resume_chatbot.telegram;

import com.example.resume_chatbot.exception.ResumeParsingException;
import com.example.resume_chatbot.model.*;
import com.example.resume_chatbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramUpdateHandler {

    private final TelegramMessageService messageService;
    private final TelegramFileService fileService;
    private final ResumeParserService resumeParserService;
    private final SkillMatchingService skillMatchingService;
    private final ResumeAnalysisService resumeAnalysisService;
    private final ResumeComparisonService resumeComparisonService;
    private final CourseRecommendationService courseRecommendationService;
    private final ChatbotService chatbotService;

    /**
     * Entry point to handle any incoming Telegram update object.
     * Returns an action/response descriptor containing chat_id, text, and reply_markup.
     */
    public TelegramResponse processUpdate(Map<String, Object> update) {
        if (update == null || !update.containsKey("message")) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        @SuppressWarnings("unchecked")
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        if (chat == null || !chat.containsKey("id")) {
            return null;
        }

        Long chatId = Long.valueOf(chat.get("id").toString());
        UserSession session = chatbotService.getOrCreateSession(chatId);

        String text = message.containsKey("text") ? message.get("text").toString().trim() : "";
        boolean hasDocument = message.containsKey("document");

        // 1. Check Global Commands
        if ("/start".equalsIgnoreCase(text) || "🏠 Main Menu".equalsIgnoreCase(text)) {
            session.reset();
            return new TelegramResponse(chatId, messageService.getStartMessage(), messageService.getMainMenuKeyboard());
        }

        if ("/help".equalsIgnoreCase(text) || "❓ Help".equalsIgnoreCase(text)) {
            return new TelegramResponse(chatId, messageService.getHelpMessage(), messageService.getMainMenuKeyboard());
        }

        if ("/about".equalsIgnoreCase(text)) {
            return new TelegramResponse(chatId, messageService.getAboutMessage(), messageService.getMainMenuKeyboard());
        }

        if ("/cancel".equalsIgnoreCase(text) || "❌ Cancel".equalsIgnoreCase(text)) {
            session.reset();
            return new TelegramResponse(chatId, "Action cancelled. Returning to main menu.", messageService.getMainMenuKeyboard());
        }

        if ("/courses".equalsIgnoreCase(text) || "📚 Course Suggestions".equalsIgnoreCase(text)) {
            List<String> skills = (session.getLastAnalysis() != null && !session.getLastAnalysis().getSkillMatch().getMissingSkills().isEmpty())
                    ? session.getLastAnalysis().getSkillMatch().getMissingSkills()
                    : List.of("Docker", "Spring Boot", "Git");

            List<CourseRecommendation> recs = courseRecommendationService.recommendCourses(skills);
            StringBuilder sb = new StringBuilder("📚 <b>Verified Official Learning Resources:</b>\n\n");
            for (int i = 0; i < recs.size(); i++) {
                CourseRecommendation r = recs.get(i);
                sb.append(String.format("%d. <a href=\"%s\"><b>%s</b></a> (%s)\n   %s\n\n",
                        i + 1, r.getUrl(), messageService.escapeHtml(r.getTitle()),
                        messageService.escapeHtml(r.getProvider()), messageService.escapeHtml(r.getDescription())));
            }
            return new TelegramResponse(chatId, sb.toString(), messageService.getMainMenuKeyboard());
        }

        if ("/analyze".equalsIgnoreCase(text) || "📄 Analyze Resume".equalsIgnoreCase(text) || "📄 Analyze Another".equalsIgnoreCase(text)) {
            session.setState(UserState.AWAITING_RESUME_FOR_ANALYSIS);
            return new TelegramResponse(chatId, "📄 <b>Please upload your resume as a PDF document.</b>\n\n<i>Attach your PDF resume in this chat:</i>", null);
        }

        if ("/compare".equalsIgnoreCase(text) || "📊 Compare Resumes".equalsIgnoreCase(text)) {
            session.setState(UserState.AWAITING_RESUMES_FOR_COMPARE);
            session.getComparisonResumes().clear();
            return new TelegramResponse(chatId, "📊 <b>Multi-Resume Comparison Mode</b>\n\nUpload 2 or more PDF resumes one by one.\nWhen finished, send <b>/done</b>.", null);
        }

        if ("💬 Ask a Question".equalsIgnoreCase(text)) {
            session.setState(UserState.AWAITING_QUESTION);
            return new TelegramResponse(chatId, "💬 <b>Ask me anything!</b>\n\nFor example:\n• <i>'What should I learn first?'</i>\n• <i>'Why is my compatibility score 78?'</i>\n• <i>'How can I improve my project section?'</i>", null);
        }

        // 2. Handle State Machine
        switch (session.getState()) {
            case AWAITING_RESUME_FOR_ANALYSIS:
                if (hasDocument) {
                    return handleResumeUploadForAnalysis(session, message, chatId);
                } else {
                    return new TelegramResponse(chatId, "⚠️ Please upload a <b>PDF resume</b> file. (Images and plain text messages are not supported for resume parsing).", null);
                }

            case AWAITING_JOB_DESCRIPTION:
                if (!text.isEmpty()) {
                    return handleJobDescriptionInput(session, text, chatId);
                } else {
                    return new TelegramResponse(chatId, "Please type or paste the target job title (e.g. <i>Java Backend Developer</i>) or job description.", null);
                }

            case AWAITING_RESUMES_FOR_COMPARE:
                if ("/done".equalsIgnoreCase(text) || "done".equalsIgnoreCase(text)) {
                    if (session.getComparisonResumes().size() < 2) {
                        return new TelegramResponse(chatId, String.format("⚠️ You have uploaded %d resume(s). Please upload at least 2 resumes to compare, or send /cancel.", session.getComparisonResumes().size()), null);
                    }
                    session.setState(UserState.AWAITING_JOB_DESCRIPTION_FOR_COMPARE);
                    return new TelegramResponse(chatId, "🎯 <b>Resumes collected!</b> Now, please send the target job role or job description to compare them against.", null);
                }
                if (hasDocument) {
                    return handleResumeUploadForCompare(session, message, chatId);
                } else {
                    return new TelegramResponse(chatId, "Please upload another PDF resume, or send <b>/done</b> if finished.", null);
                }

            case AWAITING_JOB_DESCRIPTION_FOR_COMPARE:
                if (!text.isEmpty()) {
                    return handleJobDescriptionForCompare(session, text, chatId);
                } else {
                    return new TelegramResponse(chatId, "Please enter the job title or description for the comparison.", null);
                }

            case AWAITING_QUESTION:
            case IDLE:
            default:
                if (!text.isEmpty()) {
                    String answer = chatbotService.askQuestion(chatId, text);
                    return new TelegramResponse(chatId, answer, messageService.getPostAnalysisKeyboard());
                }
                return new TelegramResponse(chatId, "I'm ready! Choose an option from the menu below or upload a resume.", messageService.getMainMenuKeyboard());
        }
    }

    private TelegramResponse handleResumeUploadForAnalysis(UserSession session, Map<String, Object> message, Long chatId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) message.get("document");
        String fileName = doc.containsKey("file_name") ? doc.get("file_name").toString() : "resume.pdf";
        String mimeType = doc.containsKey("mime_type") ? doc.get("mime_type").toString() : "";
        String fileId = doc.get("file_id").toString();

        if (!fileName.toLowerCase().endsWith(".pdf") && !mimeType.toLowerCase().contains("pdf")) {
            return new TelegramResponse(chatId, "⚠️ Please upload a document in <b>PDF format</b>.", null);
        }

        try {
            InputStream is = fileService.downloadTelegramFile(fileId);
            String rawText = resumeParserService.extractTextFromPdf(is);
            Resume resume = resumeParserService.parseResume(rawText, fileName);

            session.setActiveResume(resume);
            session.setState(UserState.AWAITING_JOB_DESCRIPTION);

            String candidateName = (resume.getName() != null && !resume.getName().equalsIgnoreCase("Candidate"))
                    ? resume.getName()
                    : "Candidate";

            return new TelegramResponse(chatId, String.format(
                    "✅ <b>Resume received for %s!</b>\n\n" +
                            "Now, please provide your <b>target job role</b> (e.g., <i>Java Backend Developer</i>) or paste the complete job description text.",
                    messageService.escapeHtml(candidateName)
            ), null);

        } catch (ResumeParsingException e) {
            log.warn("Resume parsing error for chat {}: {}", chatId, e.getMessage());
            return new TelegramResponse(chatId, "⚠️ " + messageService.escapeHtml(e.getMessage()), null);
        } catch (Exception e) {
            log.error("Unexpected error parsing resume for chat {}: {}", chatId, e.getMessage(), e);
            return new TelegramResponse(chatId, "⚠️ Failed to process resume. Please ensure it is a valid text-based PDF.", null);
        }
    }

    private TelegramResponse handleJobDescriptionInput(UserSession session, String text, Long chatId) {
        try {
            JobDescription jd = skillMatchingService.parseJobDescription(text);
            ResumeAnalysis analysis = resumeAnalysisService.analyze(session.getActiveResume(), jd);

            session.setLastAnalysis(analysis);
            session.setState(UserState.AWAITING_QUESTION);

            String formattedResponse = messageService.formatAnalysis(analysis);
            return new TelegramResponse(chatId, formattedResponse, messageService.getPostAnalysisKeyboard());
        } catch (Exception e) {
            log.error("Analysis failed for chat {}: {}", chatId, e.getMessage(), e);
            return new TelegramResponse(chatId, "⚠️ An error occurred during analysis: " + e.getMessage(), messageService.getMainMenuKeyboard());
        }
    }

    private TelegramResponse handleResumeUploadForCompare(UserSession session, Map<String, Object> message, Long chatId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) message.get("document");
        String fileName = doc.containsKey("file_name") ? doc.get("file_name").toString() : "resume.pdf";
        String fileId = doc.get("file_id").toString();

        try {
            InputStream is = fileService.downloadTelegramFile(fileId);
            String rawText = resumeParserService.extractTextFromPdf(is);
            Resume resume = resumeParserService.parseResume(rawText, fileName);

            session.getComparisonResumes().add(resume);
            int count = session.getComparisonResumes().size();

            return new TelegramResponse(chatId, String.format(
                    "📄 <b>Received:</b> %s\n" +
                            "📊 Total resumes uploaded: <b>%d</b>\n\n" +
                            "Upload another PDF resume, or send <b>/done</b> when ready to compare.",
                    messageService.escapeHtml(fileName), count
            ), null);

        } catch (Exception e) {
            return new TelegramResponse(chatId, "⚠️ Could not read this PDF: " + e.getMessage(), null);
        }
    }

    private TelegramResponse handleJobDescriptionForCompare(UserSession session, String text, Long chatId) {
        try {
            JobDescription jd = skillMatchingService.parseJobDescription(text);
            ResumeComparison comparison = resumeComparisonService.compareResumes(session.getComparisonResumes(), jd);

            session.setState(UserState.IDLE);
            String formatted = messageService.formatComparison(comparison);
            return new TelegramResponse(chatId, formatted, messageService.getMainMenuKeyboard());
        } catch (Exception e) {
            log.error("Comparison error for chat {}: {}", chatId, e.getMessage(), e);
            return new TelegramResponse(chatId, "⚠️ Comparison error: " + e.getMessage(), messageService.getMainMenuKeyboard());
        }
    }

    public record TelegramResponse(Long chatId, String text, Map<String, Object> replyMarkup) {}
}
