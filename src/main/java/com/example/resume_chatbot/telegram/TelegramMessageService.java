package com.example.resume_chatbot.telegram;

import com.example.resume_chatbot.model.CourseRecommendation;
import com.example.resume_chatbot.model.ResumeAnalysis;
import com.example.resume_chatbot.model.ResumeComparison;
import com.example.resume_chatbot.model.ScoreBreakdown;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TelegramMessageService {

    public String getStartMessage() {
        return "👋 <b>Welcome to Resume Career Assistant!</b>\n\n" +
                "I am your AI-powered career assistant and resume reviewer. I can help you:\n" +
                "• 📄 <b>Analyze your resume</b> against any job role or JD\n" +
                "• 🎯 <b>Calculate an ATS-like compatibility score</b>\n" +
                "• ✅ <b>Identify matched & missing skills</b>\n" +
                "• 💡 <b>Provide actionable resume improvement suggestions</b>\n" +
                "• 📚 <b>Recommend verified courses & official tutorials</b>\n" +
                "• 📊 <b>Compare multiple resumes</b> against the same role\n" +
                "• 💬 <b>Answer career and interview questions</b>\n\n" +
                "<i>Tap one of the options below to get started:</i>";
    }

    public String getHelpMessage() {
        return "ℹ️ <b>How to Use Resume Career Assistant</b>\n\n" +
                "<b>Commands:</b>\n" +
                "• /start - Restart bot and show main menu\n" +
                "• /analyze - Upload a PDF resume to analyze\n" +
                "• /compare - Compare 2 or more resumes against a JD\n" +
                "• /courses - Get verified course recommendations\n" +
                "• /about - Learn about the scoring system & architecture\n" +
                "• /cancel - Cancel current action and return to main menu\n\n" +
                "<b>How Analysis Works:</b>\n" +
                "1. Send /analyze and upload your PDF resume.\n" +
                "2. Provide your target job title (e.g. <i>Java Backend Developer</i>) or paste a job description.\n" +
                "3. Receive an instant analysis with your ATS-like compatibility score and skill breakdown.\n" +
                "4. Ask questions anytime, e.g. <i>'What should I learn first?'</i> or <i>'How can I improve my projects?'</i>";
    }

    public String getAboutMessage() {
        return "🔍 <b>About Resume Career Assistant</b>\n\n" +
                "<b>Architecture & Stack:</b>\n" +
                "• <b>Backend:</b> Java 21, Spring Boot 4, Apache PDFBox, Spring RestClient\n" +
                "• <b>Design:</b> Clean Layered Architecture (Telegram, Controller, Service, Model)\n\n" +
                "<b>About the ATS-like Compatibility Score:</b>\n" +
                "⚠️ <i>Important Notice:</i> The compatibility score is an <b>application-generated estimate</b> calculated using weighted factors (Skill match 50%, Keyword alignment 20%, Experience/Projects 15%, Education 10%, Readability 5%). It is NOT an official proprietary score from any commercial ATS vendor. It is designed to guide your preparation and optimize your resume presentation.";
    }

    public String formatAnalysis(ResumeAnalysis analysis) {
        ScoreBreakdown score = analysis.getScoreBreakdown();
        StringBuilder sb = new StringBuilder();

        sb.append("📋 <b>Resume Analysis</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("👤 <b>Candidate:</b> ").append(escapeHtml(analysis.getResume().getName())).append("\n");
        sb.append("🎯 <b>Target Role:</b> ").append(escapeHtml(analysis.getJobDescription().getRoleTitle())).append("\n\n");

        sb.append("📊 <b>ATS-like Compatibility Score:</b> <b>").append(score.getOverallScore()).append("/100</b>\n");
        sb.append("<i>(").append(escapeHtml(score.getLabel())).append(")</i>\n\n");

        sb.append("📈 <b>Score Breakdown:</b>\n");
        sb.append(String.format("• Skill Match: <b>%.1f / %.0f</b>\n", score.getSkillScore(), score.getMaxSkillScore()));
        sb.append(String.format("• Keyword Alignment: <b>%.1f / %.0f</b>\n", score.getKeywordScore(), score.getMaxKeywordScore()));
        sb.append(String.format("• Experience & Projects: <b>%.1f / %.0f</b>\n", score.getExperienceScore(), score.getMaxExperienceScore()));
        sb.append(String.format("• Education: <b>%.1f / %.0f</b>\n", score.getEducationScore(), score.getMaxEducationScore()));
        sb.append(String.format("• Completeness: <b>%.1f / %.0f</b>\n\n", score.getCompletenessScore(), score.getMaxCompletenessScore()));

        // Matched Skills
        sb.append("✅ <b>Matched Skills:</b>\n");
        if (analysis.getSkillMatch().getMatchedSkills().isEmpty()) {
            sb.append("• <i>No direct skill matches found for this role.</i>\n");
        } else {
            for (String s : analysis.getSkillMatch().getMatchedSkills()) {
                sb.append("✓ ").append(escapeHtml(s)).append("\n");
            }
        }
        sb.append("\n");

        // Missing Skills
        sb.append("⚠️ <b>Missing / Recommended Skills:</b>\n");
        if (analysis.getSkillMatch().getMissingSkills().isEmpty()) {
            sb.append("• <i>None! All core required skills are present in your resume.</i>\n");
        } else {
            for (String s : analysis.getSkillMatch().getMissingSkills()) {
                sb.append("• ").append(escapeHtml(s)).append("\n");
            }
        }
        sb.append("\n");

        // Related Skills if any
        if (!analysis.getSkillMatch().getRelatedSkills().isEmpty()) {
            sb.append("🔄 <b>Related / Partial Skills:</b>\n");
            for (String s : analysis.getSkillMatch().getRelatedSkills()) {
                sb.append("~ ").append(escapeHtml(s)).append("\n");
            }
            sb.append("\n");
        }

        // Suggestions
        sb.append("💡 <b>Resume Suggestions:</b>\n");
        for (String sug : analysis.getSuggestions()) {
            sb.append("• ").append(escapeHtml(sug)).append("\n\n");
        }

        // Recommended Courses with Hyperlinks
        if (!analysis.getRecommendedCourses().isEmpty()) {
            sb.append("📚 <b>Recommended Learning (Official & Verified):</b>\n");
            for (int i = 0; i < analysis.getRecommendedCourses().size(); i++) {
                CourseRecommendation rec = analysis.getRecommendedCourses().get(i);
                sb.append(String.format("%d. <a href=\"%s\"><b>%s</b></a> (%s)\n   %s\n\n",
                        i + 1, rec.getUrl(), escapeHtml(rec.getTitle()), escapeHtml(rec.getProvider()), escapeHtml(rec.getDescription())));
            }
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("<i>You can now ask any career question (e.g., 'What should I learn first?') or select an option below:</i>");
        return sb.toString();
    }

    public String formatComparison(ResumeComparison comparison) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>Multiple Resume Comparison</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🎯 <b>Target Role:</b> ").append(escapeHtml(comparison.getJobDescription().getRoleTitle())).append("\n\n");

        for (String insight : comparison.getComparisonInsights()) {
            sb.append(insight).append("\n\n");
        }

        sb.append("📋 <b>Summary:</b>\n");
        sb.append(escapeHtml(comparison.getSummary())).append("\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("<i>Note: This comparison provides objective, factual differences to assist your evaluation.</i>");
        return sb.toString();
    }

    public Map<String, Object> getMainMenuKeyboard() {
        return Map.of(
                "keyboard", List.of(
                        List.of(Map.of("text", "📄 Analyze Resume"), Map.of("text", "📊 Compare Resumes")),
                        List.of(Map.of("text", "📚 Course Suggestions"), Map.of("text", "❓ Help"))
                ),
                "resize_keyboard", true,
                "one_time_keyboard", false
        );
    }

    public Map<String, Object> getPostAnalysisKeyboard() {
        return Map.of(
                "keyboard", List.of(
                        List.of(Map.of("text", "💬 Ask a Question"), Map.of("text", "📄 Analyze Another")),
                        List.of(Map.of("text", "📊 Compare Resumes"), Map.of("text", "🏠 Main Menu"))
                ),
                "resize_keyboard", true,
                "one_time_keyboard", false
        );
    }

    public String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
