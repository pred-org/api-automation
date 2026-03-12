package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class PortfolioTest extends BaseApiTest {

    private PortfolioService portfolioService;
    private String token;
    private String cookie;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        portfolioService = new PortfolioService();
        token = TokenManager.getInstance().getAccessToken();
        cookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
    }

    @Test(description = "GET portfolio balance returns 200")
    public void getBalance_returns200() {
        Response response = portfolioService.getBalance(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(true))
                .body("usdc_balance", notNullValue())
                .body("position_balance", notNullValue());
        String usdcBalance = response.path("usdc_balance");
        assertThat(parseBalanceAsLong(usdcBalance)).isGreaterThan(0L);
    }

    @Test(description = "GET portfolio positions returns 200; when positions exist, validate entry has side, quantity, average_price, amount")
    public void getPositions_returns200() {
        Response response = portfolioService.getPositions(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(true))
                .body("total_invested", notNullValue())
                .body("total_to_win", notNullValue())
                .body("position_league_summary", notNullValue())
                .body("positions", notNullValue());
        java.util.List<?> positions = response.path("positions");
        if (positions != null && !positions.isEmpty()) {
            Object first = positions.get(0);
            if (first instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> pos = (java.util.Map<String, Object>) first;
                assertThat(pos).containsKey("side");
                assertThat(pos).containsKey("quantity");
                assertThat(pos).containsKey("average_price");
                assertThat(pos).containsKey("amount");
            }
        }
    }

    @Test(description = "GET balance by market returns 200; usdc_balance and position_balance are valid numbers (position_balance can be negative for short)")
    public void getBalanceByMarket_returns200() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) {
            throw new SkipException("MARKET_ID not set in config");
        }
        Response response = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(true))
                .body("usdc_balance", notNullValue())
                .body("position_balance", notNullValue());
        assertThat(parseBalanceAsLong(response.path("usdc_balance").toString())).isGreaterThan(0);
        String posBal = response.path("position_balance") != null ? response.path("position_balance").toString() : "0";
        parseBalanceAsLong(posBal);
    }

    @Test(description = "GET portfolio earnings returns 200; PnL fields present and total_pnl == realized_pnl + unrealized_pnl")
    public void getEarnings_returns200() {
        Response response = portfolioService.getEarnings(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("user_id", notNullValue())
                .body("realized_pnl", notNullValue())
                .body("unrealized_pnl", notNullValue())
                .body("total_pnl", notNullValue());
        String body = response.getBody().asString();
        assertThat(body).contains("user_id").contains("realized_pnl").contains("unrealized_pnl").contains("total_pnl");
        double realized = parsePnlAsDouble(response.path("realized_pnl") != null ? response.path("realized_pnl").toString() : "0");
        double unrealized = parsePnlAsDouble(response.path("unrealized_pnl") != null ? response.path("unrealized_pnl").toString() : "0");
        double total = parsePnlAsDouble(response.path("total_pnl") != null ? response.path("total_pnl").toString() : "0");
        assertThat(Math.abs(total - (realized + unrealized))).isLessThanOrEqualTo(0.01);
    }

    @Test(description = "Earnings API: total_pnl equals realized_pnl + unrealized_pnl (integrity check)")
    public void getEarnings_totalPnlEqualsRealizedPlusUnrealized() {
        Response response = portfolioService.getEarnings(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        double realized = parsePnlAsDouble(response.path("realized_pnl") != null ? response.path("realized_pnl").toString() : "0");
        double unrealized = parsePnlAsDouble(response.path("unrealized_pnl") != null ? response.path("unrealized_pnl").toString() : "0");
        double total = parsePnlAsDouble(response.path("total_pnl") != null ? response.path("total_pnl").toString() : "0");
        assertThat(Math.abs(total - (realized + unrealized))).isLessThanOrEqualTo(0.01);
    }

    @Test(description = "GET balance with invalid token returns 401")
    public void getBalance_withInvalidToken_returns401() {
        Response response = portfolioService.getBalance("invalid-token-abc", null);
        response.then().statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test(description = "GET positions with invalid token returns 401")
    public void getPositions_withInvalidToken_returns401() {
        Response response = portfolioService.getPositions("invalid-token-abc", null);
        response.then().statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test(description = "GET earnings with invalid token returns 401")
    public void getEarnings_withInvalidToken_returns401() {
        Response response = portfolioService.getEarnings("invalid-token-abc", null);
        response.then().statusCode(401)
                .body("message", equalTo("Unauthorized"));
    }

    @Test(description = "GET trade-history returns 200 and response has list structure; audit trail for orders/trades")
    public void getTradeHistory_returns200_andHasStructure() {
        Response response = portfolioService.getTradeHistory(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body(notNullValue());
        String body = response.getBody().asString();
        assertThat(body).isNotBlank();
        Object data = response.path("data");
        if (data != null && data instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) data;
            for (Object item : list) {
                if (item instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> trade = (java.util.Map<String, Object>) item;
                    assertThat(trade).containsKey("activity");
                    break;
                }
            }
        }
    }

    @Test(description = "Contract: global balance position_balance vs scoped by market; global may be 0 while scoped shows net (e.g. negative for short)")
    public void getBalance_globalVsScoped_positionBalanceContract() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) {
            throw new SkipException("MARKET_ID not set");
        }
        Response globalRes = portfolioService.getBalance(token, cookie);
        Response scopedRes = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(globalRes.getStatusCode()).isEqualTo(200);
        assertThat(scopedRes.getStatusCode()).isEqualTo(200);
        String globalPosBal = globalRes.path("position_balance") != null ? globalRes.path("position_balance").toString() : "0";
        String scopedPosBal = scopedRes.path("position_balance") != null ? scopedRes.path("position_balance").toString() : "0";
        parseBalanceAsLong(globalPosBal);
        parseBalanceAsLong(scopedPosBal);
        assertThat(parseBalanceAsLong(globalRes.path("usdc_balance").toString())).isGreaterThan(0);
        assertThat(parseBalanceAsLong(scopedRes.path("usdc_balance").toString())).isGreaterThan(0);
    }

    @Test(description = "Scoped trade-history count <= global; every scoped entry has market_id == requested market")
    public void getTradeHistory_filteredByMarketId_isSubsetOfGlobal() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");

        Response globalRes = portfolioService.getTradeHistory(token, cookie);
        Response scopedRes = portfolioService.getTradeHistoryByMarket(token, cookie, marketId);
        assertThat(globalRes.getStatusCode()).isEqualTo(200);
        assertThat(scopedRes.getStatusCode()).isEqualTo(200);

        java.util.List<?> globalList = globalRes.path("data");
        java.util.List<?> scopedList = scopedRes.path("data");
        int globalCount = globalList != null ? globalList.size() : 0;
        int scopedCount = scopedList != null ? scopedList.size() : 0;
        assertThat(scopedCount).isLessThanOrEqualTo(globalCount);

        if (scopedList != null) {
            for (Object item : scopedList) {
                if (item instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> entry = (java.util.Map<String, Object>) item;
                    Object mid = entry.get("market_id");
                    assertThat(mid).isNotNull();
                    assertThat(String.valueOf(mid).trim()).isEqualTo(marketId);
                }
            }
        }
    }

    @Test(description = "Contract: every trade-history entry has activity in [Open Long, Open Short, Redeemed]")
    public void getTradeHistory_activityTypes_areKnownValues() {
        Response response = portfolioService.getTradeHistory(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        java.util.List<?> data = response.path("data");
        if (data == null || data.isEmpty()) return;

        java.util.Set<String> known = java.util.Set.of("Open Long", "Open Short", "Redeemed");
        for (Object item : data) {
            if (item instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> entry = (java.util.Map<String, Object>) item;
                String activity = String.valueOf(entry.get("activity")).trim();
                assertThat(known.contains(activity))
                        .as("activity must be one of [Open Long, Open Short, Redeemed]; got: " + activity).isTrue();
            }
        }
    }

    @Test(description = "When positions exist: market_id non-empty, side long|short, quantity > 0, average_price 1-99, amount > 0")
    public void getPositions_fieldsAreValid_whenPositionsExist() {
        Response response = portfolioService.getPositions(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        java.util.List<?> positions = response.path("positions");
        if (positions == null || positions.isEmpty()) {
            throw new SkipException("No open positions to validate");
        }
        for (Object o : positions) {
            if (o instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> pos = (java.util.Map<String, Object>) o;
                String marketIdVal = String.valueOf(pos.get("market_id"));
                assertThat(marketIdVal).isNotBlank();
                String side = String.valueOf(pos.get("side")).trim();
                assertThat(side).isIn("long", "short");
                Object qtyObj = pos.get("quantity");
                assertThat(qtyObj).isNotNull();
                int quantity = (int) Double.parseDouble(String.valueOf(qtyObj).trim());
                assertThat(quantity).isGreaterThan(0);
                Object avgPriceObj = pos.get("average_price");
                assertThat(avgPriceObj).isNotNull();
                java.math.BigDecimal averagePrice = new java.math.BigDecimal(String.valueOf(avgPriceObj).trim());
                assertThat(averagePrice.compareTo(java.math.BigDecimal.ONE) >= 0
                        && averagePrice.compareTo(new java.math.BigDecimal("99")) <= 0).isTrue();
                Object amountObj = pos.get("amount");
                assertThat(amountObj).isNotNull();
                java.math.BigDecimal amount = new java.math.BigDecimal(String.valueOf(amountObj).trim());
                assertThat(amount.compareTo(java.math.BigDecimal.ZERO) > 0).isTrue();
            }
        }
    }
}
