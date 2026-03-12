package com.pred.apitests.base;

import com.pred.apitests.service.AuthService;
import com.pred.apitests.util.TokenManager;
import org.testng.annotations.BeforeMethod;

/**
 * Base class for API tests.
 * Uses TestNG lifecycle only; HTTP config lives in BaseService.
 * Proactive token refresh: before each test, if token is expiring soon (~40 min), refresh so long runs stay authenticated.
 */
public abstract class BaseApiTest {

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

    @BeforeMethod(alwaysRun = true)
    public void beforeEachTest() {
        if (TokenManager.getInstance().hasToken() && TokenManager.getInstance().isTokenExpiringSoon()) {
            new AuthService().refreshIfExpiringSoon();
        }
    }
}
