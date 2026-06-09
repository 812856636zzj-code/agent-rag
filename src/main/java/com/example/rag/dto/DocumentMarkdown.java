package com.example.rag.dto;

public class DocumentMarkdown {

    private String fileType;
    private String markdown;

    public DocumentMarkdown(String fileType, String markdown) {
        this.fileType = fileType;
        this.markdown = markdown;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }
}
