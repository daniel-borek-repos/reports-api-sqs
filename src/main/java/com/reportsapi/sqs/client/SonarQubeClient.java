package com.reportsapi.sqs.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportsapi.sqs.model.EndpointConfig;
import com.reportsapi.sqs.model.Project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class SonarQubeClient {

    private final EndpointConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authHeader;

    public SonarQubeClient(EndpointConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.authHeader = "Bearer " + config.getEndpointToken();
    }

    /** Fetches all projects from api/projects/search, handling pagination. */
    public List<Project> listProjects() throws Exception {
        List<Project> all = new ArrayList<>();
        int page = 1;
        int pageSize = 500;

        while (true) {
            String url = config.getEndpointUrl()
                    + "/api/projects/search"
                    + "?ps=" + pageSize
                    + "&p=" + page;

            ProjectsResponse parsed = objectMapper.readValue(get(url), ProjectsResponse.class);
            all.addAll(parsed.components);

            if (all.size() >= parsed.paging.total) {
                break;
            }
            page++;
        }

        return all;
    }

    /**
     * Returns issue counts for a project by type (BUG, VULNERABILITY, CODE_SMELL)
     * using the facets feature of api/issues/search — no need to page through all issues.
     */
    public IssueCounts fetchIssueCounts(String projectKey) throws Exception {
        String url = config.getEndpointUrl()
                + "/api/issues/search"
                + "?componentKeys=" + projectKey
                + "&types=BUG,VULNERABILITY,CODE_SMELL"
                + "&resolved=false"
                + "&ps=1"
                + "&facets=types";

        IssuesResponse response = objectMapper.readValue(get(url), IssuesResponse.class);

        IssueCounts counts = new IssueCounts();
        if (response.facets != null) {
            for (Facet facet : response.facets) {
                if ("types".equals(facet.property) && facet.values != null) {
                    for (FacetValue fv : facet.values) {
                        switch (fv.val) {
                            case "BUG"           -> counts.bugs           = fv.count;
                            case "VULNERABILITY" -> counts.vulnerabilities = fv.count;
                            case "CODE_SMELL"    -> counts.codeSmells     = fv.count;
                            default              -> { /* ignore */ }
                        }
                    }
                }
            }
        }
        return counts;
    }

    /** Returns non-comment lines of code for a project via api/measures/component. */
    public int fetchNcloc(String projectKey) throws Exception {
        String url = config.getEndpointUrl()
                + "/api/measures/component"
                + "?component=" + projectKey
                + "&metricKeys=ncloc";

        MeasuresResponse response = objectMapper.readValue(get(url), MeasuresResponse.class);
        if (response.component != null && response.component.measures != null) {
            for (Measure m : response.component.measures) {
                if ("ncloc".equals(m.metric)) {
                    try { return Integer.parseInt(m.value); } catch (NumberFormatException e) { return 0; }
                }
            }
        }
        return 0;
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API error " + response.statusCode()
                    + " for " + url + ": " + response.body());
        }
        return response.body();
    }

    // ── Inner response/model types ────────────────────────────────────────────

    public static class IssueCounts {
        public int bugs;
        public int vulnerabilities;
        public int codeSmells;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ProjectsResponse {
        public Paging paging;
        public List<Project> components = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Paging {
        public int pageIndex;
        public int pageSize;
        public int total;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class IssuesResponse {
        public List<Facet> facets;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Facet {
        public String property;
        public List<FacetValue> values;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FacetValue {
        public String val;
        public int count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MeasuresResponse {
        public ComponentMeasures component;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ComponentMeasures {
        public List<Measure> measures;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Measure {
        public String metric;
        public String value;
    }
}
