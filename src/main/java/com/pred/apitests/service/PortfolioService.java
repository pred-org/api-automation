package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.UUID;

/**
 * Portfolio: positions and balance (public and internal).
 */
public class PortfolioService extends BaseService {

    private static final String POSITIONS_PATH = "/api/v1/portfolio/positions";
    private static final String BALANCE_PATH = "/api/v1/portfolio/balance";
    private static final String BALANCE_BY_MARKET_PATH = "/api/v1/portfolio/balance?parent_market_id=%s&market_id=%s";
    private static final String INTERNAL_BALANCE_PATH = "/api/v1/balance/info";

    /**
     * GET {publicBase}/api/v1/portfolio/positions
     */
    public Response getPositions(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(POSITIONS_PATH);
    }

    /**
     * GET {publicBase}/api/v1/portfolio/balance
     */
    public Response getBalance(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(BALANCE_PATH);
    }

    /**
     * GET {publicBase}/api/v1/portfolio/balance?parent_market_id={marketId}&market_id={marketId}
     */
    public Response getBalanceByMarket(String accessToken, String refreshCookie, String marketId) {
        String path = String.format(BALANCE_BY_MARKET_PATH, marketId, marketId);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(path);
    }

    /**
     * GET {internalBase}/api/v1/balance/info
     * Headers: X-User-ID, X-Trace-ID (random UUID).
     */
    public Response getInternalBalance(String userId) {
        String base = getInternalBaseUri();
        RequestSpecification spec = given(base)
                .header("X-User-ID", userId)
                .header("X-Trace-ID", UUID.randomUUID().toString());
        return spec.when().get(INTERNAL_BALANCE_PATH);
    }
}
