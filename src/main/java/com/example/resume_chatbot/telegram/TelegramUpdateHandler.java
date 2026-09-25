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

        // Capture state before routing so we can log the transition once routing is done.
        UserState previousState = session.getState();
        String updateType = describeUpdate(text, hasDocument);

        TelegramResponse response = routeUpdate(session, chatId, text, hasDocument, message);

        UserState newState = session.getState();
        log.info("[chatId={}] STATE TRANSITION: {} -> received[{}] -> {}", chatId, previousState, updateType, newState);

        return response;
    }

    /**
     * Human-readable label for the incoming update, used purely for the state-transition log line.
     */
    private String describeUpdate(String text, boolean hasDocument) {
        if (hasDocument) {
            return "DOCUMENT";
        }
        if (!text.isEmpty() && text.startsWith("/")) {
            return "COMMAND:" + text;
        }
        if (!text.isEmpty()) {
            return "TEXT";
        }
        return "EMPTY";
    }

    /**
     * Original routing logic (global commands + state machine), unchanged aside from the
     * two guarded conditions noted inline below.
     */
    private TelegramResponse routeUpdate(UserSession session, Long chatId, String text, boolean hasDocument, Map<String, Object> message) {
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

        // NOTE: while the bot is actively waiting for a job description (AWAITING_JOB_DESCRIPTION),
        // these menu shortcuts are intentionally skipped. Previously they fired unconditionally,
        // so a stale "📄 Analyze Resume" button left over from an earlier keyboard (or a habitual
        // "/analyze" retype) would silently reset the conversation back to the resume-upload step
        // instead of accepting the job description the user just sent. Explicit /cancel or /start
        // still work at any time to abandon the current flow.
        boolean awaitingJobDescription = session.getState() == UserState.AWAITING_JOB_DESCRIPTION;

        if (!awaitingJobDescription
                && ("/analyze".equalsIgnoreCase(text) || "📄 Analyze Resume".equalsIgnoreCase(text) || "📄 Analyze Another".equalsIgnoreCase(text))) {
            session.setState(UserState.AWAITING_RESUME_FOR_ANALYSIS);
            return new TelegramResponse(chatId, "📄 <b>Please upload your resume (PDF, DOCX, DOC or TXT).</b>\n\n<i>Attach your resume file in this chat:</i>", messageService.getAwaitingInputKeyboard());
        }

        if (!awaitingJobDescription && ("/compare".equalsIgnoreCase(text) || "📊 Compare Resumes".equalsIgnoreCase(text))) {
            session.setState(UserState.AWAITING_RESUMES_FOR_COMPARE);
            session.getComparisonResumes().clear();
            return new TelegramResponse(chatId, "📊 <b>Multi-Resume Comparison Mode</b>\n\nUpload 2 or more resumes (PDF, DOCX, DOC, or TXT) one by one.\nWhen finished, send <b>/done</b>.", null);
        }

        if (!awaitingJobDescription && "💬 Ask a Question".equalsIgnoreCase(text)) {
            session.setState(UserState.AWAITING_QUESTION);
            return new TelegramResponse(chatId, "💬 <b>Ask me anything!</b>\n\nFor example:\n• <i>'What should I learn first?'</i>\n• <i>'Why is my compatibility score 78?'</i>\n• <i>'How can I improve my project section?'</i>", null);
        }

        // 2. Handle State Machine
        switch (session.getState()) {
            case AWAITING_RESUME_FOR_ANALYSIS:
                if (hasDocument) {
                    return handleResumeUploadForAnalysis(session, message, chatId);
                } else {
                    return new TelegramResponse(chatId, "⚠️ Please upload a resume file (PDF, DOCX, DOC or TXT). Images are not supported for resume parsing.", messageService.getAwaitingInputKeyboard());
                }

            case AWAITING_JOB_DESCRIPTION:
                // JD can be plain text (the common case) or, separately, a PDF document.
                // The PDF branch reuses the existing extractTextFromPdf() call (no changes to
                // resume-parsing logic) purely to get the raw text, then feeds it into the same
                // handleJobDescriptionInput() used for the text path, so both paths converge and
                // behave identically once the JD text is in hand.
                if (hasDocument) {
                    return handleJobDescriptionDocument(session, message, chatId);
                } else if (!text.isEmpty()) {
                    return handleJobDescriptionInput(session, text, chatId);
                } else {
                    return new TelegramResponse(chatId, "Please type or paste the target job title (e.g. <i>Java Backend Developer</i>) or job description.", messageService.getAwaitingInputKeyboard());
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
                    return new TelegramResponse(chatId, "Please upload another resume (PDF, DOCX, DOC or TXT), or send <b>/done</b> if finished.", null);
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
        String fileName = doc.containsKey("file_name") ? doc.get("file_name").toString() : "resume";
        String mimeType = doc.containsKey("mime_type") ? doc.get("mime_type").toString() : "";
        String fileId = doc.get("file_id").toString();
        try {
            InputStream is = fileService.downloadTelegramFile(fileId);
            String rawText = resumeParserService.extractTextFromInputStream(is, fileName, mimeType);
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
            ), messageService.getAwaitingInputKeyboard());

        } catch (ResumeParsingException e) {
            log.warn("Resume parsing error for chat {}: {}", chatId, e.getMessage());
            return new TelegramResponse(chatId, "⚠️ " + messageService.escapeHtml(e.getMessage()), messageService.getAwaitingInputKeyboard());
        } catch (Exception e) {
            log.error("Unexpected error parsing resume for chat {}: {}", chatId, e.getMessage(), e);
            return new TelegramResponse(chatId, "⚠️ Failed to process resume. Please ensure it is a valid text-based PDF/DOCX/DOC/TXT.", messageService.getAwaitingInputKeyboard());
        }
    }

    /**
     * Handles a job description supplied as a PDF document while the session is
     * AWAITING_JOB_DESCRIPTION. Reuses ResumeParserService.extractTextFromPdf() purely to get
     * plain text out of the file (no resume-specific parsing is applied to it), then delegates
     * to the same handleJobDescriptionInput() the text flow uses.
     */
    private TelegramResponse handleJobDescriptionDocument(UserSession session, Map<String, Object> message, Long chatId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) message.get("document");
        String fileId = doc.get("file_id").toString();

        try {
            InputStream is = fileService.downloadTelegramFile(fileId);
            String jdText = resumeParserService.extractTextFromInputStream(is, doc.containsKey("file_name") ? doc.get("file_name").toString() : "", doc.containsKey("mime_type") ? doc.get("mime_type").toString() : "");
            return handleJobDescriptionInput(session, jdText, chatId);
        } catch (ResumeParsingException e) {
            log.warn("JD PDF parsing error for chat {}: {}", chatId, e.getMessage());
            return new TelegramResponse(chatId, "⚠️ I couldn't read that document as a job description: " + messageService.escapeHtml(e.getMessage())
                    + "\n\nYou can also just paste the job description as plain text.", messageService.getAwaitingInputKeyboard());
        } catch (Exception e) {
            log.error("Unexpected error reading JD PDF for chat {}: {}", chatId, e.getMessage(), e);
            return new TelegramResponse(chatId, "⚠️ Failed to read that document. Please upload a text-based PDF/DOCX/DOC/TXT, or paste the job description as plain text.", messageService.getAwaitingInputKeyboard());
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
        String fileName = doc.containsKey("file_name") ? doc.get("file_name").toString() : "resume";
        String fileId = doc.get("file_id").toString();

        try {
            InputStream is = fileService.downloadTelegramFile(fileId);
            String rawText = resumeParserService.extractTextFromInputStream(is, fileName, doc.containsKey("mime_type") ? doc.get("mime_type").toString() : "");
            Resume resume = resumeParserService.parseResume(rawText, fileName);

            session.getComparisonResumes().add(resume);
            int count = session.getComparisonResumes().size();

                return new TelegramResponse(chatId, String.format(
                    "📄 <b>Received:</b> %s\n" +
                        "📊 Total resumes uploaded: <b>%d</b>\n\n" +
                        "Upload another resume (PDF, DOCX, DOC or TXT), or send <b>/done</b> when ready to compare.",
                    messageService.escapeHtml(fileName), count
                ), null);

        } catch (Exception e) {
            return new TelegramResponse(chatId, "⚠️ Could not read this document: " + e.getMessage(), null);
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
