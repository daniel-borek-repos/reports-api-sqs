package com.reportsapi.sqs.report;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        String format = "pdf";
        String csvPath = null;

        for (String arg : args) {
            if (arg.startsWith("--format=")) {
                format = arg.substring("--format=".length()).toLowerCase();
            } else if (!arg.startsWith("--")) {
                csvPath = arg;
            }
        }

        if (!format.equals("pdf") && !format.equals("html") && !format.equals("both")) {
            System.err.println("Unknown format '" + format + "'. Use --format=pdf, --format=html, or --format=both.");
            System.exit(1);
        }

        File csvFile = resolveCsvFile(csvPath);

        System.out.println("Reading: " + csvFile.getPath());
        List<CsvRecord> records = CsvReader.read(csvFile);
        System.out.println("Loaded " + records.size() + " record(s)");

        String basePath = csvFile.getPath().replaceAll("\\.csv$", "");

        if (format.equals("pdf") || format.equals("both")) {
            File pdfFile = new File(basePath + ".pdf");
            PdfGenerator.generate(records, pdfFile);
            System.out.println("PDF report written to: " + pdfFile.getPath());
        }
        if (format.equals("html") || format.equals("both")) {
            File htmlFile = new File(basePath + ".html");
            HtmlGenerator.generate(records, htmlFile);
            System.out.println("HTML report written to: " + htmlFile.getPath());
        }
    }

    private static File resolveCsvFile(String csvPath) {
        if (csvPath != null) {
            File csvFile = new File(csvPath);
            if (!csvFile.exists()) {
                System.err.println("File not found: " + csvPath);
                System.exit(1);
            }
            return csvFile;
        }
        File outputsDir = new File("outputs");
        if (!outputsDir.exists() || !outputsDir.isDirectory()) {
            System.err.println("No CSV path provided and outputs/ directory not found.");
            System.exit(1);
        }
        File[] csvFiles = outputsDir.listFiles((d, n) -> n.endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            System.err.println("No CSV files found in outputs/");
            System.exit(1);
        }
        Arrays.sort(csvFiles, Comparator.comparingLong(File::lastModified).reversed());
        System.out.println("Using latest CSV: " + csvFiles[0].getName());
        return csvFiles[0];
    }
}
