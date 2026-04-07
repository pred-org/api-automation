package com.pred.apitests.util;

import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.PortfolioService;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Runtime guards for tests that depend on market state.
 * Use @BeforeMethod with these so tests skip cleanly with a readable message
 * instead of failing with a cryptic assertion error.
 */
public final class TestPreConditions {

    private static final OrderService ORDER_SERVICE = new OrderService();
    private static final PortfolioService PORTFOLIO_SERVICE = new PortfolioService();

    private TestPreConditions() {}

    /**
     * Returns true if orderbook has at least one bid with quantity > 0.
     * Pre-condition for SHORT market order tests.
     */
    public static boolean hasBidLiquidity(String parentMarketId, String subMarketId) {
        if (parentMarketId == null || parentMarketId.isBlank()) return false;
        if (subMarketId == null || subMarketId.isBlank()) return false;
        Response res = ORDER_SERVICE.getOrderbook(parentMarketId, subMarketId);
        if (res.getStatusCode() != 200) return false;
        List<?> bids = res.path("bids");
        if (bids == null || bids.isEmpty()) return false;
        Object first = bids.get(0);
        if (!(first instanceof java.util.Map)) return false;
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> bid = (java.util.Map<String, Object>) first;
        Object q = bid.get("quantity");
        if (q == null) return false;
        try {
            long qty = (long) Double.parseDouble(String.valueOf(q).trim());
            return qty > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns true if user has at least one open position.
     */
    public static boolean hasOpenPosition(String accessToken, String refreshCookie) {
        if (accessToken == null || accessToken.isBlank()) return false;
        Response res = PORTFOLIO_SERVICE.getPositions(accessToken, refreshCookie);
        if (res.getStatusCode() != 200) return false;
        List<?> positions = res.path("positions");
        return positions != null && !positions.isEmpty();
    }

    /**
     * Returns true if usdc_balance >= minimum.
     */
    public static boolean isBalanceSufficient(String accessToken, String refreshCookie, BigDecimal minimum) {
        if (accessToken == null || accessToken.isBlank()) return false;
        if (minimum == null) return true;
        Response res = PORTFOLIO_SERVICE.getBalance(accessToken, refreshCookie);
        if (res.getStatusCode() != 200) return false;
        Object usdc = res.path("usdc_balance");
        if (usdc == null || String.valueOf(usdc).isBlank()) return false;
        try {
            BigDecimal balance = new BigDecimal(String.valueOf(usdc).trim());
            return balance.compareTo(minimum) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
