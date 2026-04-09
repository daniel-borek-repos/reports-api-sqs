package com.reportsapi.sqs.report;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class CsvReader {

    private CsvReader() {}

    public static List<CsvRecord> read(File file) throws Exception {
        List<CsvRecord> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = parseLine(line);
                if (cols.length < 6) continue;
                CsvRecord r = new CsvRecord();
                r.setProjectKey(cols[0]);
                r.setProjectName(cols[1]);
                r.setBugs(parseInt(cols[2]));
                r.setVulnerabilities(parseInt(cols[3]));
                r.setCodeSmells(parseInt(cols[4]));
                r.setNcloc(parseInt(cols[5]));
                records.add(r);
            }
        }
        return records;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }

    private static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
