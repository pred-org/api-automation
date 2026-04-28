package com.pred.apitests;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.util.TokenManager;
import com.pred.apitests.service.AuthService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeMethod;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Real login flow: create API key, login with invalid body (4xx), and optional valid login when credentials available.
 */
public class LoginTest extends BaseApiTest {

    private AuthService authService;

    @BeforeMethod
    public void setUp() {
        authService = new AuthService();
        TokenManager.getInstance().clear();
    }

    @Test(description = "Create API key returns 200 and non-empty key body")
    public void createApiKey_returns200AndKey() {
        try {
            Response response = authService.createApiKey();
            assertThat(response.getStatusCode()).isEqualTo(200);
            String body = response.getBody().asString();
            assertThat(body).isNotBlank();
        } catch (Exception e) {
            if (isNoRouteToHost(e)) {
                throw new SkipException("Internal API key endpoint unreachable (NoRouteToHost). Set API_KEY to skip internal createApiKey for CI.");
            }
            throw e;
        }
    }

    @Test(description = "Login with invalid body returns 4xx")
    public void loginWithInvalidBody_returns4xx() {
        String apiKey = getApiKeyForLogin();
        LoginRequest invalidBody = LoginRequest.builder()
                .data(new LoginRequest.LoginRequestData())
                .build();
        Response response = authService.login(apiKey, invalidBody);
        assertThat(response.getStatusCode()).as("invalid login body → 400").isEqualTo(400);
        response.then().body("error", notNullValue())
                .body("error.status_code", equalTo(400))
                .body("error.error_code", equalTo("BAD_REQUEST"))
                .body("error.message", notNullValue());
    }

    private String getApiKeyForLogin() {
        String fromConfig = com.pred.apitests.config.Config.getApiKey();
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig;
        }
        Response createResponse;
        try {
            createResponse = authService.createApiKey();
        } catch (Exception e) {
            if (isNoRouteToHost(e)) {
                throw new SkipException("Internal API key endpoint unreachable (NoRouteToHost). Set API_KEY to skip internal createApiKey for CI.");
            }
            throw e;
        }
        if (createResponse.getStatusCode() == 200) return authService.parseApiKey(createResponse);
        throw new IllegalStateException("No API key: set API_KEY env or ensure createApiKey succeeds");
    }

    private static boolean isNoRouteToHost(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof java.net.NoRouteToHostException) return true;
            if (cur instanceof java.net.UnknownHostException) return true;
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains("no route to host")) return true;
            cur = cur.getCause();
        }
        return false;
    }
}
