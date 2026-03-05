package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.Map;

/**
 * Enable trading: prepare then execute (EIP-1193 sign of transactionHash done externally).
 */
public class EnableTradingService extends BaseService {

    private static final String PREPARE_PATH = "/api/v1/user/safe-approval/prepare";
    private static final String EXECUTE_PATH = "/api/v1/user/safe-approval/execute";

    /**
     * POST {publicBase}/api/v1/user/safe-approval/prepare
     * Body: { "proxy_wallet_address": "<proxyWalletAddress>" }
     */
    public Response prepare(String accessToken, String refreshCookie, String proxyWalletAddress) {
        Map<String, String> body = Map.of("proxy_wallet_address", proxyWalletAddress);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.body(body).when().post(PREPARE_PATH);
    }

    /**
     * POST {publicBase}/api/v1/user/safe-approval/execute
     * Body: { "data": <prepareData>, "signature": "<signature>" }
     */
    public Response execute(String accessToken, String refreshCookie, Object prepareData, String signature) {
        Map<String, Object> body = new HashMap<>();
        body.put("data", prepareData);
        body.put("signature", signature);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.body(body).when().post(EXECUTE_PATH);
    }
}
