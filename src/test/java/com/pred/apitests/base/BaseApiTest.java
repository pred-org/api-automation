package com.pred.apitests.base;

import com.pred.apitests.service.AuthService;
import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.TokenManager;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.annotations.BeforeMethod;

import java.util.List;

/**
 * Base class for API tests.
 * Uses TestNG lifecycle only; HTTP config lives in BaseService.
 * Proactive token refresh: before each test, if token is expiring soon (~40 min), refresh so long runs stay authenticated.
 * User 2 is refreshed in-suite when present and last refresh was > 35 min ago (or never), then .env.session2 is updated.
 * Override getSession() in *User2 test classes to run the same tests as user 2 (SecondUserContext).
 */
public abstract class BaseApiTest {

    private static final long USER2_REFRESH_INTERVAL_MS = 35 * 60 * 1000L;
    private static long lastUser2RefreshMs = 0;

    /** Current user session (user 1 from TokenManager by default). Override in *User2 classes to return SecondUserContext.getSecondUser(). */
    protected UserSession getSession() {
        return TokenManager.getInstance().getSession();
    }

    /** Parse balance string that may be integer or decimal (e.g. "34000000003" or "34000000003.1") to long. Truncates decimal part. */
    protected static long parseBalanceAsLong(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("balance value null or blank");
        return (long) Double.parseDouble(value.trim());
    }

    /** Parse PnL string (may be negative or decimal, e.g. "0", "-3.5") to double. Returns 0 if null/blank. */
    protected static double parsePnlAsDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        return Double.parseDouble(value.trim());
    }

    /** Trade-history API may return list under "trade_history" or "data". Returns that list or null. */
    protected static List<?> getTradeHistoryList(Response response) {
        List<?> list = response.path("trade_history");
        if (list == null) list = response.path("data");
        return list;
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeEachTest() {
        if (TokenManager.getInstance().hasToken() && TokenManager.getInstance().isTokenExpiringSoon()) {
            new AuthService().refreshIfExpiringSoon();
        }
        UserSession user2 = SecondUserContext.getSecondUser();
        if (user2 != null && user2.hasToken() && user2.getRefreshCookieHeaderValue() != null && !user2.getRefreshCookieHeaderValue().isBlank()) {
            long now = System.currentTimeMillis();
            boolean isUser2TestClass = getClass().getSimpleName().contains("User2");
            boolean shouldRefresh = isUser2TestClass || lastUser2RefreshMs == 0 || (now - lastUser2RefreshMs) > USER2_REFRESH_INTERVAL_MS;
            if (shouldRefresh && new AuthService().refreshSecondUserAndStore()) {
                lastUser2RefreshMs = now;
            }
        }
    }
}
