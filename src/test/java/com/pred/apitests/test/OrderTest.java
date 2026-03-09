package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.BalanceResponse;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

public class OrderTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String ORDER_AMOUNT = "30";

    private OrderService orderService;
    private SignatureService signatureService;
    private PortfolioService portfolioService;
    private String eoa;
    private String proxyWallet;
    private String userId;

    /** Use current token/cookie from TokenManager so proactive refresh (e.g. after 40 min) is used. */
    private String token() {
        return TokenManager.getInstance().getAccessToken();
    }
    private String cookie() {
        return TokenManager.getInstance().getRefreshCookieHeaderValue();
    }

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        String refreshCookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new SkipException("No refresh_token cookie - backend must return Set-Cookie: refresh_token=... on login so long runs stay authenticated");
        }
        orderService = new OrderService();
        signatureService = new SignatureService();
        portfolioService = new PortfolioService();
        eoa = TokenManager.getInstance().getEoa();
        if (eoa == null || eoa.isBlank()) {
            eoa = Config.getEoaAddress();
            if (eoa != null && !eoa.isBlank()) {
                TokenManager.getInstance().setEoa(eoa);
            }
        }
        proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        userId = TokenManager.getInstance().getUserId();
    }

    @Test(description = "Place order with valid signature returns 202")
    public void placeOrder_withValidSignature_returns202() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        // Salt and timestamp: same values for sign-order and place-order. Salt = decimal string, timestamp = Unix seconds.
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
        // Use same key as login (from TokenManager or env) so recovered signer matches expected EOA
        String keyForSign = TokenManager.getInstance().getPrivateKey();
        if (keyForSign == null || keyForSign.isBlank()) {
            keyForSign = System.getenv("PRIVATE_KEY");
        }
        if (keyForSign != null && !keyForSign.isBlank()) {
            signRequest.setPrivateKey(keyForSign);
        }
        SignOrderResponse sigResponse = signatureService.signOrder(Config.getSigServerUrl(), signRequest);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();

        System.out.println("DEBUG [sign-order] salt=" + salt + " timestamp=" + timestampSec + " price=" + ORDER_PRICE + " quantity=" + ORDER_QUANTITY + " questionId=" + marketId);
        if (sigResponse.getSignedMessage() != null) {
            System.out.println("DEBUG [signed_message from sig-server] " + sigResponse.getSignedMessage());
        }

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
        int status = response.getStatusCode();
        if (status != 202) {
            String body = response.getBody().asString();
            System.out.println("DEBUG [place-order FAIL] status=" + status + " body=" + (body != null ? body : ""));
        }
        assertThat(status).isEqualTo(202);
        response.then().body("status", equalTo("open_order"))
                .body("order_id", notNullValue())
                .body("message", equalTo("Order placed successfully"))
                .body("filled_quantity", equalTo("0"));
    }

    @Test(description = "Cancel order with valid order_id and market_id returns 2xx")
    public void cancelOrder_withValidOrderId_returns2xx() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        String orderId = placeOrderAndReturnOrderId(marketId, tokenId);
        assertThat(orderId).as("place order should return order_id").isNotBlank();

        Response response = orderService.cancelOrder(token(), cookie(), marketId, orderId);
        assertThat(response.getStatusCode()).as("cancel order response").isBetween(200, 299);
        response.then().body("status", equalTo("user_cancelled"))
                .body("order_id", equalTo(orderId))
                .body("message", equalTo("Order cancellation submitted successfully"));
        String body = response.getBody().asString();
        assertThat(body).as("cancel response body").contains("user_cancelled").contains("order_id");
    }

    /**
     * Full flow: place order 1 -> check balance/earnings/positions -> place order 2 (different signature) ->
     * check balance/earnings/positions -> cancel order 2 -> check balance/earnings/positions.
     * PnL data comes from earnings API (/api/v1/portfolio/earnings). Position may appear when another user matches order 1.
     */
    @Test(description = "Flow: place 2 orders (2nd with different signature), cancel 2nd; check balance, earnings, positions in between")
    public void flow_placeTwoOrders_cancelSecond_withBalancePnlPositionsChecks() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        // 1) Baseline: balance, earnings (PnL), positions
        Response balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        balanceRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());
        long balanceBefore = Long.parseLong(balanceRes.path("usdc_balance").toString());
        Response earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        earningsRes.then().body("user_id", notNullValue()).body("realized_pnl", notNullValue()).body("unrealized_pnl", notNullValue()).body("total_pnl", notNullValue());
        Response positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());

        // 2) Place order 1 (first signature)
        String orderId1 = placeOrderAndReturnOrderId(marketId, tokenId);
        assertThat(orderId1).as("place order 1 should return order_id in response").isNotBlank();

        // 3) After order 1: balance, earnings, positions
        balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        balanceRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue());
        long balanceAfterOrder1 = Long.parseLong(balanceRes.path("usdc_balance").toString());
        assertThat(balanceAfterOrder1).isLessThanOrEqualTo(balanceBefore);
        earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        earningsRes.then().body("user_id", notNullValue()).body("total_pnl", notNullValue());
        positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());

        // 4) Place order 2 (different salt/timestamp -> different signature)
        String orderId2 = placeOrderAndReturnOrderId(marketId, tokenId);
        assertThat(orderId2).as("place order 2 should return order_id in response").isNotBlank();
        assertThat(orderId2).as("order 2 id should differ from order 1").isNotEqualTo(orderId1);

        // 5) After order 2: balance, earnings, positions
        balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        balanceRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue());
        earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        earningsRes.then().body("user_id", notNullValue()).body("total_pnl", notNullValue());
        positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());

        // 6) Cancel order 2
        Response cancelResponse = orderService.cancelOrder(token(), cookie(), marketId, orderId2);
        assertThat(cancelResponse.getStatusCode()).as("cancel order 2").isBetween(200, 299);
        cancelResponse.then().body("status", equalTo("user_cancelled")).body("order_id", equalTo(orderId2)).body("message", equalTo("Order cancellation submitted successfully"));

        // 7) After cancel: balance, earnings, positions (order 1 still open; position may appear if order 1 is matched later)
        balanceRes = portfolioService.getBalance(token(), cookie());
        assertThat(balanceRes.getStatusCode()).isEqualTo(200);
        balanceRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue());
        earningsRes = portfolioService.getEarnings(token(), cookie());
        assertThat(earningsRes.getStatusCode()).isEqualTo(200);
        earningsRes.then().body("user_id", notNullValue()).body("total_pnl", notNullValue());
        positionsRes = portfolioService.getPositions(token(), cookie());
        assertThat(positionsRes.getStatusCode()).isEqualTo(200);
        positionsRes.then().body("success", equalTo(true)).body("positions", notNullValue());
    }

    /**
     * Place one order with a unique salt/timestamp (so signature is unique). Returns order_id from response.
     * Expected place-order response shape: { "status": "open_order", "order_id": "<uuid>", "message": "...", "filled_quantity": "0" }
     */
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
        response.then().body("status", equalTo("open_order"))
                .body("order_id", notNullValue())
                .body("message", equalTo("Order placed successfully"))
                .body("filled_quantity", notNullValue());

        // Response: { "status": "open_order", "order_id": "<uuid>", "message": "...", "filled_quantity": "0" }
        String orderId = response.jsonPath().getString("order_id");
        if (orderId == null || orderId.isBlank()) {
            orderId = response.jsonPath().getString("data.order_id");
        }
        return orderId != null ? orderId.trim() : "";
    }

    @Test(description = "Place order with invalid signature returns 4xx")
    public void placeOrder_withInvalidSignature_returns4xx() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");

        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(String.valueOf(System.currentTimeMillis()))
                .userId(userId)
                .marketId(marketId)
                .side("long")
                .tokenId("0x0000000000000000000000000000000000000000")
                .price("30")
                .quantity("100")
                .amount("30")
                .isLowPriority(false)
                .signature("0x000000")
                .type("limit")
                .timestamp(System.currentTimeMillis() / 1000)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();

        Response response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, marketId, orderBody);
        assertThat(response.getStatusCode()).isGreaterThanOrEqualTo(400);
        Object err = response.path("error");
        Object msg = response.path("message");
        assertThat(err != null || msg != null).as("4xx response should have error or message").isTrue();
        if (err != null) {
            String responseBody = response.getBody().asString();
            assertThat(responseBody).as("error response should mention signature").matches(s -> s.contains("signature") || s.contains("error_code"));
        }
    }

    @Test(description = "Place order with balance check before and after; balance should reflect open order")
    public void placeOrder_balanceBeforeAndAfterReflectsOrder() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        // 1) Balance before
        Response balanceOverallBefore = portfolioService.getBalance(token(), cookie());
        Response balanceByMarketBefore = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        assertThat(balanceOverallBefore.getStatusCode()).isEqualTo(200);
        assertThat(balanceByMarketBefore.getStatusCode()).isEqualTo(200);
        balanceOverallBefore.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());
        balanceByMarketBefore.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());

        BalanceResponse beforeOverall = balanceOverallBefore.as(BalanceResponse.class);
        BalanceResponse beforeByMarket = balanceByMarketBefore.as(BalanceResponse.class);
        logBalance("BEFORE (overall)", beforeOverall);
        logBalance("BEFORE (by market)", beforeByMarket);

        // 2) Place order
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

        Response orderResponse = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, marketId, orderBody);
        assertThat(orderResponse.getStatusCode()).isEqualTo(202);
        orderResponse.then().body("status", equalTo("open_order"))
                .body("order_id", notNullValue())
                .body("message", equalTo("Order placed successfully"))
                .body("filled_quantity", notNullValue());

        // 3) Balance after
        Response balanceOverallAfter = portfolioService.getBalance(token(), cookie());
        Response balanceByMarketAfter = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        assertThat(balanceOverallAfter.getStatusCode()).isEqualTo(200);
        assertThat(balanceByMarketAfter.getStatusCode()).isEqualTo(200);
        balanceOverallAfter.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());
        balanceByMarketAfter.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());

        long balanceBefore = Long.parseLong(balanceByMarketBefore.path("usdc_balance").toString());
        long balanceAfter = Long.parseLong(balanceByMarketAfter.path("usdc_balance").toString());
        assertThat(balanceAfter).isLessThan(balanceBefore);
        assertThat(balanceBefore - balanceAfter).isGreaterThan(0);

        BalanceResponse afterOverall = balanceOverallAfter.as(BalanceResponse.class);
        BalanceResponse afterByMarket = balanceByMarketAfter.as(BalanceResponse.class);
        logBalance("AFTER (overall)", afterOverall);
        logBalance("AFTER (by market)", afterByMarket);

        // 4) Assert balance reflects open order: reserved should increase or available decrease (when API provides these)
        assertBalanceReflectsOrder(beforeByMarket, afterByMarket, ORDER_AMOUNT);
    }

    @Test(description = "Balance: overall usdc_balance >= by-market usdc_balance (market-level accounts for reserved); when total/available/reserved present, available = total - reserved")
    public void balance_availableEqualsTotalMinusReserved_whenFieldsPresent() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");

        Response overallRes = portfolioService.getBalance(token(), cookie());
        Response byMarketRes = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        assertThat(overallRes.getStatusCode()).isEqualTo(200);
        assertThat(byMarketRes.getStatusCode()).isEqualTo(200);
        overallRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());
        byMarketRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());

        long overall = Long.parseLong(overallRes.path("usdc_balance").toString());
        long byMarket = Long.parseLong(byMarketRes.path("usdc_balance").toString());
        assertThat(overall).isGreaterThanOrEqualTo(byMarket);

        assertAvailableEqualsTotalMinusReserved(overallRes.as(BalanceResponse.class));
        assertAvailableEqualsTotalMinusReserved(byMarketRes.as(BalanceResponse.class));
    }

    private static void logBalance(String label, BalanceResponse b) {
        if (b == null) return;
        System.out.println("Balance " + label + ": success=" + b.isSuccess()
                + " usdc_balance=" + b.getUsdcBalance()
                + " position_balance=" + b.getPositionBalance()
                + " reserved_balance=" + b.getReservedBalance()
                + " available_balance=" + b.getAvailableBalance()
                + " total_balance=" + b.getTotalBalance());
    }

    private static void assertBalanceReflectsOrder(BalanceResponse before, BalanceResponse after, String orderAmountStr) {
        if (before == null || after == null) return;
        long orderAmount = parseBalance(orderAmountStr);
        if (orderAmount < 0) return;

        Long reservedBefore = parseBalance(before.getReservedBalance());
        Long reservedAfter = parseBalance(after.getReservedBalance());
        if (reservedBefore != null && reservedAfter != null) {
            assertThat(reservedAfter).as("reserved_balance should increase after placing order").isGreaterThanOrEqualTo(reservedBefore);
        }

        Long availableBefore = parseBalance(before.getAvailableBalance());
        Long availableAfter = parseBalance(after.getAvailableBalance());
        if (availableBefore != null && availableAfter != null) {
            assertThat(availableAfter).as("available_balance should decrease or stay same after placing order").isLessThanOrEqualTo(availableBefore);
        }

        // When API only returns usdc_balance (e.g. by market), assert it decreased by order amount (balance reflects open order)
        Long usdcBefore = parseBalance(before.getUsdcBalance());
        Long usdcAfter = parseBalance(after.getUsdcBalance());
        if (usdcBefore != null && usdcAfter != null && reservedBefore == null && availableBefore == null) {
            assertThat(usdcAfter).as("usdc_balance (by market) should decrease by order amount when reserved/available not returned").isEqualTo(usdcBefore - orderAmount);
        }
    }

    private static void assertAvailableEqualsTotalMinusReserved(BalanceResponse b) {
        if (b == null) return;
        Long total = parseBalance(b.getTotalBalance());
        Long available = parseBalance(b.getAvailableBalance());
        Long reserved = parseBalance(b.getReservedBalance());
        if (total != null && available != null && reserved != null) {
            assertThat(available).as("available_balance should equal total_balance - reserved_balance").isEqualTo(total - reserved);
        }
    }

    private static Long parseBalance(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Test(description = "Place order with zero quantity returns 4xx")
    public void placeOrder_withZeroQuantity_returns4xx() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;

        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(ORDER_PRICE)
                .quantity("0")
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
                .quantity("0")
                .amount("0")
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();

        Response response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, marketId, orderBody);
        response.then().statusCode(greaterThanOrEqualTo(400))
                .statusCode(lessThan(500))
                .body("error", notNullValue());
    }

    @Test(description = "Place order with negative price returns 4xx")
    public void placeOrder_withNegativePrice_returns4xx() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(String.valueOf(System.currentTimeMillis()))
                .userId(userId)
                .marketId(marketId)
                .side("long")
                .tokenId(tokenId)
                .price("-1")
                .quantity(ORDER_QUANTITY)
                .amount("-1")
                .isLowPriority(false)
                .signature("0xAABBCC")
                .type("limit")
                .timestamp(System.currentTimeMillis() / 1000)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();

        Response response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, marketId, orderBody);
        response.then().statusCode(greaterThanOrEqualTo(400))
                .statusCode(lessThan(500))
                .body("error", notNullValue());
    }

    @Test(description = "Place order with invalid market id returns 4xx")
    public void placeOrder_withInvalidMarketId_returns4xx() {
        String tokenId = Config.getTokenId();
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        String invalidMarketId = "invalid-market-000";
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(String.valueOf(System.currentTimeMillis()))
                .userId(userId)
                .marketId(invalidMarketId)
                .side("long")
                .tokenId(tokenId)
                .price(ORDER_PRICE)
                .quantity(ORDER_QUANTITY)
                .amount(ORDER_AMOUNT)
                .isLowPriority(false)
                .signature("0xAABBCC")
                .type("limit")
                .timestamp(System.currentTimeMillis() / 1000)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();

        Response response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, invalidMarketId, orderBody);
        response.then().statusCode(greaterThanOrEqualTo(400))
                .statusCode(lessThan(500));
        if (response.getContentType() != null && response.getContentType().toLowerCase().contains("json")) {
            Object err = response.path("error");
            Object msg = response.path("message");
            assertThat(err != null || msg != null).as("4xx response should have error or message").isTrue();
        }
    }

    @Test(description = "Cancel order with non-existent order id returns 4xx")
    public void cancelOrder_withInvalidOrderId_returns4xx() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");

        Response response = orderService.cancelOrder(token(), cookie(), marketId, "non-existent-order-id-000");
        response.then().statusCode(greaterThanOrEqualTo(400))
                .statusCode(lessThan(500));
        assertThat(response.path("error") != null || response.path("message") != null).isTrue();
    }
}
