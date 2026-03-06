package com.pred.apitests.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pred.apitests.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends a summary to Slack after the suite finishes: total, passed, failed, skipped.
 * When SLACK_BOT_TOKEN and SLACK_CHANNEL are set, uses Slack Web API (chat.postMessage):
 * posts the summary, then a thread reply listing each test with status [OK]/[FAIL]/[SKIP].
 * When only SLACK_WEBHOOK_URL is set, sends a single line via Incoming Webhook (no thread).
 */
public class SlackNotificationListener implements ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(SlackNotificationListener.class);
    private static final String SLACK_POST_MESSAGE = "https://slack.com/api/chat.postMessage";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void onFinish(ISuite suite) {
        String botToken = Config.getSlackBotToken();
        String channel = Config.getSlackChannel();
        String webhookUrl = Config.getSlackWebhookUrl();

        int total = 0, passed = 0, failed = 0, skipped = 0;
        List<TestStatus> testStatuses = new ArrayList<>();
        for (var entry : suite.getResults().entrySet()) {
            ITestContext ctx = entry.getValue().getTestContext();
            passed += ctx.getPassedTests().size();
            failed += ctx.getFailedTests().size();
            skipped += ctx.getSkippedTests().size();
            collectTestStatuses(ctx.getPassedTests().getAllResults(), ITestResult.SUCCESS, testStatuses);
            collectTestStatuses(ctx.getFailedTests().getAllResults(), ITestResult.FAILURE, testStatuses);
            collectTestStatuses(ctx.getSkippedTests().getAllResults(), ITestResult.SKIP, testStatuses);
        }
        total = passed + failed + skipped;

        String summary = String.format(
                "API Automation: %d total | %d passed | %d failed | %d skipped",
                total, passed, failed, skipped
        );

        if (botToken != null && !botToken.isBlank() && channel != null && !channel.isBlank()) {
            boolean sent = sendViaWebApi(botToken, channel, summary, testStatuses);
            if (!sent && webhookUrl != null && !webhookUrl.isBlank()) {
                LOG.debug("Slack Web API failed; falling back to webhook for summary");
                sendViaWebhook(webhookUrl, summary);
            }
        } else if (webhookUrl != null && !webhookUrl.isBlank()) {
            sendViaWebhook(webhookUrl, summary);
        } else {
            LOG.debug("Slack not configured (no SLACK_BOT_TOKEN+SLACK_CHANNEL and no SLACK_WEBHOOK_URL); skipping notification");
        }
    }

    private static void collectTestStatuses(java.util.Collection<ITestResult> results, int status, List<TestStatus> out) {
        if (results == null) return;
        for (ITestResult r : results) {
            String name = r.getMethod() != null ? r.getMethod().getMethodName() : "unknown";
            out.add(new TestStatus(name, status));
        }
    }

    /** Returns true if the summary was sent successfully (and optionally thread); false if Web API failed. */
    private static boolean sendViaWebApi(String botToken, String channel, String summary, List<TestStatus> testStatuses) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String summaryBody = buildPostMessageBody(channel, summary);
        HttpRequest summaryRequest = HttpRequest.newBuilder()
                .uri(URI.create(SLACK_POST_MESSAGE))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + botToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(summaryBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> summaryResponse = client.send(summaryRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (summaryResponse.statusCode() != 200) {
                LOG.warn("Slack API returned {}: {}", summaryResponse.statusCode(), summaryResponse.body());
                return false;
            }
            JsonNode root = JSON.readTree(summaryResponse.body());
            if (root != null && !root.path("ok").asBoolean(false)) {
                LOG.warn("Slack API ok=false: {}", summaryResponse.body());
                return false;
            }
            String ts = root.path("ts").asText("");
            if (ts.isBlank()) {
                LOG.warn("Slack API response missing ts: {}", summaryResponse.body());
                return false;
            }
            LOG.info("Slack notification sent: {}", summary);

            StringBuilder threadLines = new StringBuilder();
            for (TestStatus t : testStatuses) {
                String prefix = t.status == ITestResult.SUCCESS ? "[OK] " : (t.status == ITestResult.FAILURE ? "[FAIL] " : "[SKIP] ");
                String label = t.status == ITestResult.SUCCESS ? "passed" : (t.status == ITestResult.FAILURE ? "failed" : "skipped");
                threadLines.append(prefix).append(t.methodName).append(" - ").append(label).append("\n");
            }
            if (threadLines.length() == 0) {
                threadLines.append("(no test results)");
            } else {
                threadLines.setLength(threadLines.length() - 1);
            }
            String threadBody = buildPostMessageBody(channel, threadLines.toString(), ts);
            HttpRequest threadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SLACK_POST_MESSAGE))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + botToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(threadBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> threadResponse = client.send(threadRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (threadResponse.statusCode() != 200) {
                LOG.warn("Slack thread reply returned {}: {}", threadResponse.statusCode(), threadResponse.body());
            } else {
                JsonNode threadRoot = JSON.readTree(threadResponse.body());
                if (threadRoot != null && !threadRoot.path("ok").asBoolean(false)) {
                    LOG.warn("Slack thread reply ok=false: {}", threadResponse.body());
                }
            }
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to send Slack notification: {}", e.getMessage());
            return false;
        }
    }

    private static String buildPostMessageBody(String channel, String text) {
        return buildPostMessageBody(channel, text, null);
    }

    private static String buildPostMessageBody(String channel, String text, String threadTs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"channel\":\"").append(escapeJson(channel)).append("\",\"text\":\"").append(escapeJson(text)).append("\"");
        if (threadTs != null && !threadTs.isBlank()) {
            sb.append(",\"thread_ts\":\"").append(escapeJson(threadTs)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static void sendViaWebhook(String webhookUrl, String text) {
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

    private static final class TestStatus {
        final String methodName;
        final int status;

        TestStatus(String methodName, int status) {
            this.methodName = methodName;
            this.status = status;
        }
    }
}
