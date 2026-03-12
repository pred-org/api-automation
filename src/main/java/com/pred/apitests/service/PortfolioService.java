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
    private static final String POSITIONS_BY_MARKET_PATH = "/api/v1/portfolio/positions?market_id=%s";
    private static final String BALANCE_PATH = "/api/v1/portfolio/balance";
    private static final String BALANCE_BY_MARKET_PATH = "/api/v1/portfolio/balance?parent_market_id=%s&market_id=%s";
    private static final String OPEN_ORDERS_PATH = "/api/v1/portfolio/open-orders";
    private static final String TRADE_HISTORY_PATH = "/api/v1/portfolio/trade-history";
    private static final String TRADE_HISTORY_BY_MARKET_PATH = "/api/v1/portfolio/trade-history?market_id=%s";
    private static final String PNL_PATH = "/api/v1/portfolio/pnl";
    private static final String EARNINGS_PATH = "/api/v1/portfolio/earnings";
    private static final String INTERNAL_BALANCE_PATH = "/api/v1/balance/info";

    /**
     * GET {publicBase}/api/v1/portfolio/positions
     */
    public Response getPositions(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(POSITIONS_PATH);
    }

    /**
     * GET {publicBase}/api/v1/portfolio/positions?market_id={marketId}
     */
    public Response getPositionsByMarket(String accessToken, String refreshCookie, String marketId) {
        String path = String.format(POSITIONS_BY_MARKET_PATH, marketId);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(path);
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
     * GET {publicBase}/api/v1/portfolio/open-orders
     * Returns pending limit orders.
     */
    public Response getOpenOrders(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(OPEN_ORDERS_PATH);
    }

    /**
     * GET {publicBase}/api/v1/portfolio/trade-history
     * Returns all trades (activity: Open Long | Open Short | Redeemed; side, price, quantity, amount, pnl, matched_at).
     */
    public Response getTradeHistory(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(TRADE_HISTORY_PATH);
    }

    /**
     * GET {publicBase}/api/v1/portfolio/trade-history?market_id={marketId}
     * Returns trades scoped to one market.
     */
    public Response getTradeHistoryByMarket(String accessToken, String refreshCookie, String marketId) {
        String path = String.format(TRADE_HISTORY_BY_MARKET_PATH, marketId);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(path);
    }

    /**
     * GET {publicBase}/api/v1/portfolio/pnl
     * (Backend may expect GET; POST returned 404 on UAT.)
     */
    public Response getPnl(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(PNL_PATH);
    }

    /**
     * GET {publicBase}/api/v1/portfolio/earnings
     * Response: user_id, realized_pnl (PnL when position closed), unrealized_pnl (open position PnL from mark price), total_pnl.
     */
    public Response getEarnings(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(EARNINGS_PATH);
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
