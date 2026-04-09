package com.reportsapi.sqs.report;

public class CsvRecord {

    private String projectKey;
    private String projectName;
    private int securityIssues;
    private int reliabilityIssues;
    private int maintainabilityIssues;
    private int ncloc;
    private int lines;

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String v) { projectKey = v; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { projectName = v; }

    public int getSecurityIssues() { return securityIssues; }
    public void setSecurityIssues(int v) { securityIssues = v; }

    public int getReliabilityIssues() { return reliabilityIssues; }
    public void setReliabilityIssues(int v) { reliabilityIssues = v; }

    public int getMaintainabilityIssues() { return maintainabilityIssues; }
    public void setMaintainabilityIssues(int v) { maintainabilityIssues = v; }

    public int getNcloc() { return ncloc; }
    public void setNcloc(int v) { ncloc = v; }

    public int getLines() { return lines; }
    public void setLines(int v) { lines = v; }
}
