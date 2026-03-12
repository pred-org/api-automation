package com.pred.apitests.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes the current session (access token, refresh cookie, user id, proxy, eoa) to a file
 * so k6 or other tools can source it without copy-paste. File is written to project root as .env.session.
 */
public final class SessionFileWriter {

    private static final String FILENAME = ".env.session";
    private static final String FILENAME_USER2 = ".env.session2";

    private SessionFileWriter() {}

    /**
     * Write TokenManager's session to .env.session in project root (user.dir).
     * Skips writing if any required value is missing. Overwrites the file.
     */
    public static boolean writeFromTokenManager() {
        TokenManager tm = TokenManager.getInstance();
        if (!tm.hasToken()) return false;
        String refreshCookie = tm.getRefreshCookieHeaderValue();
        String userId = tm.getUserId();
        String proxy = tm.getProxyWalletAddress();
        String eoa = tm.getEoa();
        if (userId == null || userId.isBlank() || proxy == null || proxy.isBlank()) return false;
        return write(tm.getAccessToken(), refreshCookie, userId, proxy, eoa != null ? eoa : "");
    }

    /**
     * Write session vars to .env.session in project root. Values are shell-safe (quoted, escaped).
     */
    public static boolean write(String accessToken, String refreshCookie, String userId, String proxy, String eoa) {
        if (accessToken == null || accessToken.isBlank() || userId == null || userId.isBlank()
                || proxy == null || proxy.isBlank()) {
            return false;
        }
        String cookieLine = (refreshCookie != null && !refreshCookie.isBlank())
                ? "export REFRESH_COOKIE=" + escape(refreshCookie)
                : "# export REFRESH_COOKIE not set";
        String eoaLine = (eoa != null && !eoa.isBlank())
                ? "export EOA=" + escape(eoa)
                : "# export EOA not set";
        String body = ""
                + "# Session for k6 / shell. Run: source .env.session\n"
                + "# Generated after login; do not commit.\n"
                + "export ACCESS_TOKEN=" + escape(accessToken) + "\n"
                + cookieLine + "\n"
                + "export USER_ID=" + escape(userId) + "\n"
                + "export PROXY=" + escape(proxy) + "\n"
                + eoaLine + "\n";
        Path path = Paths.get(System.getProperty("user.dir", ""), FILENAME);
        try {
            Files.writeString(path, body, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Write second user session to .env.session2 in project root (for two-user flows).
     * Optional privateKey; if provided it is written so SecondUserContext can sign for user 2.
     */
    public static boolean writeSecondUser(String accessToken, String refreshCookie, String userId, String proxy, String eoa, String privateKey) {
        if (accessToken == null || accessToken.isBlank() || userId == null || userId.isBlank()
                || proxy == null || proxy.isBlank()) {
            return false;
        }
        String cookieLine = (refreshCookie != null && !refreshCookie.isBlank())
                ? "export REFRESH_COOKIE=" + escape(refreshCookie)
                : "# export REFRESH_COOKIE not set";
        String eoaLine = (eoa != null && !eoa.isBlank())
                ? "export EOA=" + escape(eoa)
                : "# export EOA not set";
        String pkLine = (privateKey != null && !privateKey.isBlank())
                ? "export PRIVATE_KEY=" + escape(privateKey)
                : "# export PRIVATE_KEY not set (use PRIVATE_KEY_2 env or second.user.private.key)";
        String body = ""
                + "# Second user session (two-user flows). Do not commit.\n"
                + "export ACCESS_TOKEN=" + escape(accessToken) + "\n"
                + cookieLine + "\n"
                + "export USER_ID=" + escape(userId) + "\n"
                + "export PROXY=" + escape(proxy) + "\n"
                + eoaLine + "\n"
                + pkLine + "\n";
        Path path = Paths.get(System.getProperty("user.dir", ""), FILENAME_USER2);
        try {
            Files.writeString(path, body, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String escape(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
