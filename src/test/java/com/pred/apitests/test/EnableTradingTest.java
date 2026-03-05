package com.pred.apitests.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.response.PrepareResponse;
import com.pred.apitests.service.EnableTradingService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnableTradingTest extends BaseApiTest {

    private static final Logger LOG = LoggerFactory.getLogger(EnableTradingTest.class);
    private static final int PREPARE_RETRY_MAX = 3;
    private static final int PREPARE_RETRY_DELAY_MS = 4000;
    private static final String NO_CONTRACT_CODE = "no contract code at given address";

    private EnableTradingService enableTradingService;
    private SignatureService signatureService;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        enableTradingService = new EnableTradingService();
        signatureService = new SignatureService();
    }

    @Test(description = "Prepare then sign transactionHash then execute - enable trading for proxy")
    public void enableTrading_success() {
        String accessToken = TokenManager.getInstance().getAccessToken();
        String refreshCookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
        String proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        if (proxyWallet == null || proxyWallet.isBlank()) {
            throw new SkipException("Proxy wallet not in TokenManager");
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
                    throw new SkipException("Trading already enabled - skipping");
                }
                if (prepareBody != null && prepareBody.contains(NO_CONTRACT_CODE) && attempt < PREPARE_RETRY_MAX) {
                    LOG.info("Prepare 500 (no contract code) - retry {}/{} after {}ms for new-user Safe deployment", attempt, PREPARE_RETRY_MAX, PREPARE_RETRY_DELAY_MS);
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

        // Use same wallet as login (TokenManager or config) so signature is from Safe owner
        String privateKey = TokenManager.getInstance().getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            privateKey = Config.getPrivateKey();
        }
        var sigResponse = signatureService.signSafeApproval(Config.getSigServerUrl(), txHash, privateKey);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        // Unwrap one level: pass data.data to execute (API wraps prepare response in data.data)
        JsonNode prepareData = getPrepareDataForExecute(prepareResponse);

        Response executeResponse = enableTradingService.execute(accessToken, refreshCookie, prepareData, sigResponse.getSignature());
        int status = executeResponse.getStatusCode();
        if (status == 500) {
            String body = executeResponse.getBody().asString();
            if (body != null && body.contains("trading is already enabled")) {
                throw new SkipException("Trading already enabled - skipping");
            }
        }
        assertThat(status).as("Enable trading execute (report 500/transaction failed to dev team)").isEqualTo(200);

        LOG.info("Enable trading complete for proxy: {}", proxyWallet);
    }

    /** Try common paths for transactionHash (API may use data.transactionHash or data.data.transactionHash). */
    private static String getTransactionHashFromPrepare(Response prepareResponse) {
        String[] paths = { "data.transactionHash", "data.data.transactionHash", "transactionHash" };
        for (String path : paths) {
            String v = prepareResponse.jsonPath().getString(path);
            if (v != null && !v.isBlank()) return v.trim();
        }
        PrepareResponse parsed = prepareResponse.as(PrepareResponse.class);
        return parsed != null ? parsed.getTransactionHash() : null;
    }

    /** Extract data.data from prepare response for execute body; fallback to data if no nested data. */
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
