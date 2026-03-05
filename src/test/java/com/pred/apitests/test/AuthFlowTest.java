package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test(description = "Print access token for Postman (copy from output)")
    public void printAccessTokenForPostman() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run setupSuite first");
        }
        System.out.println("ACCESS_TOKEN_FOR_POSTMAN: " + TokenManager.getInstance().getAccessToken());
    }

    @Test(description = "Token is stored in TokenManager after login")
    public void verifyTokenStored() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token available - setupSuite may have failed");
        }
        assertThat(TokenManager.getInstance().hasToken()).isTrue();
        assertThat(TokenManager.getInstance().getUserId()).isNotBlank();
        assertThat(TokenManager.getInstance().getProxyWalletAddress()).isNotBlank();
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
    }
}
