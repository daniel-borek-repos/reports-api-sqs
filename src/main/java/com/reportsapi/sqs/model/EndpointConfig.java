package com.reportsapi.sqs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EndpointConfig {

    @JsonProperty("endpoint_url")
    private String endpointUrl;

    @JsonProperty("endpoint_name")
    private String endpointName;

    @JsonProperty("endpoint_token")
    private String endpointToken;

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String v) { endpointUrl = v; }

    public String getEndpointName() { return endpointName; }
    public void setEndpointName(String v) { endpointName = v; }

    public String getEndpointToken() { return endpointToken; }
    public void setEndpointToken(String v) { endpointToken = v; }
}
