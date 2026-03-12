package com.pred.apitests.util;

import com.pred.apitests.config.Config;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the second user's session (for two-user flows: place long + short so orders can match -> positions).
 * Loaded from env (USER_2_ACCESS_TOKEN, USER_2_REFRESH_COOKIE, USER_2_USER_ID, USER_2_PROXY, USER_2_EOA, USER_2_PRIVATE_KEY)
 * or from .env.session2 in project root (same format as .env.session).
 */
public final class SecondUserContext {

    private static final String SESSION2_FILENAME = ".env.session2";
    private static UserSession secondUser;

    private SecondUserContext() {}

    /**
     * Get the second user's session, or null if not configured.
     * Loads once from env or .env.session2; subsequent calls return the same instance.
     */
    public static synchronized UserSession getSecondUser() {
        if (secondUser != null) return secondUser;
        secondUser = loadFromEnv();
        if (secondUser != null) return secondUser;
        secondUser = loadFromSession2File();
        if (secondUser != null) return secondUser;
        return null;
    }

    /** Clear cached second user (e.g. after re-auth). */
    public static synchronized void clear() {
        secondUser = null;
    }

    private static UserSession loadFromEnv() {
        String token = System.getenv("USER_2_ACCESS_TOKEN");
        String userId = System.getenv("USER_2_USER_ID");
        String proxy = System.getenv("USER_2_PROXY");
        if (token == null || token.isBlank() || userId == null || userId.isBlank() || proxy == null || proxy.isBlank()) {
            return null;
        }
        String cookie = System.getenv("USER_2_REFRESH_COOKIE");
        if (cookie == null) cookie = "";
        String eoa = System.getenv("USER_2_EOA");
        if (eoa == null) eoa = "";
        String privateKey = System.getenv("USER_2_PRIVATE_KEY");
        if (privateKey == null || privateKey.isBlank()) privateKey = Config.getSecondUserPrivateKey();
        if (privateKey == null) privateKey = "";
        return new UserSession(token, cookie, userId, proxy, eoa, privateKey);
    }

    private static UserSession loadFromSession2File() {
        Path path = Paths.get(System.getProperty("user.dir", ""), SESSION2_FILENAME);
        if (!Files.isRegularFile(path)) return null;
        Map<String, String> vars = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .replace("\\n", "\n");
                }
                vars.put(key, value);
            }
        } catch (Exception ignored) {
            return null;
        }
        String token = vars.get("ACCESS_TOKEN");
        String userId = vars.get("USER_ID");
        String proxy = vars.get("PROXY");
        if (token == null || token.isBlank() || userId == null || userId.isBlank() || proxy == null || proxy.isBlank()) {
            return null;
        }
        String cookie = vars.getOrDefault("REFRESH_COOKIE", "");
        String eoa = vars.getOrDefault("EOA", "");
        String privateKey = vars.getOrDefault("PRIVATE_KEY", "");
        if (privateKey.isBlank()) privateKey = Config.getSecondUserPrivateKey();
        if (privateKey == null) privateKey = "";
        return new UserSession(token, cookie, userId, proxy, eoa, privateKey);
    }
}
