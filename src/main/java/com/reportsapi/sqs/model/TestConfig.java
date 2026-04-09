package com.reportsapi.sqs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestConfig {

    private String projectKey;

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String v) { projectKey = v; }

    public boolean isActive() {
        return projectKey != null && !projectKey.isBlank();
    }
}
