package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.LoginResponse;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.SessionFileWriter;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login as second user (different wallet) and write session to .env.session2.
 * Run after AuthFlowTest (user 1). Requires second wallet: set PRIVATE_KEY_2 or second.user.private.key in testdata.properties / env.
 * Two-user flows (place long + short to get matches/positions) use SecondUserContext.getSecondUser() which loads from .env.session2 or USER_2_* env.
 */
public class AuthFlowTestUser2 extends BaseApiTest {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFlowTestUser2.class);
    private AuthService authService;
    private SignatureService signatureService;
    private String apiKey;

    @BeforeClass(alwaysRun = true)
    public void setupSecondUser() {
        String secondPrivateKey = Config.getSecondUserPrivateKey();
        if (secondPrivateKey == null || secondPrivateKey.isBlank()) {
            throw new SkipException("Second user not configured: set PRIVATE_KEY_2 or second.user.private.key (and run AuthFlowTest first for API key)");
        }

        authService = new AuthService();
        signatureService = new SignatureService();
        apiKey = Config.getSecondUserApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            LOG.info("API_KEY_2 not set — creating a new API key for user 2 via internal endpoint");
            Response keyResponse = authService.createApiKey();
            assertThat(keyResponse.getStatusCode()).as("createApiKey for user 2").isEqualTo(200);
            apiKey = authService.parseApiKey(keyResponse);
        }
        assertThat(apiKey).as("API key for user 2 (from config or newly created)").isNotBlank();

        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(Config.getSigServerUrl(), secondPrivateKey);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();
        assertThat(sigResponse.getWalletAddress()).isNotBlank();

        long now = System.currentTimeMillis();
        LoginRequest loginRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-" + now + "-" + (now / 1000))
                .chainType("base-sepolia")
                .timestamp(now / 1000)
                .build();

        Response response = authService.login(apiKey, loginRequest);
        int status = response.getStatusCode();
        if (status == 401) {
            throw new SkipException(
                    "User 2 login returned 401 - backend may allow only one user per API key. "
                            + "Set a separate API key for user 2 or enable multi-user for this key. Skipping user 2 tests.");
        }
        assertThat(status).as("User 2 login").isEqualTo(200);
        LoginResponse loginResponse = authService.parseLoginResponse(response);
        assertThat(loginResponse).isNotNull();
        authService.fillLoginResponseFromJsonPath(response, loginResponse);
        String token = loginResponse.getAccessToken();
        if (token == null || token.isBlank()) {
            token = authService.extractAccessTokenFromResponse(response);
            if (token != null) loginResponse.setAccessToken(token);
        }
        assertThat(token).isNotBlank();
        String userId = loginResponse.getUserId();
        String proxy = loginResponse.getProxyWalletAddress();
        assertThat(userId).isNotBlank();
        assertThat(proxy).isNotBlank();
        String refreshCookie = authService.extractRefreshTokenFromResponse(response);
        if (refreshCookie == null) refreshCookie = "";
        String eoa = sigResponse.getWalletAddress();

        boolean written = SessionFileWriter.writeSecondUser(token, refreshCookie, userId, proxy, eoa, secondPrivateKey);
        assertThat(written).isTrue();
        SecondUserContext.clear();
        LOG.info("Second user session written to .env.session2 (userId={}, proxy={})", userId, proxy);
    }

    @Test(groups = {"auth-user2-complete"}, description = "Second user session is loadable")
    public void secondUserSessionIsLoadable() {
        var session = SecondUserContext.getSecondUser();
        assertThat(session).isNotNull();
        assertThat(session.hasToken()).isTrue();
        assertThat(session.getUserId()).isNotBlank();
        assertThat(session.getProxy()).isNotBlank();
    }
}
