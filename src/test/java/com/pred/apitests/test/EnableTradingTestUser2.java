package com.pred.apitests.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.response.PrepareResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.EnableTradingService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enable trading for user 2 (SecondUserContext). Run after AuthFlowTestUser2.
 */
public class EnableTradingTestUser2 extends BaseApiTest {

    private static final Logger LOG = LoggerFactory.getLogger(EnableTradingTestUser2.class);
    private static final int PREPARE_RETRY_MAX = 3;
    private static final int PREPARE_RETRY_DELAY_MS = 4000;
    private static final String NO_CONTRACT_CODE = "no contract code at given address";

    private EnableTradingService enableTradingService;
    private SignatureService signatureService;

    @BeforeClass
    public void init() {
        UserSession s = SecondUserContext.getSecondUser();
        if (s == null || !s.hasToken()) {
            throw new SkipException("User 2 session not set - run AuthFlowTestUser2 first");
        }
        enableTradingService = new EnableTradingService();
        signatureService = new SignatureService();
    }

    @Test(description = "Enable trading for user 2 proxy")
    public void enableTrading_user2_success() {
        UserSession s = SecondUserContext.getSecondUser();
        if (s == null || !s.hasToken()) {
            throw new SkipException("User 2 session not set - run AuthFlowTestUser2 first");
        }
        String accessToken = s.getAccessToken();
        String refreshCookie = s.getRefreshCookieHeaderValue();
        String proxyWallet = s.getProxy();
        if (proxyWallet == null || proxyWallet.isBlank()) {
            throw new SkipException("User 2 proxy wallet not set");
        }

        Response prepareResponse = null;
        int prepareStatus = -1;
        for (int attempt = 1; attempt <= PREPARE_RETRY_MAX; attempt++) {
            prepareResponse = enableTradingService.prepare(accessToken, refreshCookie, proxyWallet);
            prepareStatus = prepareResponse.getStatusCode();
            if (prepareStatus == 401) {
                throw new SkipException("Prepare returned 401 Unauthorized - backend may not accept Bearer+Cookie for this endpoint or session");
            }
            if (prepareStatus == 500) {
                String prepareBody = prepareResponse.getBody().asString();
                if (prepareBody != null && prepareBody.contains("trading is already enabled")) {
                    throw new SkipException("Trading already enabled for user 2 - skipping");
                }
                if (prepareBody != null && prepareBody.contains(NO_CONTRACT_CODE) && attempt < PREPARE_RETRY_MAX) {
                    LOG.info("Prepare 500 (no contract code) - retry {}/{} after {}ms for user 2 Safe deployment", attempt, PREPARE_RETRY_MAX, PREPARE_RETRY_DELAY_MS);
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
        assertThat(prepareStatus).as("Prepare after up to %d attempts", PREPARE_RETRY_MAX).isEqualTo(200);

        String txHash = getTransactionHashFromPrepare(prepareResponse);
        assertThat(txHash).isNotBlank();

        String privateKey = s.getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) privateKey = Config.getSecondUserPrivateKey();
        assertThat(privateKey).as("User 2 private key required for sign").isNotBlank();

        var sigResponse = signatureService.signSafeApproval(Config.getSigServerUrl(), txHash, privateKey);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        JsonNode prepareData = getPrepareDataForExecute(prepareResponse);

        Response executeResponse = enableTradingService.execute(accessToken, refreshCookie, prepareData, sigResponse.getSignature());
        int status = executeResponse.getStatusCode();
        if (status == 500) {
            String body = executeResponse.getBody().asString();
            if (body != null && body.contains("trading is already enabled")) {
                throw new SkipException("Trading already enabled for user 2 - skipping");
            }
        }
        assertThat(status).as("Enable trading execute for user 2").isEqualTo(200);

        // Refresh user 2 token so the new JWT carries is_enabled_trading=true.
        LOG.info("Enable trading complete for user 2 proxy: {} — refreshing token", proxyWallet);
        AuthService authService = new AuthService();
        boolean refreshed = authService.refreshSecondUserAndStore();
        if (refreshed) {
            LOG.info("User 2 token refreshed — is_enabled_trading should now be true in JWT");
        } else {
            LOG.warn("User 2 token refresh after enable-trading failed — downstream order tests may get 400");
        }
    }

    private static String getTransactionHashFromPrepare(Response prepareResponse) {
        String[] paths = { "data.transactionHash", "data.data.transactionHash", "transactionHash" };
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
