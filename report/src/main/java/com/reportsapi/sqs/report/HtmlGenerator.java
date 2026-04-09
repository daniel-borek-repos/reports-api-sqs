package com.reportsapi.sqs.report;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HtmlGenerator {

    private HtmlGenerator() {}

    private static final String DIV_CLOSE = "</div>\n";
    private static final String C_BUGS   = "#e67e22";
    private static final String C_VULN   = "#e74c3c";
    private static final String C_SMELLS = "#3498db";

    private static final int TOP_N = 30;

    public static void generate(List<CsvRecord> records, File out) throws Exception {
        long totBugs   = records.stream().mapToLong(CsvRecord::getBugs).sum();
        long totVuln   = records.stream().mapToLong(CsvRecord::getVulnerabilities).sum();
        long totSmells = records.stream().mapToLong(CsvRecord::getCodeSmells).sum();
        long totNcloc  = records.stream().mapToLong(CsvRecord::getNcloc).sum();

        List<CsvRecord> top = records.stream()
                .sorted(Comparator.comparingInt(r ->
                        -(r.getBugs() + r.getVulnerabilities() + r.getCodeSmells())))
                .limit(TOP_N)
                .collect(Collectors.toList());

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"));

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>SonarQube Server Quality Report</title>\n");
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n");
        sb.append("<style>\n").append(css()).append("</style>\n");
        sb.append("</head>\n<body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("  <h1>SonarQube Server Quality Report</h1>\n");
        sb.append("  <p>Generated ").append(esc(timestamp)).append("</p>\n");
        sb.append(DIV_CLOSE);

        sb.append("<div class=\"content\">\n");

        // Stat cards
        sb.append("<div class=\"cards\">\n");
        appendCard(sb, "Projects",        fmtLong(records.size()),              "#34495e");
        appendCard(sb, "Total Issues",    fmtLong(totBugs + totVuln + totSmells), "#8e44ad");
        appendCard(sb, "Vulnerabilities", fmtLong(totVuln),                     "#c0392b");
        appendCard(sb, "Lines of Code",   fmtLong(totNcloc),                    "#27ae60");
        sb.append(DIV_CLOSE);

        // Issue type filter
        sb.append("<div class=\"filter-bar\">\n");
        sb.append("  <span class=\"filter-label\">Filter issue types:</span>\n");
        appendCheckbox(sb, 0, C_BUGS,   "Bugs");
        appendCheckbox(sb, 1, C_VULN,   "Vulnerabilities");
        appendCheckbox(sb, 2, C_SMELLS, "Code Smells");
        sb.append(DIV_CLOSE);

        // Project search filter
        sb.append("<div class=\"search-bar\">\n");
        sb.append("  <input class=\"project-search\" type=\"search\" placeholder=\"Filter projects\u2026\" ");
        sb.append("oninput=\"filterChart(this.value)\">\n");
        sb.append(DIV_CLOSE);

        // Chart
        String sectionLabel = "Issues by Project"
                + (records.size() > TOP_N ? " \u2014 top " + TOP_N + " by issue count" : "");
        sb.append("<div class=\"section-title\">").append(esc(sectionLabel)).append("</div>\n");
        int chartHeight = Math.max(300, top.size() * 32 + 80);
        sb.append("<div class=\"chart-wrap\" id=\"chartWrap\" style=\"height:").append(chartHeight).append("px\">\n");
        sb.append("  <canvas id=\"projectChart\"></canvas>\n");
        sb.append(DIV_CLOSE);

        sb.append(DIV_CLOSE); // .content

        // JavaScript
        sb.append("<script>\n");
        sb.append("Chart.defaults.font.family = 'sans-serif';\n");
        sb.append("Chart.defaults.font.size = 11;\n\n");

        sb.append(chartOptionsFn()).append("\n\n");
        sb.append(buildChartDataJs(top)).append("\n");
        sb.append(controlFunctionsJs());

        sb.append("</script>\n</body>\n</html>\n");

        try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }

    // ── Chart JS ─────────────────────────────────────────────────────────────

    private static String chartOptionsFn() {
        return "function makeOptions() {\n" +
               "  return {\n" +
               "    indexAxis: 'y',\n" +
               "    responsive: true,\n" +
               "    maintainAspectRatio: false,\n" +
               "    interaction: { mode: 'index', axis: 'y' },\n" +
               "    plugins: {\n" +
               "      legend: { position: 'bottom' },\n" +
               "      tooltip: {\n" +
               "        callbacks: {\n" +
               "          label: function(ctx) {\n" +
               "            return ' ' + ctx.dataset.label + ': ' + ctx.parsed.x.toLocaleString();\n" +
               "          }\n" +
               "        }\n" +
               "      }\n" +
               "    },\n" +
               "    scales: {\n" +
               "      x: { grid: { color: '#d2d7dc' } },\n" +
               "      y: { grid: { display: false } }\n" +
               "    }\n" +
               "  };\n" +
               "}";
    }

    private static String buildChartDataJs(List<CsvRecord> projects) {
        StringBuilder sb = new StringBuilder();

        // Full dataset stored for search filtering
        sb.append("const allData = { labels:[");
        List<String> names = projects.stream()
                .map(r -> shorten(r.getProjectName() != null ? r.getProjectName() : r.getProjectKey(), 40))
                .collect(Collectors.toList());
        appendJsStringList(sb, names);
        sb.append("], bugs:[");
        appendIntList(sb, projects.stream().map(r -> (long) r.getBugs()).collect(Collectors.toList()));
        sb.append("], vulnerabilities:[");
        appendIntList(sb, projects.stream().map(r -> (long) r.getVulnerabilities()).collect(Collectors.toList()));
        sb.append("], codeSmells:[");
        appendIntList(sb, projects.stream().map(r -> (long) r.getCodeSmells()).collect(Collectors.toList()));
        sb.append("] };\n\n");

        sb.append("const chart = new Chart(document.getElementById('projectChart'), {\n");
        sb.append("  type: 'bar',\n");
        sb.append("  data: {\n");
        sb.append("    labels: allData.labels.slice(),\n");
        sb.append("    datasets: [\n");
        sb.append("      { label:'Bugs',            backgroundColor:'").append(C_BUGS)
          .append("', data:allData.bugs.slice() },\n");
        sb.append("      { label:'Vulnerabilities', backgroundColor:'").append(C_VULN)
          .append("', data:allData.vulnerabilities.slice() },\n");
        sb.append("      { label:'Code Smells',     backgroundColor:'").append(C_SMELLS)
          .append("', data:allData.codeSmells.slice() }\n");
        sb.append("    ]\n  },\n");
        sb.append("  options: makeOptions()\n});\n");

        return sb.toString();
    }

    private static String controlFunctionsJs() {
        return "\nfunction toggleDataset(idx, visible) {\n" +
               "  chart.setDatasetVisibility(idx, visible);\n" +
               "  chart.update();\n" +
               "}\n\n" +
               "function filterChart(search) {\n" +
               "  var lower = search.toLowerCase();\n" +
               "  var indices = [];\n" +
               "  for (var i = 0; i < allData.labels.length; i++) {\n" +
               "    if (allData.labels[i].toLowerCase().indexOf(lower) !== -1) indices.push(i);\n" +
               "  }\n" +
               "  chart.data.labels               = indices.map(function(i) { return allData.labels[i]; });\n" +
               "  chart.data.datasets[0].data     = indices.map(function(i) { return allData.bugs[i]; });\n" +
               "  chart.data.datasets[1].data     = indices.map(function(i) { return allData.vulnerabilities[i]; });\n" +
               "  chart.data.datasets[2].data     = indices.map(function(i) { return allData.codeSmells[i]; });\n" +
               "  var newH = Math.max(300, indices.length * 32 + 80);\n" +
               "  document.getElementById('chartWrap').style.height = newH + 'px';\n" +
               "  chart.update();\n" +
               "}\n";
    }

    // ── HTML helpers ─────────────────────────────────────────────────────────

    private static void appendCheckbox(StringBuilder sb, int datasetIdx, String color, String label) {
        sb.append("  <label><input type=\"checkbox\" checked onchange=\"toggleDataset(")
          .append(datasetIdx).append(",this.checked)\">")
          .append("<span class=\"color-dot\" style=\"background:").append(color).append("\"></span>")
          .append(esc(label)).append("</label>\n");
    }

    private static void appendCard(StringBuilder sb, String label, String value, String bg) {
        sb.append("  <div class=\"card\" style=\"background:").append(bg).append("\">");
        sb.append("<div class=\"value\">").append(esc(value)).append("</div>");
        sb.append("<div class=\"label\">").append(esc(label)).append("</div>");
        sb.append(DIV_CLOSE);
    }

    private static void appendJsStringList(StringBuilder sb, List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsStr(items.get(i)));
        }
    }

    private static void appendIntList(StringBuilder sb, List<Long> items) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
    }

    private static String css() {
        return "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
               "body { font-family: sans-serif; background: #f0f2f5; color: #333; }\n" +
               ".header { background: #2c3e50; color: white; padding: 24px 40px; }\n" +
               ".header h1 { font-size: 22px; margin-bottom: 6px; }\n" +
               ".header p { font-size: 11px; color: #96aabe; }\n" +
               ".content { padding: 24px 40px; }\n" +
               ".cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 24px; }\n" +
               ".card { color: white; padding: 16px; border-radius: 4px; text-align: center; }\n" +
               ".card .value { font-size: 22px; font-weight: bold; margin-bottom: 8px; }\n" +
               ".card .label { font-size: 10px; color: #bcd2e8; text-transform: uppercase; letter-spacing: 0.5px; }\n" +
               ".filter-bar { display: flex; align-items: center; gap: 20px; flex-wrap: wrap;\n" +
               "  background: white; padding: 10px 16px; border-radius: 4px; margin-bottom: 12px; }\n" +
               ".filter-label { font-size: 12px; font-weight: bold; color: #555; }\n" +
               ".filter-bar label { font-size: 12px; color: #444; display: flex; align-items: center;\n" +
               "  gap: 6px; cursor: pointer; user-select: none; }\n" +
               ".filter-bar input[type=checkbox] { cursor: pointer; }\n" +
               ".color-dot { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }\n" +
               ".search-bar { margin-bottom: 16px; }\n" +
               ".project-search { padding: 6px 10px; border: 1px solid #d2d7dc; border-radius: 4px;\n" +
               "  font-size: 12px; width: 100%; max-width: 300px; }\n" +
               ".project-search:focus { outline: none; border-color: #3498db; }\n" +
               ".section-title { font-size: 13px; font-weight: bold; color: #2c3e50; margin-bottom: 12px;\n" +
               "  border-bottom: 1px solid #d2d7dc; padding-bottom: 6px; }\n" +
               ".chart-wrap { background: white; padding: 16px; border-radius: 4px; position: relative; }\n";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String jsStr(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String fmtLong(long n) {
        if (n >= 1_000_000L) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000L)     return String.format(Locale.US, "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
