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

    @BeforeMethod(alwaysRun = true)
    public void beforeEachTest() {
        if (TokenManager.getInstance().hasToken() && TokenManager.getInstance().isTokenExpiringSoon()) {
            new AuthService().refreshIfExpiringSoon();
        }
    }
}
