package com.example.rag.dto;

import com.example.rag.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;

public class AgentPlanningInput {

    private String question;
    private QuestionType questionType;
    private List<QueryEntity> queryEntities = new ArrayList<>();
    private Object context;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public List<QueryEntity> getQueryEntities() {
        return queryEntities;
    }

    public void setQueryEntities(List<QueryEntity> queryEntities) {
        this.queryEntities = queryEntities;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }
}
