package com.pred.apitests.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class ExcelTrackerListener implements ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelTrackerListener.class);
    private static final String TRACKER_FILE_NAME = "test_run_tracker.xlsx";
    private static final String SESSION_FILE_NAME = ".env.tracker";
    private static final String SHEET_NAME = "Run Log";
    private static final String WEBHOOK_KEY = "GSHEET_WEBHOOK_URL=";

    @Override
    public void onFinish(ISuite suite) {
        try {
            int passedCount = 0;
            int failedCount = 0;
            int skippedCount = 0;
            List<String> failedMethods = new ArrayList<>();

            for (ISuiteResult suiteResult : suite.getResults().values()) {
                ITestContext context = suiteResult.getTestContext();
                passedCount += context.getPassedTests().size();
                failedCount += context.getFailedTests().size();
                skippedCount += context.getSkippedTests().size();

                for (ITestResult failedResult : context.getFailedTests().getAllResults()) {
                    failedMethods.add(failedResult.getMethod().getMethodName());
                }
            }

            int totalCount = passedCount + failedCount + skippedCount;
            int retriedCount = skippedCount;

            StringJoiner joiner = new StringJoiner(", ");
            for (String methodName : failedMethods) {
                joiner.add(methodName);
            }
            String failedTestNames = joiner.toString();

            File trackerFile = new File(System.getProperty("user.dir"), TRACKER_FILE_NAME);
            if (!trackerFile.exists()) {
                LOG.warn("[TRACKER] test_run_tracker.xlsx not found, skipping local Excel update");
            } else {
                try (FileInputStream fis = new FileInputStream(trackerFile);
                     Workbook workbook = WorkbookFactory.create(fis)) {

                    Sheet sheet = workbook.getSheet(SHEET_NAME);
                    if (sheet == null) {
                        LOG.warn("[TRACKER] Sheet '{}' not found in {}. Skipping local Excel update.", SHEET_NAME, trackerFile.getAbsolutePath());
                    } else {
                        int targetRow = findFirstEmptyDataRow(sheet);
                        Row row = sheet.getRow(targetRow);
                        if (row == null) row = sheet.createRow(targetRow);

                        CellStyle dateStyle = workbook.createCellStyle();
                        short dateFormat = workbook.getCreationHelper().createDataFormat().getFormat("YYYY-MM-DD");
                        dateStyle.setDataFormat(dateFormat);

                        CellStyle timeStyle = workbook.createCellStyle();
                        short timeFormat = workbook.getCreationHelper().createDataFormat().getFormat("HH:mm");
                        timeStyle.setDataFormat(timeFormat);

                        Date now = new Date();

                        writeNumeric(row, 0, targetRow);
                        writeDate(row, 1, now, dateStyle);
                        writeDate(row, 2, now, timeStyle);
                        writeNumeric(row, 3, totalCount);
                        writeNumeric(row, 4, passedCount);
                        writeNumeric(row, 5, failedCount);
                        writeNumeric(row, 6, skippedCount);
                        writeNumeric(row, 7, retriedCount);
                        // Column I (index 8) contains pass-rate formula and is intentionally left untouched.
                        writeString(row, 9, failedTestNames);
                        writeString(row, 10, "");

                        try (FileOutputStream fos = new FileOutputStream(trackerFile)) {
                            workbook.write(fos);
                        }

                        LOG.info("[TRACKER] Run #{} logged to Excel: {} passed, {} failed, {} skipped",
                                targetRow, passedCount, failedCount, skippedCount);
                    }
                }
            }

            String webhookUrl = readWebhookUrlFromSessionFile();
            if (webhookUrl == null || webhookUrl.isBlank()) {
                LOG.warn("[TRACKER] GSHEET_WEBHOOK_URL not set in .env.tracker, skipping Google Sheets update");
            } else {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("passed", passedCount);
                payload.put("failed", failedCount);
                payload.put("skipped", skippedCount);
                payload.put("retried", retriedCount);
                payload.put("failedTests", failedTestNames);
                String json = mapper.writeValueAsString(payload);

                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                LOG.info("[TRACKER] Google Sheets updated (HTTP {})", response.statusCode());
            }
        } catch (Exception e) {
            LOG.warn("[TRACKER] Failed to update tracker: {}", e.getMessage());
        }
    }

    private int findFirstEmptyDataRow(Sheet sheet) {
        int rowIdx = 1;
        while (true) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) return rowIdx;
            Cell totalCell = row.getCell(3);
            if (totalCell == null) return rowIdx;
            String value = totalCell.toString();
            if (value == null || value.trim().isEmpty()) return rowIdx;
            rowIdx++;
        }
    }

    private void writeNumeric(Row row, int columnIdx, double value) {
        Cell cell = row.getCell(columnIdx);
        if (cell == null) cell = row.createCell(columnIdx);
        cell.setCellValue(value);
    }

    private void writeString(Row row, int columnIdx, String value) {
        Cell cell = row.getCell(columnIdx);
        if (cell == null) cell = row.createCell(columnIdx);
        cell.setCellValue(value != null ? value : "");
    }

    private void writeDate(Row row, int columnIdx, Date value, CellStyle style) {
        Cell cell = row.getCell(columnIdx);
        if (cell == null) cell = row.createCell(columnIdx);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String readWebhookUrlFromSessionFile() {
        Path path = Path.of(System.getProperty("user.dir"), SESSION_FILE_NAME);
        if (!Files.exists(path)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Strip leading "export " if present
                String normalized = trimmed.startsWith("export ") ? trimmed.substring(7).trim() : trimmed;
                if (normalized.startsWith(WEBHOOK_KEY)) {
                    String value = normalized.substring(WEBHOOK_KEY.length()).trim();
                    // Remove surrounding quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (Exception e) {
            LOG.warn("[TRACKER] Failed reading .env.tracker for webhook URL: {}", e.getMessage());
        }
        return null;
    }
}
