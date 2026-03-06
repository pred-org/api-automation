package com.pred.apitests.listeners;

import com.pred.apitests.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends a one-line summary to Slack after the suite finishes: total, passed, failed, skipped.
 * Only runs if SLACK_WEBHOOK_URL (or slack.webhook.url) is set.
 */
public class SlackNotificationListener implements ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(SlackNotificationListener.class);

    @Override
    public void onFinish(ISuite suite) {
        String webhookUrl = Config.getSlackWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOG.debug("Slack webhook not set; skipping notification");
            return;
        }

        int total = 0, passed = 0, failed = 0, skipped = 0;
        for (var entry : suite.getResults().entrySet()) {
            ITestContext ctx = entry.getValue().getTestContext();
            passed += ctx.getPassedTests().size();
            failed += ctx.getFailedTests().size();
            skipped += ctx.getSkippedTests().size();
        }
        total = passed + failed + skipped;

        String text = String.format(
                "API Automation: %d total | %d passed | %d failed | %d skipped",
                total, passed, failed, skipped
        );
        sendToSlack(webhookUrl, text);
    }

    private static void sendToSlack(String webhookUrl, String text) {
        String body = "{\"text\":\"" + escapeJson(text) + "\"}";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.info("Slack notification sent: {}", text);
            } else {
                LOG.warn("Slack webhook returned {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.warn("Failed to send Slack notification: {}", e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
