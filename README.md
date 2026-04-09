# SonarQube Server Quality Report Generator

Fetches software quality metrics from a SonarQube Server instance and generates a quality report as a PDF or HTML file.

Metrics are retrieved via `api/measures/component` using the `software_quality_*` metric keys introduced in SonarQube 10 (MQR mode). Projects are enumerated via `api/projects/search` with pagination.

## Prerequisites

- Java 17+
- Maven 3.6+
- A SonarQube Server instance with an API token

## Project structure

```
reports-api-sqs/
├── src/                        # Stage 1 — data collection
│   └── main/java/com/reportsapi/sqs/
│       ├── Main.java
│       ├── client/SonarQubeClient.java
│       └── model/
├── report/                     # Stage 2 — report generation
│   └── src/main/java/com/reportsapi/sqs/report/
│       ├── Main.java
│       ├── PdfGenerator.java
│       ├── HtmlGenerator.java
│       ├── CsvReader.java
│       └── CsvRecord.java
├── outputs/                    # Generated CSV, PDF, and HTML files
├── endpoint.json               # Your config (create from sample)
└── endpoint.json.sample        # Config template
```

## Configuration

Copy the sample config and fill in your SonarQube Server URL and token:

```bash
cp endpoint.json.sample endpoint.json
```

Edit `endpoint.json`:

```json
{
  "endpoint_url": "https://your-sonarqube-server",
  "endpoint_name": "SonarQube Server",
  "endpoint_token": "your-sonarqube-token-here"
}
```

The `endpoint_url` must not have a trailing slash. The client appends API paths such as `/api/projects/search` directly to it.

For test mode, copy and edit `test.json.sample`:

```bash
cp test.json.sample test.json
```

```json
{
  "projectKey": "my-project-key"
}
```

## Build

Build both modules from the repository root:

```bash
mvn clean package
mvn clean package -f report/pom.xml
```

This produces:
- `target/reports-api-sqs-1.0-SNAPSHOT.jar`
- `report/target/reports-api-sqs-report-1.0-SNAPSHOT.jar`

## Usage

### Stage 1 — Collect data from SonarQube Server

Fetches all projects and their software quality metrics, then writes a timestamped CSV to `outputs/`.

```bash
java -jar target/reports-api-sqs-1.0-SNAPSHOT.jar
```

To test against a single project first, use test mode (reads `test.json`):

```bash
java -jar target/reports-api-sqs-1.0-SNAPSHOT.jar -t
```

Output: `outputs/yyyyMMdd_HHmmss.csv`

CSV columns: `project_key, project_name, security_issues, reliability_issues, maintainability_issues, ncloc, lines`

### Stage 2 — Generate a report

Run the report generator against the latest CSV in `outputs/`:

```bash
java -jar report/target/reports-api-sqs-report-1.0-SNAPSHOT.jar
```

Or point it at a specific CSV file:

```bash
java -jar report/target/reports-api-sqs-report-1.0-SNAPSHOT.jar outputs/20260409_120000.csv
```

#### Output format

By default a PDF is produced. Use `--format` to choose:

| Flag | Output |
|------|--------|
| `--format=pdf` | PDF report (default) |
| `--format=html` | HTML report |
| `--format=both` | Both PDF and HTML |

```bash
java -jar report/target/reports-api-sqs-report-1.0-SNAPSHOT.jar --format=html
java -jar report/target/reports-api-sqs-report-1.0-SNAPSHOT.jar --format=both outputs/20260409_120000.csv
```

Output files are written alongside the CSV with the same base name, e.g. `outputs/20260409_120000.pdf`.

## Report contents

Both PDF and HTML reports include:

- Summary stat cards — project count, total issues, security issues, lines of code
- Horizontal bar chart — top 30 projects by total issue count, broken down by Security Issues / Reliability Issues / Maintainability Issues

The HTML report additionally supports:

- **Tooltips** — hover over any bar to see exact values for all three issue types
- **Issue type filter** — checkboxes to show/hide Security, Reliability, or Maintainability issues
- **Project search** — text filter to narrow down projects shown in the chart
