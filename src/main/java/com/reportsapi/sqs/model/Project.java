package com.reportsapi.sqs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    private String key;
    private String name;

    public String getKey() { return key; }
    public void setKey(String v) { key = v; }

    public String getName() { return name; }
    public void setName(String v) { name = v; }
}
