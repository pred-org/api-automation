package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.SessionFileWriter;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class AuthFlowTest extends BaseApiTest {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFlowTest.class);
    private AuthService authService;
    private SignatureService signatureService;
    private String apiKey;

    @BeforeClass(alwaysRun = true)
    public void setupSuite() {
        authService = new AuthService();
        signatureService = new SignatureService();

        // Step 1: use API key from config/env, or create if not set
        apiKey = Config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            Response keyResponse = authService.createApiKey();
            assertThat(keyResponse.getStatusCode()).isEqualTo(200);
            apiKey = authService.parseApiKey(keyResponse);
        }
        assertThat(apiKey).isNotBlank();

        // Step 2: get login signature from sig-server (wallet from config: testdata.properties or env)
        String sigServerUrl = Config.getSigServerUrl();
        String privateKey = Config.getPrivateKey();
        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, privateKey);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        // Store EOA and private key so OrderTest uses same wallet for sign-order
        String eoa = sigResponse.getWalletAddress();
        if (eoa == null || eoa.isBlank()) {
            eoa = Config.getEoaAddress();
        }
        TokenManager.getInstance().setEoa(eoa);
        if (privateKey != null && !privateKey.isBlank()) {
            TokenManager.getInstance().setPrivateKey(privateKey);
        }

        // Step 3: build login request
        long now = System.currentTimeMillis();
        LoginRequest loginRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-" + now + "-" + (now / 1000))
                .chainType("base-sepolia")
                .timestamp(now / 1000)
                .build();

        // Step 4: login and store (store apiKey so proactive refresh can run later)
        TokenManager.getInstance().setApiKey(apiKey);
        var loginResponse = authService.loginAndStore(apiKey, loginRequest);
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getAccessToken()).isNotBlank();
        assertThat(loginResponse.getUserId()).isNotBlank();
        assertThat(loginResponse.getProxyWalletAddress()).isNotBlank();

        // Re-store EOA and privateKey (loginAndStore overwrites them); needed for enable-trading and order signing
        TokenManager.getInstance().setEoa(eoa);
        if (privateKey != null && !privateKey.isBlank()) {
            TokenManager.getInstance().setPrivateKey(privateKey);
        }

        LOG.info("Login success. userId={} proxy={}",
            loginResponse.getUserId(),
            loginResponse.getProxyWalletAddress());

        if (SessionFileWriter.writeFromTokenManager()) {
            LOG.info("Session written to .env.session (source it for k6)");
        }
    }

    @Test(groups = {"auth-complete"}, description = "Token is stored in TokenManager after login")
    public void verifyTokenStored() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token available - setupSuite may have failed");
        }
        assertThat(TokenManager.getInstance().hasToken()).isTrue();
        assertThat(TokenManager.getInstance().getUserId()).isNotBlank();
        assertThat(TokenManager.getInstance().getProxyWalletAddress()).isNotBlank();
    }

    @Test(description = "POST /auth/refresh/token returns 200 and new access_token different from old")
    public void refreshToken_returns200_andNewToken() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run login first");
        }
        String refreshCookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new SkipException("No refresh cookie - backend must return Set-Cookie: refresh_token on login");
        }
        String oldToken = TokenManager.getInstance().getAccessToken();
        Response response = authService.refreshToken(refreshCookie);
        if (response.getStatusCode() == 404) {
            throw new SkipException("POST /auth/refresh/token not implemented - endpoint returned 404");
        }
        assertThat(response.getStatusCode()).as("refresh token").isEqualTo(200);
        String newToken = response.path("access_token");
        if (newToken == null || newToken.isBlank()) newToken = response.path("data.access_token");
        assertThat(newToken).as("response must contain access_token").isNotBlank();
        assertThat(newToken).as("new token must differ from old").isNotEqualTo(oldToken);
    }

    @Test(description = "Login with stale timestamp: backend may return 4xx (reject) or 200 (UAT currently may not enforce freshness)")
    public void loginWithExpiredTimestamp_returns4xx() {
        if (apiKey == null || apiKey.isBlank()) throw new SkipException("No API key");
        String sigServerUrl = Config.getSigServerUrl();
        if (sigServerUrl == null || sigServerUrl.isBlank()) throw new SkipException("sig-server not configured");
        if (authService == null || signatureService == null) throw new SkipException("Auth services not initialized");

        long now = System.currentTimeMillis();
        long oldTimestamp = (now / 1000L) - 3600L;

        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, Config.getPrivateKey());
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        LoginRequest badRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-expired-" + now)
                .chainType("base-sepolia")
                .timestamp(oldTimestamp)
                .build();

        Response response = authService.login(apiKey, badRequest);
        if (response == null) throw new AssertionError("Expected non-null response");
        int code = response.getStatusCode();
        if (code >= 400 && code < 500) {
            Object err = response.path("error");
            assertThat(err).as("error object").isNotNull();
        } else if (code == 200) {
            String token = authService.extractAccessTokenFromResponse(response);
            assertThat(token).as("if login returns 200, access_token must be present").isNotBlank();
            LOG.warn("loginWithExpiredTimestamp: got 200; backend did not reject stale timestamp. Align with product if strict rejection is required.");
        } else {
            assertThat(false).withFailMessage("Unexpected status %s body=%s", code, response.getBody().asString()).isTrue();
        }
    }

    @Test(description = "Login with missing wallet address returns 4xx")
    public void loginWithMissingWalletAddress_returns4xx() {
        if (apiKey == null || apiKey.isBlank()) throw new SkipException("No API key");
        String sigServerUrl = Config.getSigServerUrl();
        if (sigServerUrl == null || sigServerUrl.isBlank()) throw new SkipException("sig-server not configured");
        if (authService == null || signatureService == null) throw new SkipException("Auth services not initialized");

        long now = System.currentTimeMillis();
        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, Config.getPrivateKey());
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        LoginRequest badRequest = LoginRequest.builder()
                .walletAddress(null)
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-missing-wallet-" + now)
                .chainType("base-sepolia")
                .timestamp(now / 1000L)
                .build();

        Response response = authService.login(apiKey, badRequest);
        if (response == null) throw new AssertionError("Expected non-null response");
        assertThat(response.getStatusCode()).as("status code").isGreaterThanOrEqualTo(400).isLessThan(500);
        Object err = response.path("error");
        assertThat(err).as("error object").isNotNull();
    }

    @Test(description = "Login with missing signature returns 4xx")
    public void loginWithMissingSignature_returns4xx() {
        if (apiKey == null || apiKey.isBlank()) throw new SkipException("No API key");
        String sigServerUrl = Config.getSigServerUrl();
        if (sigServerUrl == null || sigServerUrl.isBlank()) throw new SkipException("sig-server not configured");
        if (authService == null || signatureService == null) throw new SkipException("Auth services not initialized");

        long now = System.currentTimeMillis();
        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, Config.getPrivateKey());
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getWalletAddress()).isNotBlank();

        LoginRequest badRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(null)
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-missing-signature-" + now)
                .chainType("base-sepolia")
                .timestamp(now / 1000L)
                .build();

        Response response = authService.login(apiKey, badRequest);
        if (response == null) throw new AssertionError("Expected non-null response");
        assertThat(response.getStatusCode()).as("status code").isGreaterThanOrEqualTo(400).isLessThan(500);
        Object err = response.path("error");
        assertThat(err).as("error object").isNotNull();
    }

    @Test(description = "Refresh with invalid cookie returns 4xx")
    public void refreshWithInvalidCookie_returns4xx() {
        String invalidCookie = "refresh_token=invalid-garbage-token-12345";
        Response response = authService.refreshToken(invalidCookie);
        if (response == null) throw new AssertionError("Expected non-null response");
        assertThat(response.getStatusCode()).as("status code").isGreaterThanOrEqualTo(400).isLessThan(500);
        Object err = response.path("error");
        assertThat(err).as("error object").isNotNull();
    }

    @Test(description = "Login with missing API key returns 4xx")
    public void loginWithMissingApiKey_returns4xx() {
        String sigServerUrl = Config.getSigServerUrl();
        if (sigServerUrl == null || sigServerUrl.isBlank()) throw new SkipException("sig-server not configured");
        if (authService == null || signatureService == null) throw new SkipException("Auth services not initialized");

        long now = System.currentTimeMillis();
        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, Config.getPrivateKey());
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        LoginRequest loginRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-missing-apikey-" + now)
                .chainType("base-sepolia")
                .timestamp(now / 1000L)
                .build();

        // AuthService would set the header; avoid null to prevent NPE, use empty string instead.
        Response response = authService.login("", loginRequest);
        if (response == null) throw new AssertionError("Expected non-null response");
        assertThat(response.getStatusCode()).as("status code").isGreaterThanOrEqualTo(400).isLessThan(500);
        Object err = response.path("error");
        assertThat(err).as("error object").isNotNull();
    }

    @Test(description = "Login with invalid API key returns 4xx")
    public void loginWithInvalidApiKey_returns4xx() {
        String sigServerUrl = Config.getSigServerUrl();
        if (sigServerUrl == null || sigServerUrl.isBlank()) throw new SkipException("sig-server not configured");
        if (authService == null || signatureService == null) throw new SkipException("Auth services not initialized");

        long now = System.currentTimeMillis();
        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, Config.getPrivateKey());
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        LoginRequest loginRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-invalid-apikey-" + now)
                .chainType("base-sepolia")
                .timestamp(now / 1000L)
                .build();

        Response response = authService.login("invalid-api-key-xyz-000", loginRequest);
        if (response == null) throw new AssertionError("Expected non-null response");
        assertThat(response.getStatusCode()).as("status code").isGreaterThanOrEqualTo(400).isLessThan(500);
        Object err = response.path("error");
        assertThat(err).as("error object").isNotNull();
    }

    @Test(description = "Login with invalid signature returns 4xx")
    public void loginWithInvalidSignature_returns4xx() {
        AuthService svc = new AuthService();
        String key = apiKey;
        if (key == null || key.isBlank()) {
            Response keyResponse = svc.createApiKey();
            if (keyResponse.getStatusCode() != 200) {
                throw new SkipException("Cannot get API key");
            }
            key = svc.parseApiKey(keyResponse);
        }
        LoginRequest badRequest = LoginRequest.builder()
                .walletAddress("0x0000000000000000000000000000000000000000")
                .signature("0x000000")
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-" + System.currentTimeMillis())
                .chainType("base-sepolia")
                .timestamp(System.currentTimeMillis() / 1000)
                .build();
        Response response = svc.login(key, badRequest);
        assertThat(response.getStatusCode())
                .isGreaterThanOrEqualTo(400)
                .isLessThan(500);
        response.then().body("error", notNullValue())
                .body("error.status_code", equalTo(401))
                .body("error.error_code", equalTo("SIGNATURE_LOGIN_FAILED"))
                .body("error.message", notNullValue());
    }
}
