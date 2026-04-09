package com.reportsapi.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportsapi.sqs.client.SonarQubeClient;
import com.reportsapi.sqs.model.EndpointConfig;
import com.reportsapi.sqs.model.Project;
import com.reportsapi.sqs.model.TestConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class Main {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String CSV_HEADER = "project_key,project_name,bugs,vulnerabilities,code_smells,ncloc";

    public static void main(String[] args) throws Exception {
        File configFile = new File("endpoint.json");
        if (!configFile.exists()) {
            System.err.println("endpoint.json not found in working directory");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        EndpointConfig config = mapper.readValue(configFile, EndpointConfig.class);

        System.out.println("Endpoint: " + config.getEndpointName() + " (" + config.getEndpointUrl() + ")");

        File outputsDir = new File("outputs");
        outputsDir.mkdirs();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        File csvFile = new File(outputsDir, timestamp + ".csv");

        SonarQubeClient client = new SonarQubeClient(config);

        try (BufferedWriter csv = new BufferedWriter(new FileWriter(csvFile))) {
            csv.write(CSV_HEADER);
            csv.newLine();

            boolean testMode = Arrays.asList(args).contains("-t");
            if (testMode) {
                runTestMode(csv, mapper, client);
            } else {
                runFullMode(csv, client);
            }
        }

        System.out.println("\nOutput written to " + csvFile.getPath());
    }

    private static void runTestMode(BufferedWriter csv, ObjectMapper mapper, SonarQubeClient client) throws Exception {
        File testFile = new File("test.json");
        if (!testFile.exists()) {
            System.err.println("[-t] test.json not found in working directory");
            System.exit(1);
        }
        TestConfig testConfig = mapper.readValue(testFile, TestConfig.class);
        if (!testConfig.isActive()) {
            System.err.println("[-t] test.json exists but projectKey is empty");
            System.exit(1);
        }
        System.out.println("[TEST MODE] Project: " + testConfig.getProjectKey());
        writeRow(csv, client, testConfig.getProjectKey(), testConfig.getProjectKey());
    }

    private static void runFullMode(BufferedWriter csv, SonarQubeClient client) throws Exception {
        System.out.println("Fetching projects...");
        List<Project> projects = client.listProjects();
        System.out.println("Found " + projects.size() + " project(s). Fetching issues...");

        for (Project project : projects) {
            System.out.println("  Processing: " + project.getName() + " [" + project.getKey() + "]");
            writeRow(csv, client, project.getKey(), project.getName());
        }
    }

    private static void writeRow(BufferedWriter csv, SonarQubeClient client,
                                  String projectKey, String projectName) throws Exception {
        SonarQubeClient.IssueCounts issues = client.fetchIssueCounts(projectKey);
        int ncloc = client.fetchNcloc(projectKey);

        csv.write(escapeCsv(projectKey) + ","
                + escapeCsv(projectName) + ","
                + issues.bugs + ","
                + issues.vulnerabilities + ","
                + issues.codeSmells + ","
                + ncloc);
        csv.newLine();
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
