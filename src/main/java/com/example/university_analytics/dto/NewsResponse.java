package com.example.universityanalytics.dto;

public class NewsResponse {
    private String id;
    private String title;
    private String source;
    private String date;
    private String summary;
    private String impact;

    public NewsResponse() {}

    public NewsResponse(String id, String title, String source, String date, String summary, String impact) {
        this.id = id;
        this.title = title;
        this.source = source;
        this.date = date;
        this.summary = summary;
        this.impact = impact;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }
}