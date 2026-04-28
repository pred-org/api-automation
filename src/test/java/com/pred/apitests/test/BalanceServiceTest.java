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
import com.pred.apitests.util.MarketContext;
import com.pred.apitests.util.PollingUtil;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Balance correctness around order placement. Discovers delta for price=30 qty=100.
 */
public class BalanceServiceTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String ORDER_AMOUNT = "30";

    private OrderService orderService;
    private PortfolioService portfolioService;
    private SignatureService signatureService;
    private String token;
    private String cookie;
    private String eoa;
    private String proxyWallet;
    private String userId;
    private String marketId;
    private String parentMarketId;

    /** Discovered delta from placeOrder_balanceDecreasesCorrectly (price=30, qty=100). */
    private long discoveredDelta;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        orderService = new OrderService();
        portfolioService = new PortfolioService();
        signatureService = new SignatureService();
        token = TokenManager.getInstance().getAccessToken();
        cookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
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
        Response response = portfolioService.getBalance(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        String usdc = response.path("usdc_balance");
        if (usdc == null || usdc.isBlank()) return 0L;
        return parseBalanceAsLong(usdc);
    }

    private long getMarketBalance() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        Response response = portfolioService.getBalanceByMarket(token, cookie, marketId);
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
        assertThat(sigResponse.isOk()).isTrue();
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(userId)
                .marketId(marketId)
                .side("long")
                .tokenId(tokenId)
                .price(price)
                .quantity(qty)
                .amount(String.format("%.2f", Long.parseLong(price) * Long.parseLong(qty) / 100.0))
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        Response response = orderService.placeOrder(token, cookie, eoa, proxyWallet, parentMarketId, orderBody);
        assertThat(response.getStatusCode()).isEqualTo(202);
        String orderId = response.jsonPath().getString("order_id");
        return orderId != null ? orderId.trim() : "";
    }

    private void cancelOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) return;
        Response r = orderService.cancelOrder(token, cookie, parentMarketId, orderId);
        assertThat(r.getStatusCode()).as("cancel order").isEqualTo(200);
    }

    @Test(description = "Balance restores after cancelling order by at least the reserved amount")
    public void cancelOrder_balanceRestores() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        String orderId = placeOrder(ORDER_PRICE, ORDER_QUANTITY);
        // Balance deduction is synchronous — snapshot immediately after place
        long afterPlace = getMarketBalance();
        cancelOrder(orderId);
        // Poll until balance increases by at least the known reservation ($30)
        long expectedReservation = parseBalanceAsLong(ORDER_AMOUNT);
        PollingUtil.pollUntil(15_000, 500, 1000,
                "Balance did not restore after cancel within 15s",
                () -> {
                    long current = getMarketBalance();
                    return (current - afterPlace) >= expectedReservation;
                });
        long afterCancel = getMarketBalance();
        long increase = afterCancel - afterPlace;
        System.out.printf("[CANCEL_BALANCE] afterPlace=%d afterCancel=%d increase=%d expectedReservation=%d%n",
                afterPlace, afterCancel, increase, expectedReservation);
        assertThat(increase).as("cancel should restore at least the reserved amount ($%s)", ORDER_AMOUNT)
                .isGreaterThanOrEqualTo(expectedReservation);
    }

    @Test(description = "Discover exact delta for price=30 qty=100; assert positive and reasonable")
    public void placeOrder_balanceDecreasesCorrectly() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        long balanceBefore = getMarketBalance();
        String orderId = placeOrder("30", "100");
        long balanceAfter = getMarketBalance();
        long actualDelta = balanceBefore - balanceAfter;

        System.out.println("[DELTA DISCOVERY] price=30 qty=100 delta=" + actualDelta);

        assertThat(actualDelta).isGreaterThan(0L);
        assertThat(actualDelta).isLessThan(1_000_000L);

        this.discoveredDelta = actualDelta;

        cancelOrder(orderId);
    }

    @Test(description = "Place order with balance check before and after; balance should reflect open order")
    public void placeOrder_balanceReflectsOpenOrder() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (Config.getTokenId() == null || Config.getTokenId().isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("EOA or proxy not set");

        Response balanceOverallBefore = portfolioService.getBalance(token, cookie);
        Response balanceByMarketBefore = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(balanceOverallBefore.getStatusCode()).isEqualTo(200);
        assertThat(balanceByMarketBefore.getStatusCode()).isEqualTo(200);
        balanceOverallBefore.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());
        balanceByMarketBefore.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());

        BalanceResponse beforeByMarket = balanceByMarketBefore.as(BalanceResponse.class);
        logBalance("BEFORE (by market)", beforeByMarket);

        String orderId = placeOrder(ORDER_PRICE, ORDER_QUANTITY);
        assertThat(orderId).isNotBlank();

        Response balanceOverallAfter = portfolioService.getBalance(token, cookie);
        Response balanceByMarketAfter = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(balanceOverallAfter.getStatusCode()).isEqualTo(200);
        assertThat(balanceByMarketAfter.getStatusCode()).isEqualTo(200);
        long balanceBefore = parseBalanceAsLong(balanceByMarketBefore.path("usdc_balance").toString());
        long balanceAfter = parseBalanceAsLong(balanceByMarketAfter.path("usdc_balance").toString());
        assertThat(balanceAfter).isLessThan(balanceBefore);
        assertThat(balanceBefore - balanceAfter).isGreaterThan(0);

        BalanceResponse afterByMarket = balanceByMarketAfter.as(BalanceResponse.class);
        logBalance("AFTER (by market)", afterByMarket);
        assertBalanceReflectsOrder(beforeByMarket, afterByMarket, ORDER_AMOUNT);

        cancelOrder(orderId);
    }

    @Test(description = "Balance: overall usdc_balance >= by-market usdc_balance; when total/available/reserved present, available = total - reserved")
    public void balance_availableEqualsTotalMinusReserved() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");

        Response overallRes = portfolioService.getBalance(token, cookie);
        Response byMarketRes = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(overallRes.getStatusCode()).isEqualTo(200);
        assertThat(byMarketRes.getStatusCode()).isEqualTo(200);
        overallRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());
        byMarketRes.then().body("success", equalTo(true)).body("usdc_balance", notNullValue()).body("position_balance", notNullValue());

        long overall = parseBalanceAsLong(overallRes.path("usdc_balance").toString());
        long byMarket = parseBalanceAsLong(byMarketRes.path("usdc_balance").toString());
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
        long orderAmount = parseBalanceLong(orderAmountStr);
        if (orderAmount < 0) return;

        Long reservedBefore = parseBalanceLong(before.getReservedBalance());
        Long reservedAfter = parseBalanceLong(after.getReservedBalance());
        if (reservedBefore != null && reservedAfter != null) {
            assertThat(reservedAfter).as("reserved_balance should increase after placing order").isGreaterThanOrEqualTo(reservedBefore);
        }

        Long availableBefore = parseBalanceLong(before.getAvailableBalance());
        Long availableAfter = parseBalanceLong(after.getAvailableBalance());
        if (availableBefore != null && availableAfter != null) {
            assertThat(availableAfter).as("available_balance should decrease or stay same after placing order").isLessThanOrEqualTo(availableBefore);
        }

        Long usdcBefore = parseBalanceLong(before.getUsdcBalance());
        Long usdcAfter = parseBalanceLong(after.getUsdcBalance());
        if (usdcBefore != null && usdcAfter != null && reservedBefore == null && availableBefore == null) {
            assertThat(usdcAfter).as("usdc_balance (by market) should decrease by order amount when reserved/available not returned").isEqualTo(usdcBefore - orderAmount);
        }
    }

    private static void assertAvailableEqualsTotalMinusReserved(BalanceResponse b) {
        if (b == null) return;
        Long total = parseBalanceLong(b.getTotalBalance());
        Long available = parseBalanceLong(b.getAvailableBalance());
        Long reserved = parseBalanceLong(b.getReservedBalance());
        if (total != null && available != null && reserved != null) {
            assertThat(available).as("available_balance should equal total_balance - reserved_balance").isEqualTo(total - reserved);
        }
    }

    private static Long parseBalanceLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return (long) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
