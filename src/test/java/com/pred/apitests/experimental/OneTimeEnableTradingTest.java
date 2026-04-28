package com.pred.apitests.experimental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.LoginResponse;
import com.pred.apitests.model.response.PrepareResponse;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.EnableTradingService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OneTimeEnableTradingTest extends BaseApiTest {

    private static final int PREPARE_RETRY_MAX = 3;
    private static final int PREPARE_RETRY_DELAY_MS = 4000;
    private static final String NO_CONTRACT_CODE = "no contract code at given address";

    private AuthService authService;
    private SignatureService signatureService;
    private EnableTradingService enableTradingService;

    @BeforeClass
    public void init() {
        authService = new AuthService();
        signatureService = new SignatureService();
        enableTradingService = new EnableTradingService();
    }

    @Test(description = "One-time flow: login with signature and enable trading only")
    public void loginAndEnableTrading_oneTime() {
        String apiKey = firstNonBlank(System.getenv("ONE_TIME_API_KEY"), Config.getApiKey());
        String privateKey = firstNonBlank(System.getenv("ONE_TIME_PRIVATE_KEY"), Config.getPrivateKey());
        String sigServerUrl = Config.getSigServerUrl();

        if (apiKey == null || apiKey.isBlank()) throw new SkipException("ONE_TIME_API_KEY (or API_KEY) is required");
        if (privateKey == null || privateKey.isBlank()) throw new SkipException("ONE_TIME_PRIVATE_KEY (or PRIVATE_KEY) is required");
        if (sigServerUrl == null || sigServerUrl.isBlank()) throw new SkipException("SIG_SERVER_URL is required");

        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, privateKey);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();
        assertThat(sigResponse.getWalletAddress()).isNotBlank();

        long now = System.currentTimeMillis();
        LoginRequest loginRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-one-time-" + now + "-" + (now / 1000))
                .chainType("base-sepolia")
                .timestamp(now / 1000)
                .build();

        Response loginResponseRaw = authService.login(apiKey, loginRequest);
        assertThat(loginResponseRaw).isNotNull();
        assertThat(loginResponseRaw.getStatusCode()).as("login status").isEqualTo(200);

        LoginResponse loginResponse = authService.loginAndStoreFromResponse(loginResponseRaw);
        assertThat(loginResponse).as("parsed login response").isNotNull();
        assertThat(loginResponse.getAccessToken()).isNotBlank();
        assertThat(loginResponse.getProxyWalletAddress()).isNotBlank();

        TokenManager.getInstance().setApiKey(apiKey);
        TokenManager.getInstance().setPrivateKey(privateKey);
        TokenManager.getInstance().setEoa(sigResponse.getWalletAddress());

        String accessToken = TokenManager.getInstance().getAccessToken();
        String refreshCookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
        String proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        assertThat(accessToken).isNotBlank();
        assertThat(proxyWallet).isNotBlank();

        Response prepareResponse = null;
        int prepareStatus = -1;
        for (int attempt = 1; attempt <= PREPARE_RETRY_MAX; attempt++) {
            prepareResponse = enableTradingService.prepare(accessToken, refreshCookie, proxyWallet);
            prepareStatus = prepareResponse.getStatusCode();

            if (prepareStatus == 500) {
                String body = prepareResponse.getBody().asString();
                if (body != null && body.contains("trading is already enabled")) {
                    throw new SkipException("Trading already enabled for this user");
                }
                if (body != null && body.contains(NO_CONTRACT_CODE) && attempt < PREPARE_RETRY_MAX) {
                    try {
                        Thread.sleep(PREPARE_RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Prepare retry interrupted", e);
                    }
                    continue;
                }
            }
            if (prepareStatus == 200) break;
        }
        assertThat(prepareStatus).as("prepare status").isEqualTo(200);

        String txHash = getTransactionHashFromPrepare(prepareResponse);
        assertThat(txHash).isNotBlank();

        var safeSigResponse = signatureService.signSafeApproval(sigServerUrl, txHash, privateKey);
        assertThat(safeSigResponse).isNotNull();
        assertThat(safeSigResponse.isOk()).isTrue();
        assertThat(safeSigResponse.getSignature()).isNotBlank();

        JsonNode prepareData = getPrepareDataForExecute(prepareResponse);
        Response executeResponse = enableTradingService.execute(accessToken, refreshCookie, prepareData, safeSigResponse.getSignature());
        int executeStatus = executeResponse.getStatusCode();
        if (executeStatus == 500) {
            String body = executeResponse.getBody().asString();
            if (body != null && body.contains("trading is already enabled")) {
                throw new SkipException("Trading already enabled for this user");
            }
        }
        assertThat(executeStatus).as("execute status").isEqualTo(200);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }

    private static String getTransactionHashFromPrepare(Response prepareResponse) {
        String[] paths = {"data.transactionHash", "data.data.transactionHash", "transactionHash"};
        for (String path : paths) {
            String v = prepareResponse.jsonPath().getString(path);
            if (v != null && !v.isBlank()) return v.trim();
        }
        PrepareResponse parsed = prepareResponse.as(PrepareResponse.class);
        return parsed != null ? parsed.getTransactionHash() : null;
    }

    private static JsonNode getPrepareDataForExecute(Response prepareResponse) {
        try {
            JsonNode root = new ObjectMapper().readTree(prepareResponse.getBody().asString());
            JsonNode inner = root.path("data").path("data");
            if (!inner.isMissingNode()) return inner;
            JsonNode data = root.path("data");
            return data.isMissingNode() ? root : data;
        } catch (Exception e) {
            PrepareResponse parsed = prepareResponse.as(PrepareResponse.class);
            return parsed != null ? parsed.getData() : null;
        }
    }
}
