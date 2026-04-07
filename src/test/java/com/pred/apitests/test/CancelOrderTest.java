package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.MarketContext;
import com.pred.apitests.util.PollingUtil;
import com.pred.apitests.util.SchemaValidator;
import com.pred.apitests.util.TokenManager;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * Cancel order tests: happy path, invalid id, trade-history and balance assertions.
 */
public class CancelOrderTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String ORDER_AMOUNT = "30";

    private OrderService orderService;
    private PortfolioService portfolioService;
    private SignatureService signatureService;
    private String marketId;
    private String parentMarketId;
    private String tokenId;
    private String eoa;
    private String proxyWallet;
    private String userId;

    private String token() {
        return getSession().getAccessToken();
    }
    private String cookie() {
        return getSession().getRefreshCookieHeaderValue();
    }
    private String privateKeyForSign() {
        UserSession s = getSession();
        if (s != null && s.getPrivateKey() != null && !s.getPrivateKey().isBlank()) return s.getPrivateKey();
        String k = Config.getPrivateKey();
        if (k != null && !k.isBlank()) return k;
        return System.getenv("PRIVATE_KEY");
    }

    /**
     * After a 401, refresh the account that {@link #getSession()} represents.
     * User 1: {@link AuthService#refreshIfExpiringSoon()} on TokenManager.
     * User 2: {@link AuthService#refreshSecondUserAndStore()} (reloads from .env.session2 after clear).
     * <p>Detection: if session access token equals TokenManager's token, treat as User 1; otherwise User 2.
     * A blind "try User 1 then User 2" would refresh the wrong user when User 1 gets 401 but is not "expiring soon" by clock.
     */
    private boolean refreshCurrentUser() {
        AuthService auth = new AuthService();
        UserSession session = getSession();
        if (session == null || session.getAccessToken() == null || session.getAccessToken().isBlank()) {
            return false;
        }
        String tmToken = TokenManager.getInstance().getAccessToken();
        if (tmToken != null && tmToken.equals(session.getAccessToken())) {
            return auth.refreshIfExpiringSoon();
        }
        return auth.refreshSecondUserAndStore();
    }

    /** Re-read eoa / proxy / userId from {@link #getSession()} after token refresh (place order headers + body). */
    private void syncPlaceOrderContextFromSession() {
        UserSession s = getSession();
        if (s == null) return;
        if (s.getEoa() != null && !s.getEoa().isBlank()) {
            eoa = s.getEoa();
        }
        if (eoa == null || eoa.isBlank()) {
            eoa = Config.getEoaAddress();
        }
        if (s.getProxy() != null && !s.getProxy().isBlank()) {
            proxyWallet = s.getProxy();
        }
        if (s.getUserId() != null && !s.getUserId().isBlank()) {
            userId = s.getUserId();
        }
    }

    @BeforeClass
    public void init() {
        UserSession s = getSession();
        if (s == null || !s.hasToken()) {
            throw new SkipException("No session - run AuthFlowTest first (and AuthFlowTestUser2 for user 2)");
        }
        orderService = new OrderService();
        portfolioService = new PortfolioService();
        signatureService = new SignatureService();
        try {
            MarketContext.getInstance().init();
        } catch (Exception e) {
            System.out.println("MarketContext init skipped: " + e.getMessage());
        }
        marketId = MarketContext.resolveMarketId();
        parentMarketId = MarketContext.resolveParentMarketIdForPath();
        tokenId = Config.getTokenId();
        eoa = s.getEoa();
        if (eoa == null || eoa.isBlank()) eoa = Config.getEoaAddress();
        proxyWallet = s.getProxy();
        userId = s.getUserId();
    }

    @Test(description = "Cancel order with valid order_id and market_id returns 2xx")
    public void cancelOrder_validOrderId_accepted() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        String orderId = placeLimitOrderAndReturnOrderId();
        assertThat(orderId).as("place order should return order_id").isNotBlank();

        Response response = orderService.cancelOrder(token(), cookie(), parentMarketId, orderId);
        if (response.getStatusCode() == 401) {
            refreshCurrentUser();
            response = orderService.cancelOrder(token(), cookie(), parentMarketId, orderId);
        }
        assertThat(response.getStatusCode()).as("cancel order response").isBetween(200, 299);
        response.then().assertThat().body(matchesJsonSchemaInClasspath("schemas/cancel-order-response.json"));
        response.then().body("status", equalTo("user_cancelled"))
                .body("order_id", equalTo(orderId))
                .body("message", equalTo("Order cancellation submitted successfully"));
        String body = response.getBody().asString();
        assertThat(body).as("cancel response body").contains("user_cancelled").contains("order_id");
    }

    @Test(description = "Cancel order with non-existent order id returns 4xx")
    public void cancelOrder_invalidOrderId_rejected() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");

        Response response = orderService.cancelOrder(token(), cookie(), parentMarketId, "non-existent-order-id-000");
        response.then().statusCode(greaterThanOrEqualTo(400))
                .statusCode(lessThan(500));
        String raw = response.getBody().asString();
        if (response.getContentType() != null && response.getContentType().toLowerCase().contains("json")) {
            assertThat(response.path("error") != null || response.path("message") != null).isTrue();
        } else {
            assertThat(raw != null && !raw.isBlank()).as("non-JSON error body").isTrue();
        }
    }

    @Test(description = "Cancelled unmatched limit order does not appear in trade-history")
    public void cancelLimitOrder_notInTradeHistory() {
        if (marketId == null || marketId.isBlank() || tokenId == null || tokenId.isBlank()) throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("EOA or proxy not set");

        Response historyBefore = portfolioService.getTradeHistory(token(), cookie());
        if (historyBefore.getStatusCode() == 401) {
            refreshCurrentUser();
            historyBefore = portfolioService.getTradeHistory(token(), cookie());
        }
        assertThat(historyBefore.getStatusCode()).isEqualTo(200);
        SchemaValidator.assertMatchesSchema(historyBefore, "trade-history-response.json");
        List<?> listBefore = getTradeHistoryList(historyBefore);
        int countBefore = listBefore != null ? listBefore.size() : 0;

        String orderId = placeLimitOrderAndReturnOrderId();
        assertThat(orderId).isNotBlank();
        Response cancelRes = orderService.cancelOrder(token(), cookie(), parentMarketId, orderId);
        if (cancelRes.getStatusCode() == 401) {
            refreshCurrentUser();
            cancelRes = orderService.cancelOrder(token(), cookie(), parentMarketId, orderId);
        }
        assertThat(cancelRes.getStatusCode()).isBetween(200, 299);

        Response historyAfter = portfolioService.getTradeHistory(token(), cookie());
        if (historyAfter.getStatusCode() == 401) {
            refreshCurrentUser();
            historyAfter = portfolioService.getTradeHistory(token(), cookie());
        }
        assertThat(historyAfter.getStatusCode()).isEqualTo(200);
        SchemaValidator.assertMatchesSchema(historyAfter, "trade-history-response.json");
        List<?> listAfter = getTradeHistoryList(historyAfter);
        int countAfter = listAfter != null ? listAfter.size() : 0;
        assertThat(countAfter).as("cancelled unmatched limit order should not add trade-history entry").isEqualTo(countBefore);

        if (listAfter != null) {
            for (Object item : listAfter) {
                if (item instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> entry = (java.util.Map<String, Object>) item;
                    Object oid = entry.get("order_id");
                    if (oid != null && orderId.equals(String.valueOf(oid).trim())) {
                        throw new AssertionError("Trade history must not contain cancelled order_id " + orderId);
                    }
                }
            }
        }
    }

    @Test(description = "After cancelling limit order, usdc_balance restored exactly (to the cent)")
    public void cancelLimitOrder_balanceFullyRestored() {
        if (marketId == null || marketId.isBlank() || tokenId == null || tokenId.isBlank()) throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("EOA or proxy not set");

        Response balanceBefore = portfolioService.getBalance(token(), cookie());
        if (balanceBefore.getStatusCode() == 401) {
            refreshCurrentUser();
            balanceBefore = portfolioService.getBalance(token(), cookie());
        }
        assertThat(balanceBefore.getStatusCode()).isEqualTo(200);
        SchemaValidator.assertMatchesSchema(balanceBefore, "balance-response.json");
        String usdcBeforeStr = balanceBefore.path("usdc_balance");
        assertThat(usdcBeforeStr).isNotNull();
        BigDecimal balanceBeforeVal = new BigDecimal(String.valueOf(usdcBeforeStr).trim());

        String orderId = placeLimitOrderAndReturnOrderId();
        assertThat(orderId).isNotBlank();
        Response cancelRes = orderService.cancelOrder(token(), cookie(), parentMarketId, orderId);
        if (cancelRes.getStatusCode() == 401) {
            refreshCurrentUser();
            cancelRes = orderService.cancelOrder(token(), cookie(), parentMarketId, orderId);
        }
        assertThat(cancelRes.getStatusCode()).isBetween(200, 299);

        PollingUtil.pollUntil(6_000, 300, 500,
                "Balance did not restore after cancel within 6s",
                () -> {
                    Response r = portfolioService.getBalance(token(), cookie());
                    if (r.getStatusCode() == 401) {
                        refreshCurrentUser();
                        r = portfolioService.getBalance(token(), cookie());
                    }
                    if (r.getStatusCode() != 200) return false;
                    String usdcStr = r.path("usdc_balance");
                    if (usdcStr == null) return false;
                    BigDecimal current = new BigDecimal(String.valueOf(usdcStr).trim());
                    return current.compareTo(balanceBeforeVal) == 0;
                });

        Response balanceAfter = portfolioService.getBalance(token(), cookie());
        if (balanceAfter.getStatusCode() == 401) {
            refreshCurrentUser();
            balanceAfter = portfolioService.getBalance(token(), cookie());
        }
        assertThat(balanceAfter.getStatusCode()).isEqualTo(200);
        SchemaValidator.assertMatchesSchema(balanceAfter, "balance-response.json");
        String usdcAfterStr = balanceAfter.path("usdc_balance");
        assertThat(usdcAfterStr).isNotNull();
        BigDecimal balanceAfterVal = new BigDecimal(String.valueOf(usdcAfterStr).trim());

        assertThat(balanceAfterVal.compareTo(balanceBeforeVal)).as("usdc_balance after cancel must equal usdc_balance before place (exact)").isEqualTo(0);
    }

    private String placeLimitOrderAndReturnOrderId() {
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
        String keyForSign = privateKeyForSign();
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

        Response response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, parentMarketId, orderBody);
        if (response.getStatusCode() == 401) {
            refreshCurrentUser();
            syncPlaceOrderContextFromSession();
            response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, parentMarketId, orderBody);
        }
        assertThat(response.getStatusCode()).as("place order").isEqualTo(202);
        response.then().body("status", equalTo("open_order")).body("order_id", notNullValue());
        String orderId = response.jsonPath().getString("order_id");
        if (orderId == null || orderId.isBlank()) orderId = response.jsonPath().getString("data.order_id");
        return orderId != null ? orderId.trim() : "";
    }
}
