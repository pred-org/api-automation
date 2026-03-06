package com.pred.apitests.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Base configuration for main code. Reads from classpath properties, then system properties, then env.
 * Used by BaseService and services. Test run has test resources on classpath.
 */
public final class Config {

    private static final String DEFAULT_PUBLIC_BASE = "https://api.example.com";
    private static final Properties PROPS = loadProperties();

    private Config() {}

    private static Properties loadProperties() {
        Properties p = new Properties();
        loadFromClasspath(p, "application.properties");
        loadFromClasspath(p, "testdata.properties");
        return p;
    }

    private static void loadFromClasspath(Properties p, String name) {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(name)) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // use defaults
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String trimSlash(String v) {
        return v == null ? null : v.replaceAll("/+$", "");
    }

    public static String getPublicBaseUri() {
        return trimSlash(firstNonBlank(
                System.getProperty("api.base.uri.public"),
                System.getenv("API_BASE_URI_PUBLIC"),
                PROPS.getProperty("api.base.uri.public"),
                System.getProperty("api.base.uri"),
                System.getenv("API_BASE_URI"),
                PROPS.getProperty("api.base.uri"),
                DEFAULT_PUBLIC_BASE
        ));
    }

    public static String getInternalBaseUri() {
        return trimSlash(firstNonBlank(
                System.getProperty("api.base.uri.internal"),
                System.getenv("API_BASE_URI_INTERNAL"),
                PROPS.getProperty("api.base.uri.internal"),
                getPublicBaseUri()
        ));
    }

    public static String getApiKey() {
        return firstNonBlank(
                System.getProperty("api.key"),
                System.getenv("API_KEY"),
                PROPS.getProperty("api.key")
        );
    }

    public static String getSigServerUrl() {
        return trimSlash(firstNonBlank(
                System.getProperty("sig.server.url"),
                System.getenv("SIG_SERVER_URL"),
                PROPS.getProperty("sig.server.url"),
                "http://localhost:5050"
        ));
    }

    /** EOA address (wallet) from env/properties. Use when TokenManager.getEoa() is null (e.g. deposit runs without Auth Flow in same run). */
    public static String getEoaAddress() {
        return firstNonBlank(
                System.getProperty("eoa.address"),
                System.getenv("EOA_ADDRESS"),
                PROPS.getProperty("eoa.address")
        );
    }

    /** Private key for sign-create-proxy and sign-safe-approval (same wallet as EOA). From properties or env. */
    public static String getPrivateKey() {
        return firstNonBlank(
                System.getProperty("private.key"),
                System.getenv("PRIVATE_KEY"),
                PROPS.getProperty("private.key")
        );
    }

    public static String getMarketId() {
        return firstNonBlank(
                System.getProperty("market.id"),
                System.getenv("MARKET_ID"),
                PROPS.getProperty("market.id")
        );
    }

    public static String getTokenId() {
        return firstNonBlank(
                System.getProperty("token.id"),
                System.getenv("TOKEN_ID"),
                PROPS.getProperty("token.id")
        );
    }

    public static long getDepositAmount() {
        String v = firstNonBlank(
                System.getProperty("deposit.amount"),
                System.getenv("DEPOSIT_AMOUNT"),
                PROPS.getProperty("deposit.amount")
        );
        if (v == null || v.isBlank()) return 1_000_000_000L;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 1_000_000_000L;
        }
    }

    /** Slack Incoming Webhook URL. If blank, Slack notification is skipped. */
    public static String getSlackWebhookUrl() {
        return firstNonBlank(
                System.getProperty("slack.webhook.url"),
                System.getenv("SLACK_WEBHOOK_URL"),
                PROPS.getProperty("slack.webhook.url")
        );
    }
}
