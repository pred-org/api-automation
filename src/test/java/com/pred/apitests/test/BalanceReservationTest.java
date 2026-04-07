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
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Validates balance reservation: scoped {@code available_balance} drops when a limit order reserves funds;
 * global {@code usdc_balance} unchanged; cancel restores scoped to match global.
 */
public class BalanceReservationTest extends BaseApiTest {

    private static final String ORDER_PRICE = "10";
    private static final String ORDER_QUANTITY = "100";
    /** LONG: amount = price * quantity / 100 */
    private static final String ORDER_AMOUNT = "10.00";

    private OrderService orderService;
    private PortfolioService portfolioService;
    private SignatureService signatureService;
    private AuthService authService;
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
        if (s != null && s.getPrivateKey() != null && !s.getPrivateKey().isBlank()) {
            return s.getPrivateKey();
        }
        String k = Config.getPrivateKey();
        if (k != null && !k.isBlank()) {
            return k;
        }
        return System.getenv("PRIVATE_KEY");
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
        authService = new AuthService();
        try {
            MarketContext.getInstance().init();
        } catch (Exception e) {
            System.out.println("MarketContext init skipped: " + e.getMessage());
        }
        marketId = MarketContext.resolveMarketId();
        parentMarketId = MarketContext.resolveParentMarketIdForPath();
        tokenId = Config.getTokenId();
        eoa = s.getEoa();
        if (eoa == null || eoa.isBlank()) {
            eoa = Config.getEoaAddress();
        }
        proxyWallet = s.getProxy();
        userId = s.getUserId();
    }

    @Test(priority = 1, description = "Scoped available_balance drops on limit order reservation; global usdc unchanged; cancel restores scoped")
    public void balanceReservation_scopedReducedOnOrder_restoredOnCancel() {
        if (marketId == null || marketId.isBlank() || tokenId == null || tokenId.isBlank()) {
            throw new SkipException("MARKET_ID or TOKEN_ID not set");
        }
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) {
            throw new SkipException("EOA or proxy not set");
        }

        Response balanceResp = portfolioService.getBalance(token(), cookie());
        if (balanceResp.getStatusCode() == 401) {
            authService.refreshIfExpiringSoon();
            balanceResp = portfolioService.getBalance(token(), cookie());
        }
        assertThat(balanceResp.getStatusCode()).as("global balance GET").isEqualTo(200);
        String usdcStr = balanceResp.path("usdc_balance");
        if (usdcStr == null) {
            throw new SkipException("Global balance response missing usdc_balance");
        }
        BigDecimal globalBalance = new BigDecimal(String.valueOf(usdcStr).trim());

        Response scopedResp = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        if (scopedResp.getStatusCode() == 401) {
            authService.refreshIfExpiringSoon();
            scopedResp = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        }
        assertThat(scopedResp.getStatusCode()).as("scoped balance GET").isEqualTo(200);
        String availStr = scopedResp.path("available_balance");
        if (availStr == null) {
            throw new SkipException("Scoped balance response missing available_balance; cannot validate reservation");
        }
        BigDecimal scopedAvailable = new BigDecimal(String.valueOf(availStr).trim());

        Assert.assertEquals(scopedAvailable.compareTo(globalBalance), 0,
                "Scoped should equal global before any orders");

        String orderId = placeLimitLong10AndReturnOrderId();
        assertThat(orderId).as("place order should return order_id").isNotBlank();

        PollingUtil.pollUntil(6_000, 300, 500,
                "Scoped balance did not reduce after order placement within 6s",
                () -> {
                    Response r = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
                    if (r.getStatusCode() == 401) {
                        authService.refreshIfExpiringSoon();
                        r = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
                    }
                    if (r.getStatusCode() != 200) {
                        return false;
                    }
                    String avail = r.path("available_balance");
                    if (avail == null) {
                        return false;
                    }
                    BigDecimal current = new BigDecimal(String.valueOf(avail).trim());
                    return current.compareTo(globalBalance) < 0;
                });

        Response scopedAfterOrderResp = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        if (scopedAfterOrderResp.getStatusCode() == 401) {
            authService.refreshIfExpiringSoon();
            scopedAfterOrderResp = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        }
        assertThat(scopedAfterOrderResp.getStatusCode()).isEqualTo(200);
        String availAfterStr = scopedAfterOrderResp.path("available_balance");
        assertThat(availAfterStr).as("available_balance after order").isNotNull();
        BigDecimal scopedAfterOrder = new BigDecimal(String.valueOf(availAfterStr).trim());

        Response globalAfterPlace = portfolioService.getBalance(token(), cookie());
        if (globalAfterPlace.getStatusCode() == 401) {
            authService.refreshIfExpiringSoon();
            globalAfterPlace = portfolioService.getBalance(token(), cookie());
        }
        assertThat(globalAfterPlace.getStatusCode()).isEqualTo(200);
        String globalAfterStr = globalAfterPlace.path("usdc_balance");
        assertThat(globalAfterStr).isNotNull();
        BigDecimal globalAfterOrder = new BigDecimal(String.valueOf(globalAfterStr).trim());
        Assert.assertEquals(globalAfterOrder.compareTo(globalBalance), 0,
                "Global usdc_balance should not change when funds are reserved");

        BigDecimal reservedDelta = globalBalance.subtract(scopedAfterOrder);
        Assert.assertTrue(reservedDelta.compareTo(BigDecimal.ZERO) > 0,
                "globalBalance - scopedAfterOrder must be > 0 when order is open");

        Assert.assertTrue(scopedAfterOrder.compareTo(globalBalance) < 0,
                "Scoped must be less than global when order is open");

        Response cancelResp = orderService.cancelOrder(token(), cookie(), marketId, orderId);
        Assert.assertEquals(cancelResp.getStatusCode(), 200);

        PollingUtil.pollUntil(6_000, 300, 500,
                "Scoped available_balance did not restore to global within 6s after cancel",
                () -> {
                    Response r = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
                    if (r.getStatusCode() == 401) {
                        authService.refreshIfExpiringSoon();
                        r = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
                    }
                    if (r.getStatusCode() != 200) {
                        return false;
                    }
                    String avail = r.path("available_balance");
                    if (avail == null) {
                        return false;
                    }
                    BigDecimal current = new BigDecimal(String.valueOf(avail).trim());
                    return current.compareTo(globalBalance) == 0;
                });

        Response scopedFinalResp = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        if (scopedFinalResp.getStatusCode() == 401) {
            authService.refreshIfExpiringSoon();
            scopedFinalResp = portfolioService.getBalanceByMarket(token(), cookie(), marketId);
        }
        assertThat(scopedFinalResp.getStatusCode()).isEqualTo(200);
        String scopedFinalStr = scopedFinalResp.path("available_balance");
        assertThat(scopedFinalStr).isNotNull();
        BigDecimal scopedAfterCancel = new BigDecimal(String.valueOf(scopedFinalStr).trim());

        Assert.assertEquals(scopedAfterCancel.compareTo(globalBalance), 0,
                "Scoped must restore to global after cancel");
    }

    private String placeLimitLong10AndReturnOrderId() {
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
        if (keyForSign != null && !keyForSign.isBlank()) {
            signRequest.setPrivateKey(keyForSign);
        }

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
        assertThat(response.getStatusCode()).as("place order").isEqualTo(202);
        response.then().body("status", equalTo("open_order")).body("order_id", notNullValue());
        String oid = response.jsonPath().getString("order_id");
        if (oid == null || oid.isBlank()) {
            oid = response.jsonPath().getString("data.order_id");
        }
        return oid != null ? oid.trim() : "";
    }
}
