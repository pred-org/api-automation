package com.pred.apitests;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.util.TokenManager;
import com.pred.apitests.service.AuthService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        Response response = authService.createApiKey();
        assertThat(response.getStatusCode()).isEqualTo(200);
        String body = response.getBody().asString();
        assertThat(body).isNotBlank();
    }

    @Test(description = "Login with invalid body returns 4xx")
    public void loginWithInvalidBody_returns4xx() {
        String apiKey = getApiKeyForLogin();
        LoginRequest invalidBody = LoginRequest.builder()
                .data(new LoginRequest.LoginRequestData())
                .build();
        Response response = authService.login(apiKey, invalidBody);
        assertThat(response.getStatusCode()).isGreaterThanOrEqualTo(400).isLessThan(500);
    }

    private String getApiKeyForLogin() {
        String fromConfig = com.pred.apitests.config.Config.getApiKey();
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig;
        }
        Response createResponse = authService.createApiKey();
        if (createResponse.getStatusCode() == 200) {
            return authService.parseApiKey(createResponse);
        }
        throw new IllegalStateException("No API key: set API_KEY env or ensure createApiKey succeeds");
    }
}
