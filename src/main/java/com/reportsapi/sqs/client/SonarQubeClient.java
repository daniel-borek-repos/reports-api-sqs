package com.reportsapi.sqs.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
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

    private static final String METRIC_KEYS =
            "software_quality_security_issues," +
            "software_quality_reliability_issues," +
            "software_quality_maintainability_issues," +
            "ncloc,lines";

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
     * Fetches software quality metrics for a project via api/measures/component.
     * Handles both plain integer values and JSON-object values (e.g. {"total":5,"HIGH":2,...})
     * returned by SonarQube 10+ in MQR mode.
     */
    public ProjectMeasures fetchMeasures(String projectKey) throws Exception {
        String url = config.getEndpointUrl()
                + "/api/measures/component"
                + "?component=" + projectKey
                + "&metricKeys=" + METRIC_KEYS;

        MeasuresResponse response = objectMapper.readValue(get(url), MeasuresResponse.class);

        ProjectMeasures pm = new ProjectMeasures();
        if (response.component != null && response.component.measures != null) {
            for (Measure m : response.component.measures) {
                int val = extractInt(m.value);
                switch (m.metric) {
                    case "software_quality_security_issues"        -> pm.securityIssues        = val;
                    case "software_quality_reliability_issues"     -> pm.reliabilityIssues     = val;
                    case "software_quality_maintainability_issues" -> pm.maintainabilityIssues = val;
                    case "ncloc"                                   -> pm.ncloc                 = val;
                    case "lines"                                   -> pm.lines                 = val;
                    default -> { /* ignore */ }
                }
            }
        }
        return pm;
    }

    /**
     * Parses a metric value string that may be either a plain integer ("42")
     * or a JSON object with a "total" field ({"total":42,"HIGH":10,"MEDIUM":20,"LOW":12}).
     */
    private static int extractInt(String value) {
        if (value == null || value.isBlank()) return 0;
        String v = value.trim();
        if (v.startsWith("{")) {
            try {
                JsonNode node = new ObjectMapper().readTree(v);
                JsonNode total = node.get("total");
                return total != null ? total.asInt() : 0;
            } catch (Exception e) {
                return 0;
            }
        }
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return 0; }
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

    // ── Public result type ────────────────────────────────────────────────────

    public static class ProjectMeasures {
        public int securityIssues;
        public int reliabilityIssues;
        public int maintainabilityIssues;
        public int ncloc;
        public int lines;
    }

    // ── Inner response types ──────────────────────────────────────────────────

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
