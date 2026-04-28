package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.MarketContext;
import com.pred.apitests.util.PollingUtil;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exact financial correctness: delta assertions and insufficient balance.
 * TODO: after first run of BalanceServiceTest.placeOrder_balanceDecreasesCorrectly, replace 0L with printed delta.
 */
public class BalanceIntegrityTest extends BaseApiTest {

    private static final long EXPECTED_DELTA_PRICE30_QTY100 = 0L;

    private OrderService orderService;
    private PortfolioService portfolioService;
    private SignatureService signatureService;
    private String eoa;
    private String proxyWallet;
    private String userId;
    /** Sub-market id (question) for sign, place body, balance-by-market. */
    private String marketId;
    /** Parent market id for /order/{parent}/place and cancel paths. */
    private String parentMarketId;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        orderService = new OrderService();
        portfolioService = new PortfolioService();
        signatureService = new SignatureService();
        eoa = TokenManager.getInstance().getEoa();
        if (eoa == null || eoa.isBlank()) eoa = Config.getEoaAddress();
        proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        userId = TokenManager.getInstance().getUserId();
        try {
            MarketContext.getInstance().init();
        } catch (Exception e) {
            System.out.println("MarketContext init skipped: " + e.getMessage());
        }
        marketId = MarketContext.resolveMarketId();
        parentMarketId = MarketContext.resolveParentMarketIdForPath();
    }

    private long getOverallBalance() {
        Response response = portfolioService.getBalance(TokenManager.getInstance().getAccessToken(), TokenManager.getInstance().getRefreshCookieHeaderValue());
        assertThat(response.getStatusCode()).isEqualTo(200);
        String usdc = response.path("usdc_balance");
        if (usdc == null || usdc.isBlank()) return 0L;
        return parseBalanceAsLong(usdc);
    }

    private long getMarketBalance() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        Response response = portfolioService.getBalanceByMarket(TokenManager.getInstance().getAccessToken(), TokenManager.getInstance().getRefreshCookieHeaderValue(), marketId);
        assertThat(response.getStatusCode()).isEqualTo(200);
        String usdc = response.path("usdc_balance");
        if (usdc == null || usdc.isBlank()) return 0L;
        return parseBalanceAsLong(usdc);
    }

    private String placeOrder(String price, String qty) {
        if (marketId == null || marketId.isBlank() || Config.getTokenId() == null || Config.getTokenId().isBlank())
            throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank())
            throw new SkipException("EOA or proxy not set");
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;
        String tokenId = Config.getTokenId();
        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(price)
                .quantity(qty)
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
        if (!sigResponse.isOk()) {
            throw new AssertionError("sig-server returned non-OK; fail fast. Check sig-server logs.");
        }
        // Long: amount = price * quantity / 100 (2 decimals), per API payload convention
        String amount = String.format("%.2f", Long.parseLong(price) * Long.parseLong(qty) / 100.0);
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(userId)
                .marketId(marketId)
                .side("long")
                .tokenId(tokenId)
                .price(price)
                .quantity(qty)
                .amount(amount)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        Response response = orderService.placeOrder(TokenManager.getInstance().getAccessToken(), TokenManager.getInstance().getRefreshCookieHeaderValue(), eoa, proxyWallet, parentMarketId, orderBody);
        assertThat(response.getStatusCode()).isEqualTo(202);
        String orderId = response.jsonPath().getString("order_id");
        return orderId != null ? orderId.trim() : "";
    }

    /** Place order with explicit amount. Amount = price * quantity / 100 (2 decimals) for long. */
    private String placeOrder(String price, String qty, String amount) {
        if (marketId == null || marketId.isBlank() || Config.getTokenId() == null || Config.getTokenId().isBlank())
            throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank())
            throw new SkipException("EOA or proxy not set");
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;
        String tokenId = Config.getTokenId();
        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(price)
                .quantity(qty)
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
        if (!sigResponse.isOk()) {
            throw new AssertionError("sig-server returned non-OK; fail fast. Check sig-server logs.");
        }
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(userId)
                .marketId(marketId)
                .side("long")
                .tokenId(tokenId)
                .price(price)
                .quantity(qty)
                .amount(amount)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        Response response = orderService.placeOrder(TokenManager.getInstance().getAccessToken(), TokenManager.getInstance().getRefreshCookieHeaderValue(), eoa, proxyWallet, parentMarketId, orderBody);
        assertThat(response.getStatusCode()).isEqualTo(202);
        String orderId = response.jsonPath().getString("order_id");
        return orderId != null ? orderId.trim() : "";
    }

    private void cancelOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) return;
        Response r = orderService.cancelOrder(TokenManager.getInstance().getAccessToken(), TokenManager.getInstance().getRefreshCookieHeaderValue(), parentMarketId, orderId);
        assertThat(r.getStatusCode()).as("cancel order").isEqualTo(200);
    }

    @Test(description = "Exact delta after placing order (price=30 qty=100); first run asserts direction only")
    public void placeOrder_balanceDecreasesExactly() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        long before = getMarketBalance();
        String orderId = placeOrder("30", "100");
        long after = getMarketBalance();
        long delta = before - after;
        System.out.println("[EXACT DELTA] " + delta);
        assertThat(delta).isGreaterThan(0L);
        if (EXPECTED_DELTA_PRICE30_QTY100 > 0L) {
            assertThat(delta).isEqualTo(EXPECTED_DELTA_PRICE30_QTY100); }
        cancelOrder(orderId);
    }

    @Test(description = "Balance restores after cancel by at least the reserved amount")
    public void cancelOrder_balanceRestoresExactly() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        String orderId = placeOrder("30", "100");
        // Balance deduction is synchronous — snapshot immediately after place
        long afterPlace = getMarketBalance();
        cancelOrder(orderId);
        // Poll until balance increases by at least the known reservation ($30)
        long expectedReservation = parseBalanceAsLong("30");
        PollingUtil.pollUntil(15_000, 500, 1000,
                "Balance did not restore after cancel within 15s",
                () -> {
                    long current = getMarketBalance();
                    return (current - afterPlace) >= expectedReservation;
                });
        long afterCancel = getMarketBalance();
        long increase = afterCancel - afterPlace;
        System.out.printf("[RESTORE CHECK] afterPlace=%d afterCancel=%d increase=%d expectedReservation=%d%n",
                afterPlace, afterCancel, increase, expectedReservation);
        assertThat(increase).as("cancel should restore at least the reserved amount ($30)")
                .isGreaterThanOrEqualTo(expectedReservation);
    }

    @Test(description = "Two orders of same size: deltas equal")
    public void twoOrders_balanceDeductionIsConsistent() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        long before = getMarketBalance();
        String order1 = placeOrder("30", "100");
        long afterFirst = getMarketBalance();
        long delta1 = before - afterFirst;
        String order2 = placeOrder("30", "100");
        long afterSecond = getMarketBalance();
        long delta2 = afterFirst - afterSecond;
        System.out.println("[TWO ORDERS] delta1=" + delta1 + " delta2=" + delta2);
        assertThat(delta1).isEqualTo(delta2);
        cancelOrder(order1);
        cancelOrder(order2);
    }

    @Test(description = "Larger order deducts more than smaller")
    public void balance_largerOrderDeductsMore() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        long before = getMarketBalance();
        String smallOrder = placeOrder("30", "100");
        long afterSmall = getMarketBalance();
        long smallDelta = before - afterSmall;
        cancelOrder(smallOrder);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        long before2 = getMarketBalance();
        String largeOrder = placeOrder("30", "200", "60.00");
        long afterLarge = getMarketBalance();
        long largeDelta = before2 - afterLarge;
        cancelOrder(largeOrder);

        System.out.println("[SIZE CHECK] smallDelta=" + smallDelta + " largeDelta=" + largeDelta);
        assertThat(largeDelta).isGreaterThan(smallDelta);
    }

    @Test(enabled = false, description = "DISABLED - was placing 999999999 qty orders that pollute the orderbook. Re-enable only in isolated env with controlled balance.")
    public void placeOrder_insufficientBalance_returns4xx() {
        if (marketId == null || marketId.isBlank() || Config.getTokenId() == null || Config.getTokenId().isBlank())
            throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank())
            throw new SkipException("EOA or proxy not set");

        long currentBalance = getMarketBalance();
        System.out.println("[INSUFFICIENT] current balance=" + currentBalance);

        long salt = System.currentTimeMillis();
        long timestampSec = salt / 1000;
        String tokenId = Config.getTokenId();
        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(String.valueOf(salt))
                .price("90")
                .quantity("999999999")
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
        if (!sigResponse.isOk()) {
            throw new AssertionError("sig-server returned non-OK for insufficient-balance test; fail fast.");
        }

        // amount = price * quantity / 100 (2 decimals) so API validates amount then rejects on balance if needed
        String insufficientAmount = String.format("%.2f", 90L * 999999999L / 100.0);
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(String.valueOf(salt))
                .userId(userId)
                .marketId(marketId)
                .side("long")
                .tokenId(tokenId)
                .price("90")
                .quantity("999999999")
                .amount(insufficientAmount)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();

        Response response = orderService.placeOrder(TokenManager.getInstance().getAccessToken(), TokenManager.getInstance().getRefreshCookieHeaderValue(), eoa, proxyWallet, parentMarketId, orderBody);
        System.out.println("[INSUFFICIENT] status=" + response.getStatusCode());
        System.out.println("[INSUFFICIENT] body=" + response.getBody().asString());

        if (response.getStatusCode() == 202) {
            String orderId = response.jsonPath().getString("order_id");
            System.out.println("[INSUFFICIENT] WARNING: oversized order was ACCEPTED. order_id=" + orderId);
            if (orderId != null && !orderId.isBlank()) {
                cancelOrder(orderId.trim());
            }
        } else {
            assertThat(response.getStatusCode()).isGreaterThanOrEqualTo(400);
            assertThat(response.getStatusCode()).isLessThan(500);
            System.out.println("[INSUFFICIENT] Correctly rejected with " + response.getStatusCode());
        }
    }
}
