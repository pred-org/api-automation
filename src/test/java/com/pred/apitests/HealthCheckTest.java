package com.pred.apitests;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.service.UserService;
import com.pred.apitests.util.TokenManager;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Platform health check (pre-condition for all tests).
 * Test case 1 from API reference: GET /user/maintainance (downtime == false), GET /user/me (status ACTIVE, is_enabled_trading true).
 * Requires token from AuthFlowTest; skips if no token.
 */
public class HealthCheckTest extends BaseApiTest {

    @Test(description = "GET /user/maintainance -> downtime == false")
    public void maintainance_downtimeFalse() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        var response = new UserService().getMaintainance(
                TokenManager.getInstance().getAccessToken(),
                TokenManager.getInstance().getRefreshCookieHeaderValue());
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("downtime", equalTo(false));
    }

    @Test(description = "GET /user/me -> status == ACTIVE and is_enabled_trading == true")
    public void userMe_statusActive_enabledTrading() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        var response = new UserService().getMe(
                TokenManager.getInstance().getAccessToken(),
                TokenManager.getInstance().getRefreshCookieHeaderValue());
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then()
                .body("status", equalTo("ACTIVE"))
                .body("is_enabled_trading", equalTo(true));
    }
}
