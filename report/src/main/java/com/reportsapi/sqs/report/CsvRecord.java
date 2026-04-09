package com.reportsapi.sqs.report;

public class CsvRecord {

    private String projectKey;
    private String projectName;
    private int bugs;
    private int vulnerabilities;
    private int codeSmells;
    private int ncloc;

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String v) { projectKey = v; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { projectName = v; }

    public int getBugs() { return bugs; }
    public void setBugs(int v) { bugs = v; }

    public int getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(int v) { vulnerabilities = v; }

    public int getCodeSmells() { return codeSmells; }
    public void setCodeSmells(int v) { codeSmells = v; }

    public int getNcloc() { return ncloc; }
    public void setNcloc(int v) { ncloc = v; }
}
