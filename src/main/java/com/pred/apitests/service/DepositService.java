package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.CashflowDepositRequest;
import com.pred.apitests.model.request.DepositRequest;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.Map;

/**
 * Two-step deposit: (1) internal deposit returns transaction_hash, (2) public cashflow/deposit confirms with that hash.
 */
public class DepositService extends BaseService {

    /** Internal: POST with ?skip_updating_bs=true to get transaction_hash. */
    private static final String INTERNAL_DEPOSIT_PATH = "/api/v1/competitions/internal/deposit?skip_updating_bs=true";
    /** Public: POST with Bearer + X-Proxy-Address, body salt + transaction_hash + timestamp. */
    private static final String CASHFLOW_DEPOSIT_PATH = "/api/v1/cashflow/deposit";

    /**
     * Step 1: POST {internalBase}/api/v1/competitions/internal/deposit?skip_updating_bs=true
     * Body: { "user_id": "<userId>", "amount": <amount> }
     * Response contains transaction_hash for step 2.
     */
    public Response internalDeposit(String userId, long amount) {
        String base = getInternalBaseUri();
        DepositRequest body = DepositRequest.builder().userId(userId).amount(amount).build();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        String token = System.getenv("INTERNAL_DEPOSIT_TOKEN");
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return post(base, INTERNAL_DEPOSIT_PATH, body, headers);
    }

    /**
     * Extract transaction_hash from internal deposit response (tries common paths).
     */
    public String extractTransactionHashFromInternalDeposit(Response response) {
        if (response == null || response.getStatusCode() < 200 || response.getStatusCode() >= 300) return null;
        try {
            String[] paths = { "transaction_hash", "data.transaction_hash", "data.data.transaction_hash" };
            for (String path : paths) {
                String v = response.jsonPath().getString(path);
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (Exception ignored) { }
        return null;
    }

    /**
     * Step 2: POST {publicBase}/api/v1/cashflow/deposit
     * Headers: Authorization: Bearer, X-Proxy-Address, X-Wallet-Address (EOA)
     * Body: { "salt": &lt;long&gt;, "transaction_hash": "&lt;from step 1&gt;", "timestamp": &lt;unix&gt; }
     */
    public Response cashflowDeposit(String accessToken, String proxyAddress, String transactionHash, long salt, long timestamp) {
        CashflowDepositRequest body = CashflowDepositRequest.builder()
                .salt(salt)
                .transactionHash(transactionHash)
                .timestamp(timestamp)
                .build();
        String eoa = TokenManager.getInstance().getEoa();
        if (eoa == null || eoa.isBlank()) {
            eoa = Config.getEoaAddress();
        }
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, null)
                .header("X-Proxy-Address", proxyAddress);
        if (eoa != null && !eoa.isBlank()) {
            spec = spec.header("X-Wallet-Address", eoa);
        }
        return spec.body(body).when().post(CASHFLOW_DEPOSIT_PATH).then().extract().response();
    }

    /**
     * Legacy: single internal deposit only (no cashflow step). Prefer internalDeposit + cashflowDeposit for full flow.
     */
    public Response deposit(String userId, long amount) {
        return internalDeposit(userId, amount);
    }
}
