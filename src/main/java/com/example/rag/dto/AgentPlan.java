package com.example.rag.dto;

import com.example.rag.enums.QuestionType;
import com.example.rag.enums.ToolType;

import java.util.ArrayList;
import java.util.List;

public class AgentPlan {

    private QuestionType questionType;
    private List<ToolType> selectedTools = new ArrayList<>();
    private String reason;
    private Integer maxSteps;

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public List<ToolType> getSelectedTools() {
        return selectedTools;
    }

    public void setSelectedTools(List<ToolType> selectedTools) {
        this.selectedTools = selectedTools;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(Integer maxSteps) {
        this.maxSteps = maxSteps;
    }
}
