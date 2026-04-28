package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import com.pred.apitests.model.request.DepositRequest;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two-step deposit: (1) internal deposit returns transaction_hash, (2) internal cashflow confirms with that hash.
 */
public class DepositService extends BaseService {

    /** Internal: POST with ?skip_updating_bs=true to get transaction_hash. */
    private static final String INTERNAL_DEPOSIT_PATH = "/api/v1/competitions/internal/deposit?skip_updating_bs=true";
    /** Internal: POST array body [{user_id, transaction_hash}] to confirm deposit. */
    private static final String CASHFLOW_INTERNAL_PATH = "/api/v1/cashflow/internal";

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
     * Step 2: POST {internalBase}/api/v1/cashflow/internal
     * Headers: Content-Type: application/json
     * Body: [ { "user_id": "&lt;userId&gt;", "transaction_hash": "&lt;from step 1&gt;" } ]
     */
    public Response cashflowDeposit(String userId, String transactionHash) {
        Map<String, String> item = new HashMap<>();
        item.put("user_id", userId);
        item.put("transaction_hash", transactionHash);
        List<Map<String, String>> body = new ArrayList<>();
        body.add(item);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return post(getInternalBaseUri(), CASHFLOW_INTERNAL_PATH, body, headers);
    }

    /**
     * Legacy: single internal deposit only (no cashflow step). Prefer internalDeposit + cashflowDeposit for full flow.
     */
    public Response deposit(String userId, long amount) {
        return internalDeposit(userId, amount);
    }
}
