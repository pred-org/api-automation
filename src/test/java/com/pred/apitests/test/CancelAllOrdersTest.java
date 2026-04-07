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
import com.pred.apitests.util.TokenManager;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Validates "Cancel All" endpoint: DELETE /api/v1/order/{marketId}/cancel/all.
 * singleThreaded: avoids cross-method races when suite uses parallel execution elsewhere.
 */
@Test(singleThreaded = true)
public class CancelAllOrdersTest extends BaseApiTest {

    private static final String ORDER_QUANTITY = "100";
    private static final String DEFAULT_ORDER_PRICE = "30";

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
     * User 1: {@link AuthService#refreshUser1SessionAfter401()} (cookie refresh + login if needed).
     * User 2: {@link AuthService#refreshSecondUserAndStore()} (reloads from .env.session2 after clear).
     * <p>Detection: if session access token equals TokenManager's token, treat as User 1; otherwise User 2.
     */
    private boolean refreshCurrentUser() {
        AuthService auth = new AuthService();
        UserSession session = getSession();
        if (session == null || session.getAccessToken() == null || session.getAccessToken().isBlank()) {
            return false;
        }
        String tmToken = TokenManager.getInstance().getAccessToken();
        if (tmToken != null && tmToken.equals(session.getAccessToken())) {
            return auth.refreshUser1SessionAfter401();
        }
        return auth.refreshSecondUserAndStore();
    }

    /** Re-read eoa / proxy / userId from {@link #getSession()} after token refresh. */
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

    private void validateInit() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");
    }

    @BeforeClass
    public void init() {
        UserSession s = getSession();
        if (s == null || !s.hasToken()) {
            throw new SkipException("No session - run AuthFlowTest first");
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

    private Response cancelAllOrdersWith401Retry() {
        Response resp = orderService.cancelAllOrders(token(), cookie(), parentMarketId, 30_000);
        if (resp.getStatusCode() == 401) {
            refreshCurrentUser();
            syncPlaceOrderContextFromSession();
            resp = orderService.cancelAllOrders(token(), cookie(), parentMarketId, 30_000);
        }
        return resp;
    }

    private Response placeLimitLongOrderAndReturnOrderId(String price) {
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;

        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(price)
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
                .price(price)
                .quantity(ORDER_QUANTITY)
                .amount(price)
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
        return response;
    }

    private String placeLimitOrderAndReturnOrderId(String price) {
        Response response = placeLimitLongOrderAndReturnOrderId(price);
        assertThat(response.getStatusCode()).as("place order @ price=" + price).isEqualTo(202);
        response.then().body("status", equalTo("open_order")).body("order_id", notNullValue());
        String orderId = response.jsonPath().getString("order_id");
        if (orderId == null || orderId.isBlank()) orderId = response.jsonPath().getString("data.order_id");
        return orderId != null ? orderId.trim() : "";
    }

    private List<Map<String, Object>> getOpenOrdersForMarket() {
        Response r = portfolioService.getOpenOrders(token(), cookie());
        if (r.getStatusCode() == 401) {
            refreshCurrentUser();
            syncPlaceOrderContextFromSession();
            r = portfolioService.getOpenOrders(token(), cookie());
        }
        if (r.getStatusCode() != 200) return List.of();

        Object listObj = r.path("data");
        if (listObj == null) listObj = r.path("open_orders");
        if (listObj == null) listObj = r.path("data.open_orders");
        if (listObj == null) listObj = r.path("data.data");
        if (listObj == null) listObj = r.path("orders");
        if (listObj == null) listObj = r.path("data.orders");
        if (listObj == null) return List.of();
        if (!(listObj instanceof List)) return List.of();

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) listObj;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) item;
            if (openOrderEntryMatchesMarket(entry)) {
                out.add(entry);
            }
        }
        return out;
    }

    private static String normId(Object o) {
        return o == null ? "" : String.valueOf(o).trim().toLowerCase();
    }

    /**
     * Open-order rows may use market_id, marketId, or nested market.{id|market_id}.
     */
    private boolean openOrderEntryMatchesMarket(Map<String, Object> entry) {
        String mid = normId(entry.get("market_id"));
        if (mid.isEmpty()) {
            mid = normId(entry.get("marketId"));
        }
        if (mid.isEmpty() && entry.get("market") instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) entry.get("market");
            Object v = m.get("market_id");
            if (v == null) {
                v = m.get("id");
            }
            if (v == null) {
                v = m.get("marketId");
            }
            mid = normId(v);
        }
        String parentMid = normId(entry.get("parent_market_id"));
        if (parentMid.isEmpty()) {
            parentMid = normId(entry.get("parentMarketId"));
        }
        String wantSub = normId(marketId);
        String wantPar = normId(parentMarketId);
        if (!wantSub.isEmpty() && (wantSub.equals(mid) || wantSub.equals(parentMid))) {
            return true;
        }
        return !wantPar.isEmpty() && (wantPar.equals(mid) || wantPar.equals(parentMid));
    }

    private int getOpenOrdersForMarketSize() {
        return getOpenOrdersForMarket().size();
    }

    @Test(description = "cancelAllOrders: returns success (2xx) when a user has an open order")
    public void cancelAllOrders_returnsSuccess() {
        validateInit();
        String orderId = placeLimitOrderAndReturnOrderId(DEFAULT_ORDER_PRICE);
        assertThat(orderId).isNotBlank();

        Response resp = cancelAllOrdersWith401Retry();
        assertThat(resp.getStatusCode()).as("cancelAllOrders response").isBetween(200, 299);
        System.out.println("cancelAllOrders response body:\n" + resp.getBody().asPrettyString());
    }

    @Test(description = "cancelAllOrders clears all open orders for the market")
    public void cancelAllOrders_clearsAllOpenOrders() {
        validateInit();
        // Ensure a clean baseline for this market
        cancelAllOrdersWith401Retry();

        // Use very low prices to reduce the chance of immediate matching.
        placeLimitOrderAndReturnOrderId("3");
        placeLimitOrderAndReturnOrderId("4");
        placeLimitOrderAndReturnOrderId("5");

        System.out.println("DEBUG open-orders response (pre-poll):");
        Response debugResp = portfolioService.getOpenOrders(token(), cookie());
        System.out.println(debugResp.getBody().asPrettyString());
        System.out.println("DEBUG market-filtered count (pre-poll): " + getOpenOrdersForMarketSize());

        final int[] lastMarketOpenCount = new int[1];
        PollingUtil.pollUntil(30_000, 500, 1000,
                "Open orders were not visible for the market within 30s (Kafka / open-orders index)",
                () -> {
                    lastMarketOpenCount[0] = getOpenOrdersForMarketSize();
                    return lastMarketOpenCount[0] >= 3;
                });

        int marketOrderCount = getOpenOrdersForMarketSize();
        Response debugResp2 = portfolioService.getOpenOrders(token(), cookie());
        if (debugResp2.getStatusCode() == 401) {
            refreshCurrentUser();
            syncPlaceOrderContextFromSession();
            debugResp2 = portfolioService.getOpenOrders(token(), cookie());
        }
        String rawBody = debugResp2.getBody() != null ? debugResp2.getBody().asString() : "";
        if (rawBody.length() > 12_000) {
            rawBody = rawBody.substring(0, 12_000) + "... (truncated)";
        }
        assertThat(marketOrderCount)
                .as("open orders before cancel (market-specific). http=%s lastPollCount=%s. Raw: %s",
                        debugResp2.getStatusCode(), lastMarketOpenCount[0], rawBody)
                .isGreaterThanOrEqualTo(3);

        Response resp = cancelAllOrdersWith401Retry();
        assertThat(resp.getStatusCode()).as("cancelAllOrders response").isBetween(200, 299);

        PollingUtil.pollUntil(30_000, 500, 1000,
                "Open orders for market were not cleared after cancel within 30s",
                () -> getOpenOrdersForMarketSize() == 0);

        assertThat(getOpenOrdersForMarketSize()).as("open orders for market after cancel").isEqualTo(0);
    }

    @Test(description = "cancelAllOrders fully restores usdc_balance after canceling reserved orders")
    public void cancelAllOrders_balanceFullyRestored() {
        validateInit();
        cancelAllOrdersWith401Retry();

        Response balanceBeforeRes = portfolioService.getBalance(token(), cookie());
        if (balanceBeforeRes.getStatusCode() == 401) {
            refreshCurrentUser();
            syncPlaceOrderContextFromSession();
            balanceBeforeRes = portfolioService.getBalance(token(), cookie());
        }
        assertThat(balanceBeforeRes.getStatusCode()).isEqualTo(200);
        String usdcBeforeStr = balanceBeforeRes.path("usdc_balance");
        assertThat(usdcBeforeStr).isNotNull();
        BigDecimal balanceBefore = new BigDecimal(String.valueOf(usdcBeforeStr).trim());

        placeLimitOrderAndReturnOrderId("28");
        placeLimitOrderAndReturnOrderId("30");

        Response resp = cancelAllOrdersWith401Retry();
        assertThat(resp.getStatusCode()).as("cancelAllOrders response").isBetween(200, 299);

        PollingUtil.pollUntil(8_000, 300, 500,
                "Balance did not restore after cancel within 8s",
                () -> {
                    Response r = portfolioService.getBalance(token(), cookie());
                    if (r.getStatusCode() == 401) {
                        refreshCurrentUser();
                        syncPlaceOrderContextFromSession();
                        r = portfolioService.getBalance(token(), cookie());
                    }
                    if (r.getStatusCode() != 200) return false;
                    String usdcStr = r.path("usdc_balance");
                    if (usdcStr == null) return false;
                    BigDecimal current = new BigDecimal(String.valueOf(usdcStr).trim());
                    return current.compareTo(balanceBefore) == 0;
                });

        Response balanceAfterRes = portfolioService.getBalance(token(), cookie());
        if (balanceAfterRes.getStatusCode() == 401) {
            refreshCurrentUser();
            syncPlaceOrderContextFromSession();
            balanceAfterRes = portfolioService.getBalance(token(), cookie());
        }
        assertThat(balanceAfterRes.getStatusCode()).isEqualTo(200);
        String usdcAfterStr = balanceAfterRes.path("usdc_balance");
        assertThat(usdcAfterStr).isNotNull();
        BigDecimal balanceAfter = new BigDecimal(String.valueOf(usdcAfterStr).trim());

        assertThat(balanceAfter.compareTo(balanceBefore)).as("balance after cancel must restore exactly").isEqualTo(0);
    }

    @Test(description = "cancelAllOrders returns success even when there are no open orders")
    public void cancelAllOrders_withNoOpenOrders_returnsSuccess() {
        validateInit();

        // Cleanup and assert empty for this market
        cancelAllOrdersWith401Retry();
        PollingUtil.pollUntil(10_000, 500, 1000,
                "Open orders for market were not empty after cleanup within 10s",
                () -> getOpenOrdersForMarketSize() == 0);
        assertThat(getOpenOrdersForMarketSize()).isEqualTo(0);

        Response resp = cancelAllOrdersWith401Retry();
        assertThat(resp.getStatusCode()).as("cancelAllOrders response on empty state").isBetween(200, 299);

        assertThat(getOpenOrdersForMarketSize()).as("open orders for market should remain empty").isEqualTo(0);
    }
}

