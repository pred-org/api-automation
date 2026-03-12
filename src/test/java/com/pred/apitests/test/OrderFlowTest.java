package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.TestPreConditions;
import com.pred.apitests.util.TokenManager;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Multi-step integration flows: place/cancel sequences, two-user matching, position and PnL assertions.
 */
public class OrderFlowTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String ORDER_AMOUNT = "30";
    private static final int TWO_USER_MATCH_QUANTITY = 10;

    private OrderService orderService;
    private SignatureService signatureService;
    private PortfolioService portfolioService;
    private String eoa;
    private String proxyWallet;
    private String userId;
    private String marketId;
    private String tokenId;

    private String token() {
        return TokenManager.getInstance().getAccessToken();
    }
    private String cookie() {
        return TokenManager.getInstance().getRefreshCookieHeaderValue();
    }

    @BeforeClass
    public void initOrderFlowTest() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        orderService = new OrderService();
        signatureService = new SignatureService();
        portfolioService = new PortfolioService();
        eoa = TokenManager.getInstance().getEoa();
        if (eoa == null || eoa.isBlank()) eoa = Config.getEoaAddress();
        proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        userId = TokenManager.getInstance().getUserId();
        marketId = Config.getMarketId();
        tokenId = Config.getTokenId();
    }

    @Test(description = "Flow: place 2 orders (2nd with different signature), cancel 2nd; check balance, earnings, positions in between")
    public void flow_placeTwoOrders_cancelSecond_withBalancePnlPositionsChecks() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        Response balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        balanceRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());
        long balanceBefore = parseBalanceAsLong(balanceRes.path("usdc_balance").toString());
        Response earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        earningsRes.then().body("user_id", notNullValue()).body("realized_pnl", notNullValue()).body("unrealized_pnl", notNullValue()).body("total_pnl", notNullValue());
        Response positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());

        String orderId1 = placeOrderAndReturnOrderId(marketId, tokenId);
        assertThat(orderId1).as("place order 1 should return order_id in response").isNotBlank();

        balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        balanceRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue());
        long balanceAfterOrder1 = parseBalanceAsLong(balanceRes.path("usdc_balance").toString());
        assertThat(balanceAfterOrder1).isLessThanOrEqualTo(balanceBefore);
        earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());

        String orderId2 = placeOrderAndReturnOrderId(marketId, tokenId);
        assertThat(orderId2).as("place order 2 should return order_id in response").isNotBlank();
        assertThat(orderId2).as("order 2 id should differ from order 1").isNotEqualTo(orderId1);

        balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());

        Response cancelResponse = orderService.cancelOrder(token(), cookie(), marketId, orderId2);
        assertThat(cancelResponse.getStatusCode()).as("cancel order 2").isBetween(200, 299);
        cancelResponse.then().body("status", equalTo("user_cancelled")).body("order_id", equalTo(orderId2)).body("message", equalTo("Order cancellation submitted successfully"));

        balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());
        Response tradeHistoryRes = portfolioService.getTradeHistory(token(), cookie());
        assertThat(tradeHistoryRes.getStatusCode()).as("trade-history after flow").isEqualTo(200);
    }

    @Test(description = "E2E: User 1 has LONG bids (liquidity); User 2 places SHORT at 30c -> match. Assert User 2: open-orders empty, SHORT position, Open Short in trade-history, balance decreased by correct amount.")
    public void flow_twoUsers_placeLongAndShort_mayMatch_positions() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        UserSession user2 = SecondUserContext.getSecondUser();
        if (user2 == null || !user2.hasToken()) {
            throw new SkipException("Second user not set: run AuthFlowTestUser2 or set USER_2_* env / .env.session2");
        }

        int nShares = TWO_USER_MATCH_QUANTITY;
        String price = String.valueOf(ORDER_PRICE);
        double expectedAmountDeducted = (100 - Integer.parseInt(price)) * nShares / 100.0;

        Response balance2Res = portfolioService.getBalance(user2.getAccessToken(), user2.getRefreshCookie());
        assertThat(balance2Res.getStatusCode()).isEqualTo(200);
        long balance2Before = parseBalanceAsLong(balance2Res.path("usdc_balance").toString());

        Response history2Before = portfolioService.getTradeHistory(user2.getAccessToken(), user2.getRefreshCookie());
        assertThat(history2Before.getStatusCode()).isEqualTo(200);
        List<?> data2Before = history2Before.path("data");
        int history2CountBefore = data2Before != null ? data2Before.size() : 0;

        String orderIdShort = placeOrderAndReturnOrderIdForSession(user2, marketId, tokenId, "short", price, String.valueOf(nShares));
        assertThat(orderIdShort).as("User 2 place SHORT at 30c").isNotBlank();

        for (int i = 0; i < 50; i++) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            Response open2 = portfolioService.getOpenOrders(user2.getAccessToken(), user2.getRefreshCookie());
            if (open2.getStatusCode() != 200) continue;
            List<?> list2 = open2.path("data");
            if (list2 == null) list2 = open2.path("open_orders");
            if (list2 != null && list2.size() == 0) break;
        }

        Response open2 = portfolioService.getOpenOrders(user2.getAccessToken(), user2.getRefreshCookie());
        assertThat(open2.getStatusCode()).isEqualTo(200);
        List<?> list2 = open2.path("data");
        if (list2 == null) list2 = open2.path("open_orders");
        assertThat(list2 == null ? 0 : list2.size()).as("User 2 open-orders should be empty after match (order matched, not pending)").isEqualTo(0);

        Response pos2 = portfolioService.getPositions(user2.getAccessToken(), user2.getRefreshCookie());
        assertThat(pos2.getStatusCode()).isEqualTo(200);
        List<?> positions2 = pos2.path("positions");
        assertThat(positions2).as("User 2 should have at least one position (SHORT)").isNotEmpty();
        assertPositionHasSideAndQuantity(positions2, "short", nShares);

        Response th2 = portfolioService.getTradeHistory(user2.getAccessToken(), user2.getRefreshCookie());
        assertThat(th2.getStatusCode()).isEqualTo(200);
        assertLatestTradeActivity(th2, "Open Short");
        List<?> data2After = th2.path("data");
        assertThat(data2After != null ? data2After.size() : 0).as("User 2 trade-history should have one more entry").isEqualTo(history2CountBefore + 1);

        Response balance2AfterRes = portfolioService.getBalance(user2.getAccessToken(), user2.getRefreshCookie());
        assertThat(balance2AfterRes.getStatusCode()).isEqualTo(200);
        long balance2After = parseBalanceAsLong(balance2AfterRes.path("usdc_balance").toString());
        long actualDecrease = balance2Before - balance2After;
        assertThat(actualDecrease).as("User 2 balance should decrease after matched SHORT").isGreaterThan(0);
        assertThat(actualDecrease).as("User 2 balance decrease should be at least SHORT amount (price 30c, qty " + nShares + " = " + expectedAmountDeducted + ")").isGreaterThanOrEqualTo((long) expectedAmountDeducted);
    }

    @Test(description = "Place SHORT at best bid when external liquidity exists; assert position and trade-history. Skips when all bid liquidity belongs to this account (self-match prevention).")
    public void flow_placeShort_assertPositionAndTradeHistory() {
        if (marketId == null || marketId.isBlank() || tokenId == null || tokenId.isBlank()) throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("EOA or proxy not set");

        Response orderbookRes = orderService.getOrderbook(marketId);
        assertThat(orderbookRes.getStatusCode()).isEqualTo(200);
        Object totalBidObj = orderbookRes.path("metadata.total_bid_quantity");
        if (totalBidObj == null) totalBidObj = orderbookRes.path("total_bid_quantity");
        if (totalBidObj == null) throw new SkipException("Skipping: orderbook does not return total_bid_quantity.");
        long totalBidQuantity = (long) Double.parseDouble(String.valueOf(totalBidObj).trim());

        if (totalBidQuantity == 0) {
            throw new SkipException("Skipping: no bid liquidity in orderbook.");
        }

        List<?> bids = orderbookRes.path("bids");
        assertThat(bids).as("bids should not be empty when total_bid_quantity > 0").isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> bestBid = (Map<String, Object>) bids.get(0);
        String bestBidPrice = String.valueOf(bestBid.get("price")).trim();
        int nShares = 5;

        Response positionsBefore = portfolioService.getPositions(token(), cookie());
        assertThat(positionsBefore.getStatusCode()).isEqualTo(200);
        Response historyBefore = portfolioService.getTradeHistory(token(), cookie());
        assertThat(historyBefore.getStatusCode()).isEqualTo(200);
        List<?> dataBefore = historyBefore.path("data");
        int historyCountBefore = dataBefore != null ? dataBefore.size() : 0;

        Response placeRes = placeShortLimitOrderAtPriceWithResponse(bestBidPrice, String.valueOf(nShares));
        assertThat(placeRes).as("place SHORT response (sign-order may have failed)").isNotNull();
        if (placeRes.getStatusCode() == 422
                && placeRes.getBody().asString().contains("No external liquidity")) {
            throw new SkipException(
                    "Skipping: self-match prevention — this account owns the bid "
                            + "liquidity. Use the two-user flow test for E2E matching.");
        }
        Assert.assertEquals(placeRes.getStatusCode(), 202,
                "place-order returned " + placeRes.getStatusCode()
                        + " | body: " + placeRes.getBody().asString());

        Response positionsAfter = portfolioService.getPositions(token(), cookie());
        assertThat(positionsAfter.getStatusCode()).isEqualTo(200);
        List<?> posListAfter = positionsAfter.path("positions");
        assertThat(posListAfter).as("At least one position (SHORT) should exist after place").isNotEmpty();
        boolean foundShort = false;
        for (Object o : posListAfter) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) o;
                if ("short".equalsIgnoreCase(String.valueOf(p.get("side")))) {
                    Object qty = p.get("quantity");
                    if (qty != null && Integer.parseInt(String.valueOf(qty).trim()) == nShares) {
                        foundShort = true;
                        break;
                    }
                }
            }
        }
        assertThat(foundShort).as("Position with side=short, quantity=" + nShares + " should exist").isTrue();

        Response historyAfter = portfolioService.getTradeHistory(token(), cookie());
        assertThat(historyAfter.getStatusCode()).isEqualTo(200);
        List<?> dataAfter = historyAfter.path("data");
        assertThat(dataAfter != null ? dataAfter.size() : 0).as("Trade history count should increase by 1").isEqualTo(historyCountBefore + 1);
        Object latest = dataAfter != null && !dataAfter.isEmpty() ? dataAfter.get(dataAfter.size() - 1) : null;
        if (latest instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) latest;
            assertThat(String.valueOf(entry.get("activity")).trim()).isEqualTo("Open Short");
            assertThat(String.valueOf(entry.get("quantity")).trim()).isEqualTo(String.valueOf(nShares));
            assertThat(String.valueOf(entry.get("pnl")).trim()).isEqualTo("0");
        }
    }

    @Test(description = "Unrealized PnL formula: (avg_price - mark_price) * quantity / 100 for SHORT; total_pnl == realized + unrealized")
    public void flow_unrealizedPnl_matchesFormula() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (!TestPreConditions.hasOpenPosition(token(), cookie())) {
            throw new SkipException("No open positions to validate - run flow_placeShort or flow_twoUsers first");
        }

        Response positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        List<?> positions = positionsRes.path("positions");
        assertThat(positions).isNotEmpty();
        Map<String, Object> shortPos = null;
        for (Object o : positions) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) o;
                if ("short".equalsIgnoreCase(String.valueOf(p.get("side")))) {
                    shortPos = p;
                    break;
                }
            }
        }
        if (shortPos == null) throw new SkipException("No SHORT position to validate unrealized PnL formula");
        double avgPrice = Double.parseDouble(String.valueOf(shortPos.get("average_price")).trim());
        int quantity = Integer.parseInt(String.valueOf(shortPos.get("quantity")).trim());
        assertThat(quantity).isGreaterThan(0);

        Response orderbookRes = orderService.getOrderbook(marketId);
        assertThat(orderbookRes.getStatusCode()).isEqualTo(200);
        Object midObj = orderbookRes.path("metadata.mid_price");
        if (midObj == null) midObj = orderbookRes.path("mid_price");
        assertThat(midObj).isNotNull();
        double markPrice = Double.parseDouble(String.valueOf(midObj).trim());

        Response earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        double unrealizedPnl = parsePnlAsDouble(earningsRes.path("unrealized_pnl") != null ? earningsRes.path("unrealized_pnl").toString() : "0");
        double realizedPnl = parsePnlAsDouble(earningsRes.path("realized_pnl") != null ? earningsRes.path("realized_pnl").toString() : "0");
        double totalPnl = parsePnlAsDouble(earningsRes.path("total_pnl") != null ? earningsRes.path("total_pnl").toString() : "0");

        double expectedUnrealized = (avgPrice - markPrice) * quantity / 100.0;
        assertThat(Math.abs(unrealizedPnl - expectedUnrealized)).as("unrealized_pnl == (avg_price - mark_price) * quantity / 100 (SHORT)").isLessThanOrEqualTo(0.01);
        assertThat(Math.abs(totalPnl - (realizedPnl + unrealizedPnl))).as("total_pnl == realized_pnl + unrealized_pnl").isLessThanOrEqualTo(0.01);
    }

    private void assertPositionHasSideAndQuantity(List<?> positions, String side, int quantity) {
        for (Object o : positions) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) o;
                if (side.equalsIgnoreCase(String.valueOf(p.get("side")))) {
                    Object q = p.get("quantity");
                    assertThat(q).isNotNull();
                    assertThat(Integer.parseInt(String.valueOf(q).trim())).isEqualTo(quantity);
                    return;
                }
            }
        }
        throw new AssertionError("No position with side=" + side + " and quantity=" + quantity);
    }

    private void assertLatestTradeActivity(Response tradeHistoryRes, String expectedActivity) {
        List<?> data = tradeHistoryRes.path("data");
        assertThat(data).isNotEmpty();
        Object latest = data.get(data.size() - 1);
        assertThat(latest).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) latest;
        assertThat(String.valueOf(entry.get("activity")).trim()).isEqualTo(expectedActivity);
    }

    private String placeOrderAndReturnOrderId(String marketId, String tokenId) {
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;
        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(ORDER_PRICE)
                .quantity(ORDER_QUANTITY)
                .questionId(marketId)
                .timestamp(timestampSec)
                .feeRateBps(0)
                .intent(0)
                .signatureType(2)
                .taker("0x0000000000000000000000000000000000000000")
                .expiration("0")
                .nonce("0")
                .maker(proxyWallet)
                .signer(eoa)
                .priceInCents(false)
                .build();
        String keyForSign = TokenManager.getInstance().getPrivateKey();
        if (keyForSign == null || keyForSign.isBlank()) keyForSign = System.getenv("PRIVATE_KEY");
        if (keyForSign != null && !keyForSign.isBlank()) signRequest.setPrivateKey(keyForSign);
        SignOrderResponse sigResponse = signatureService.signOrder(Config.getSigServerUrl(), signRequest);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(userId)
                .marketId(marketId)
                .side("long")
                .tokenId(tokenId)
                .price(ORDER_PRICE)
                .quantity(ORDER_QUANTITY)
                .amount(ORDER_AMOUNT)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        Response response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, marketId, orderBody);
        assertThat(response.getStatusCode()).as("place order").isEqualTo(202);
        response.then().body("status", equalTo("open_order")).body("order_id", notNullValue());
        String orderId = response.jsonPath().getString("order_id");
        if (orderId == null || orderId.isBlank()) orderId = response.jsonPath().getString("data.order_id");
        return orderId != null ? orderId.trim() : "";
    }

    private String placeOrderAndReturnOrderIdForSession(UserSession session, String marketId, String tokenId, String side, String price, String quantity) {
        if (session == null || !session.hasToken()) return "";
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;
        int priceNum = (int) Double.parseDouble(price);
        String amount = side.equalsIgnoreCase("short")
                ? String.format("%.2f", (100 - priceNum) * Double.parseDouble(quantity) / 100.0)
                : String.format("%.2f", priceNum * Double.parseDouble(quantity) / 100.0);
        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(price)
                .quantity(quantity)
                .questionId(marketId)
                .timestamp(timestampSec)
                .feeRateBps(0)
                .intent(side.equalsIgnoreCase("short") ? 1 : 0)
                .signatureType(2)
                .taker("0x0000000000000000000000000000000000000000")
                .expiration("0")
                .nonce("0")
                .maker(session.getProxy())
                .signer(session.getEoa())
                .priceInCents(false)
                .build();
        String keyForSign = session.getPrivateKey();
        if (keyForSign != null && !keyForSign.isBlank()) signRequest.setPrivateKey(keyForSign);
        SignOrderResponse sigResponse = signatureService.signOrder(Config.getSigServerUrl(), signRequest);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(session.getUserId())
                .marketId(marketId)
                .side(side)
                .tokenId(tokenId)
                .price(price)
                .quantity(quantity)
                .amount(amount)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        Response response = orderService.placeOrder(
                session.getAccessToken(), session.getRefreshCookie(),
                session.getEoa(), session.getProxy(), marketId, orderBody);
        assertThat(response.getStatusCode()).as("place order for session").isEqualTo(202);
        String orderId = response.jsonPath().getString("order_id");
        if (orderId == null || orderId.isBlank()) orderId = response.jsonPath().getString("data.order_id");
        return orderId != null ? orderId.trim() : "";
    }

    /** Places SHORT limit order (single attempt). Returns place-order response, or null if sign-order failed. */
    private Response placeShortLimitOrderAtPriceWithResponse(String price, String quantity) {
        return attemptPlaceShortLimitOrder(price, quantity);
    }

    /** Single attempt: sign + place SHORT limit order. Returns place-order response, or null if sign-order failed. */
    private Response attemptPlaceShortLimitOrder(String price, String quantity) {
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;
        int priceNum = (int) Double.parseDouble(price);
        double shortAmountVal = (100 - priceNum) * Double.parseDouble(quantity) / 100.0;
        String amount = String.format("%.2f", shortAmountVal);
        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(price)
                .quantity(quantity)
                .questionId(marketId)
                .timestamp(timestampSec)
                .feeRateBps(0)
                .intent(1)
                .signatureType(2)
                .taker("0x0000000000000000000000000000000000000000")
                .expiration("0")
                .nonce("0")
                .maker(proxyWallet)
                .signer(eoa)
                .priceInCents(false)
                .build();
        String keyForSign = TokenManager.getInstance().getPrivateKey();
        if (keyForSign == null || keyForSign.isBlank()) keyForSign = System.getenv("PRIVATE_KEY");
        if (keyForSign != null && !keyForSign.isBlank()) signRequest.setPrivateKey(keyForSign);
        SignOrderResponse sigResponse = signatureService.signOrder(Config.getSigServerUrl(), signRequest);
        if (sigResponse == null || !sigResponse.isOk()) return null;
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(userId)
                .marketId(marketId)
                .side("short")
                .tokenId(tokenId)
                .price(price)
                .quantity(quantity)
                .amount(amount)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        return orderService.placeOrder(token(), cookie(), eoa, proxyWallet, marketId, orderBody);
    }
}
