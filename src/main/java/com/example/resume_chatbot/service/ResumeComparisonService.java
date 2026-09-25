package com.example.resume_chatbot.service;

import com.example.resume_chatbot.model.JobDescription;
import com.example.resume_chatbot.model.Resume;
import com.example.resume_chatbot.model.ResumeAnalysis;
import com.example.resume_chatbot.model.ResumeComparison;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeComparisonService {

    private final ResumeAnalysisService resumeAnalysisService;

    /**
     * Compares multiple resumes against the same JobDescription factually without subjective bias.
     */
    public ResumeComparison compareResumes(List<Resume> resumes, JobDescription jobDescription) {
        log.info("Comparing {} resumes against target role [{}]", resumes.size(), jobDescription.getRoleTitle());

        List<ResumeAnalysis> analyses = new ArrayList<>();
        for (Resume resume : resumes) {
            analyses.add(resumeAnalysisService.analyze(resume, jobDescription));
        }

        List<String> insights = new ArrayList<>();

        for (int i = 0; i < analyses.size(); i++) {
            ResumeAnalysis analysis = analyses.get(i);
            String name = analysis.getResume().getName() != null && !analysis.getResume().getName().equals("Candidate")
                    ? analysis.getResume().getName()
                    : "Resume " + (char) ('A' + i);

            int score = analysis.getScoreBreakdown().getOverallScore();
            int matchedCount = analysis.getSkillMatch().getMatchedSkills().size();
            int missingCount = analysis.getSkillMatch().getMissingSkills().size();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("• %s (%s): Compatibility %d/100, Matched %d skills (%s)",
                    name,
                    analysis.getResume().getFilename(),
                    score,
                    matchedCount,
                    String.join(", ", analysis.getSkillMatch().getMatchedSkills())
            ));

            if (missingCount > 0) {
                sb.append(String.format(". Missing: %s.", String.join(", ", analysis.getSkillMatch().getMissingSkills())));
            }
            insights.add(sb.toString());
        }

        String summary = String.format(
                "Compared %d resumes against the '%s' role requirements. Each resume shows different strengths in skills, projects, and keyword alignment. Review the factual breakdown above to choose the best fit for your specific criteria.",
                resumes.size(),
                jobDescription.getRoleTitle()
        );

        return ResumeComparison.builder()
                .jobDescription(jobDescription)
                .analyses(analyses)
                .comparisonInsights(insights)
                .summary(summary)
                .build();
    }
}
