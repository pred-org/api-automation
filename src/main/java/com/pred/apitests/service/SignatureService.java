package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.model.response.SignSafeApprovalResponse;
import io.restassured.response.Response;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls the Node sig-server over HTTP for login (CreateProxy) and order signatures.
 */
public class SignatureService extends BaseService {

    private static final String SIGN_CREATE_PROXY_PATH = "/sign-create-proxy";
    private static final String SIGN_ORDER_PATH = "/sign-order";
    private static final String SIGN_SAFE_APPROVAL_PATH = "/sign-safe-approval";

    /**
     * POST {sigServerUrl}/sign-create-proxy
     * Body: {@code privateKey} and/or {@code signing_id} (wallet registered via POST /wallets on sig-server).
     * Omit both to let sig-server allocate a new in-process wallet (returns {@code signing_id}).
     */
    public SignCreateProxyResponse signCreateProxy(String sigServerUrl, String privateKey) {
        return signCreateProxy(sigServerUrl, privateKey, null);
    }

    public SignCreateProxyResponse signCreateProxy(String sigServerUrl, String privateKey, String signingId) {
        Map<String, Object> body = new HashMap<>();
        if (signingId != null && !signingId.isBlank()) {
            body.put("signing_id", signingId);
        }
        if (privateKey != null && !privateKey.isBlank()) {
            body.put("privateKey", privateKey);
        }
        Response response = post(sigServerUrl, SIGN_CREATE_PROXY_PATH, body.isEmpty() ? Collections.emptyMap() : body);
        return response.as(SignCreateProxyResponse.class);
    }

    /**
     * POST {sigServerUrl}/sign-order
     * Body: SignOrderRequest as JSON.
     */
    public SignOrderResponse signOrder(String sigServerUrl, SignOrderRequest request) {
        Response response = post(sigServerUrl, SIGN_ORDER_PATH, request);
        return response.as(SignOrderResponse.class);
    }

    /**
     * POST {sigServerUrl}/sign-safe-approval
     * Body: { "transactionHash": "<transactionHash>", "usePersonalSign": false, "privateKey": "<optional>" }
     * Returns signature for enable-trading execute (raw hash signing, NOT personal_sign).
     * Pass privateKey of the wallet that owns the Safe (same as login EOA) so the backend accepts the signature.
     */
    public SignSafeApprovalResponse signSafeApproval(String sigServerUrl, String transactionHash) {
        return signSafeApproval(sigServerUrl, transactionHash, null);
    }

    public SignSafeApprovalResponse signSafeApproval(String sigServerUrl, String transactionHash, String privateKey) {
        return signSafeApproval(sigServerUrl, transactionHash, privateKey, null);
    }

    /**
     * Same as {@link #signSafeApproval(String, String, String)} but uses a wallet registered on sig-server ({@code signing_id}).
     */
    public SignSafeApprovalResponse signSafeApproval(String sigServerUrl, String transactionHash, String privateKey, String signingId) {
        Map<String, Object> body = new HashMap<>();
        body.put("transactionHash", transactionHash);
        body.put("usePersonalSign", false);
        if (signingId != null && !signingId.isBlank()) {
            body.put("signing_id", signingId);
        }
        if (privateKey != null && !privateKey.isBlank()) {
            body.put("privateKey", privateKey);
        }
        Response response = post(sigServerUrl, SIGN_SAFE_APPROVAL_PATH, body);
        return response.as(SignSafeApprovalResponse.class);
    }
}
