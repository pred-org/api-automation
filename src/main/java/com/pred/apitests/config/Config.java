package com.pred.apitests.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Base configuration for main code. Reads from classpath properties, then system properties, then env.
 * Loads .env from project root (user.dir) if present so SLACK_* and other vars are available when running via Maven.
 */
public final class Config {

    private static final String DEFAULT_PUBLIC_BASE = "https://api.example.com";
    private static final Properties PROPS = loadProperties();

    static {
        loadEnvFile();
        clearStaleSessionFiles();
    }

    private Config() {}

    /** Load .env from project root into system properties so Config getters see them (e.g. SLACK_BOT_TOKEN -> slack.bot.token). */
    private static void loadEnvFile() {
        String env = firstNonBlank(System.getProperty("env"), System.getenv("ENV"));
        Path projectRoot = Paths.get(System.getProperty("user.dir", ""));
        Path envPath;

        if (env != null && !env.isBlank()) {
            Path envSpecific = projectRoot.resolve(".env." + env.toLowerCase().trim());
            if (Files.isRegularFile(envSpecific)) {
                envPath = envSpecific;
                System.out.println("[Config] Loading environment: " + env + " from " + envSpecific.getFileName());
            } else {
                System.out.println("[Config] WARNING: .env." + env + " not found, falling back to .env");
                envPath = projectRoot.resolve(".env");
            }
        } else {
            envPath = projectRoot.resolve(".env");
        }

        if (!Files.isRegularFile(envPath)) return;
        try (BufferedReader reader = Files.newBufferedReader(envPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
                if (key.isEmpty() || value.isEmpty()) continue;
                String sysKey = key.toLowerCase().replace('_', '.');
                if (key.startsWith("SLACK_")) sysKey = "slack." + key.substring(6).toLowerCase().replace('_', '.');
                else if (key.equals("API_BASE_URI_PUBLIC")) sysKey = "api.base.uri.public";
                else if (key.equals("API_BASE_URI_INTERNAL")) sysKey = "api.base.uri.internal";
                else if (key.equals("PRIVATE_KEY")) sysKey = "private.key";
                else if (key.equals("EOA_ADDRESS")) sysKey = "eoa.address";
                else if (key.equals("PRIVATE_KEY_2")) sysKey = "second.user.private.key";
                else if (key.equals("EOA_ADDRESS_2")) sysKey = "second.user.eoa";
                else if (key.equals("MARKET_ID")) sysKey = "market.id";
                else if (key.equals("CANONICAL_NAME")) sysKey = "canonical.name";
                else if (key.equals("SIG_SERVER_URL")) sysKey = "sig.server.url";
                else if (key.equals("API_KEY")) sysKey = "api.key";
                else if (key.equals("API_KEY_2")) sysKey = "second.user.api.key";
                else if (key.equals("AUTOMATION_FIXTURE_BOOTSTRAP")) sysKey = "automation.fixture.bootstrap";
                else if (key.equals("AUTOMATION_LEAGUE_ID")) sysKey = "automation.league.id";
                else if (key.equals("AUTOMATION_CNAME_PREFIX")) sysKey = "automation.cname.prefix";
                else if (key.equals("AUTOMATION_CNAME_MAX_SCAN")) sysKey = "automation.cname.max.scan";
                else if (key.equals("AUTOMATION_PARENT_MARKETS")) sysKey = "automation.parent.markets";
                else if (key.equals("AUTOMATION_TITLE_MARKER")) sysKey = "automation.title.marker";
                if (System.getProperty(sysKey) == null || System.getProperty(sysKey).isBlank())
                    System.setProperty(sysKey, value);
            }
        } catch (IOException ignored) {
            // .env optional
        }
    }

    private static void clearStaleSessionFiles() {
        Path root = Paths.get(System.getProperty("user.dir", ""));
        clearIfKeyChanged(root.resolve(".env.session"), System.getProperty("private.key"));
        clearIfKeyChanged(root.resolve(".env.session2"), System.getProperty("second.user.private.key"));
    }

    /**
     * Reads a session file for {@code PRIVATE_KEY=}. If the stored key differs from
     * {@code currentKey} (from .env), the file is deleted so the next run does a fresh login.
     * If there is no {@code PRIVATE_KEY=} line, the file is left unchanged (legacy session files
     * until {@link com.pred.apitests.util.SessionFileWriter#writeFromTokenManager()} overwrites them).
     */
    private static void clearIfKeyChanged(Path sessionFile, String currentKey) {
        if (!Files.isRegularFile(sessionFile)) {
            return;
        }
        if (currentKey == null || currentKey.isBlank()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(sessionFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                if (line.startsWith("PRIVATE_KEY=")) {
                    String storedKey = line.substring("PRIVATE_KEY=".length()).trim();
                    if (storedKey.startsWith("\"") && storedKey.endsWith("\"")) {
                        storedKey = storedKey.substring(1, storedKey.length() - 1);
                    }
                    if (!storedKey.isBlank() && !storedKey.equals(currentKey)) {
                        System.out.println("[Config] Private key changed — deleting stale "
                                + sessionFile.getFileName());
                        Files.deleteIfExists(sessionFile);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

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

    /** API key for user 2 login (when backend allows only one user per key). Env API_KEY_2 or property second.user.api.key. */
    public static String getSecondUserApiKey() {
        return firstNonBlank(
                System.getProperty("second.user.api.key"),
                System.getenv("API_KEY_2"),
                PROPS.getProperty("second.user.api.key")
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

    /** Second user private key (for two-user flows). Env PRIVATE_KEY_2 or property second.user.private.key. */
    public static String getSecondUserPrivateKey() {
        return firstNonBlank(
                System.getProperty("second.user.private.key"),
                System.getenv("PRIVATE_KEY_2"),
                PROPS.getProperty("second.user.private.key")
        );
    }

    /** Second user EOA (optional override when loading from .env.session2). Env EOA_ADDRESS_2 or property second.user.eoa. */
    public static String getSecondUserEoa() {
        return firstNonBlank(
                System.getProperty("second.user.eoa"),
                System.getenv("EOA_ADDRESS_2"),
                PROPS.getProperty("second.user.eoa")
        );
    }

    public static String getMarketId() {
        return firstNonBlank(
                System.getProperty("market.id"),
                System.getenv("MARKET_ID"),
                PROPS.getProperty("market.id")
        );
    }

    /** Fixture canonical name for market discovery (e.g. westham-vs-wolves-2026). Optional if MARKET_ID is set. */
    public static String getCanonicalName() {
        return firstNonBlank(
                System.getProperty("canonical.name"),
                System.getenv("CANONICAL_NAME"),
                PROPS.getProperty("canonical.name")
        );
    }

    /**
     * When true and {@link #getCanonicalName()} is blank, {@code AutomationFixtureBootstrap} can create or reuse
     * a fixture cname (only-for-automation-1, ...). Env: {@code AUTOMATION_FIXTURE_BOOTSTRAP}, property {@code automation.fixture.bootstrap}.
     */
    public static boolean isAutomationFixtureBootstrapEnabled() {
        return parseTruthy(firstNonBlank(
                System.getProperty("automation.fixture.bootstrap"),
                System.getenv("AUTOMATION_FIXTURE_BOOTSTRAP"),
                PROPS.getProperty("automation.fixture.bootstrap")));
    }

    /** UAT EPL league id for sports-data matches. Env: {@code AUTOMATION_LEAGUE_ID}. */
    public static String getAutomationLeagueId() {
        return firstNonBlank(
                System.getProperty("automation.league.id"),
                System.getenv("AUTOMATION_LEAGUE_ID"),
                PROPS.getProperty("automation.league.id"),
                "de1bd252-baf5-4417-89ba-77d635f5f8f0");
    }

    /** Cname prefix for automation fixtures. Default: only-for-automation. */
    public static String getAutomationCnamePrefix() {
        return firstNonBlank(
                System.getProperty("automation.cname.prefix"),
                System.getenv("AUTOMATION_CNAME_PREFIX"),
                PROPS.getProperty("automation.cname.prefix"),
                "only-for-automation");
    }

    public static int getAutomationCnameMaxScan() {
        String v = firstNonBlank(
                System.getProperty("automation.cname.max.scan"),
                System.getenv("AUTOMATION_CNAME_MAX_SCAN"),
                PROPS.getProperty("automation.cname.max.scan"));
        if (v == null || v.isBlank()) {
            return 30;
        }
        try {
            return Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    /**
     * Comma-separated parent market families for create-fixture, e.g. moneyline,BTTS,totals_1.5
     * Env: {@code AUTOMATION_PARENT_MARKETS}.
     */
    public static String getAutomationParentMarketsSpec() {
        return firstNonBlank(
                System.getProperty("automation.parent.markets"),
                System.getenv("AUTOMATION_PARENT_MARKETS"),
                PROPS.getProperty("automation.parent.markets"),
                "moneyline,BTTS,totals_1.5");
    }

    /**
     * If set, reusing an existing cname requires a parent or fixture title to contain this marker (case-insensitive), e.g. Automation.
     * If blank, any successful non-empty discover passes.
     */
    public static String getAutomationTitleMarker() {
        return firstNonBlank(
                System.getProperty("automation.title.marker"),
                System.getenv("AUTOMATION_TITLE_MARKER"),
                PROPS.getProperty("automation.title.marker"));
    }

    private static boolean parseTruthy(String v) {
        if (v == null) {
            return false;
        }
        String t = v.trim();
        if (t.isEmpty()) {
            return false;
        }
        if ("1".equals(t)) {
            return true;
        }
        return "true".equalsIgnoreCase(t) || "yes".equalsIgnoreCase(t);
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

    /** Slack Bot Token (xoxb-...) for Web API (thread replies). If set with SLACK_CHANNEL, used instead of webhook. */
    public static String getSlackBotToken() {
        return firstNonBlank(
                System.getProperty("slack.bot.token"),
                System.getenv("SLACK_BOT_TOKEN"),
                PROPS.getProperty("slack.bot.token")
        );
    }

    /** Slack channel ID (e.g. C01234...) for chat.postMessage. Required when using SLACK_BOT_TOKEN. */
    public static String getSlackChannel() {
        return firstNonBlank(
                System.getProperty("slack.channel"),
                System.getenv("SLACK_CHANNEL"),
                PROPS.getProperty("slack.channel")
        );
    }
}
