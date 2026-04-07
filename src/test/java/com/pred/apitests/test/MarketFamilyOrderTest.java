package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.MarketContext;
import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Places one order per market family (moneyline, spreads, totals, btts)
 * using the canonical name from .env. NOT part of regular suite.xml.
 *
 * User 1 places LONG, User 2 places SHORT on each market family.
 * Orders are cancelled after verification.
 *
 * dependsOnGroups: must run after AuthFlowTest and AuthFlowTestUser2 so TokenManager and
 * SecondUserContext are populated (suite.xml parallel=methods can otherwise start this class first).
 */
@Test(dependsOnGroups = {"auth-complete", "auth-user2-complete"})
public class MarketFamilyOrderTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String SHORT_ORDER_PRICE = "70";

    private OrderService orderService;
    private SignatureService signatureService;
    private MarketContext marketContext;

    @BeforeClass
    public void init() {
        UserSession s = getSession();
        if (s == null || !s.hasToken()) {
            throw new SkipException("No session — run AuthFlowTest first");
        }
        UserSession s2 = SecondUserContext.getSecondUser();
        if (s2 == null || !s2.hasToken()) {
            throw new SkipException("No user 2 session — run AuthFlowTestUser2 first");
        }

        orderService = new OrderService();
        signatureService = new SignatureService();

        marketContext = MarketContext.getInstance();
        marketContext.init();
    }

    @Test(description = "Place order on moneyline market — user1 LONG, user2 SHORT")
    public void placeOrder_moneyline() {
        placeOrderForFamily("moneyline");
    }

    @Test(description = "Place order on spreads market — user1 LONG, user2 SHORT")
    public void placeOrder_spreads() {
        placeOrderForFamily("spreads");
    }

    @Test(description = "Place order on totals market — user1 LONG, user2 SHORT")
    public void placeOrder_totals() {
        placeOrderForFamily("totals");
    }

    @Test(description = "Place order on btts market — user1 LONG, user2 SHORT")
    public void placeOrder_btts() {
        placeOrderForFamily("btts");
    }

    /**
     * Core logic: for a given family, get the first parent market,
     * pick the first sub-market, sign + place LONG (user1) and SHORT (user2).
     * Cancel both orders after assertion.
     */
    private void placeOrderForFamily(String family) {
        if (!marketContext.hasFamilyWithMarkets(family)) {
            throw new SkipException("No active " + family + " market found for " + marketContext.getCanonicalName());
        }

        MarketContext.ParentMarket pm = marketContext.getFirstParentMarket(family);
        String parentMarketId = pm.getParentMarketId();
        MarketContext.SubMarket sub = pm.getSubMarkets().get(0);
        String subMarketId = sub.getMarketId();

        System.out.println("[" + family + "] parentMarketId=" + parentMarketId
                + " subMarketId=" + subMarketId + " name=" + sub.getName());

        UserSession user1 = getSession();
        // Path /api/v1/order/{marketId}/place must use the same outcome id as body.market_id and EIP-712 questionId (sub).
        String orderId1 = signAndPlace(user1, subMarketId, "long", ORDER_PRICE);
        assertThat(orderId1).as("User1 LONG order on " + family).isNotEmpty();
        System.out.println("[" + family + "] User1 LONG placed: " + orderId1);

        UserSession user2 = SecondUserContext.getSecondUser();
        String orderId2 = signAndPlace(user2, subMarketId, "short", SHORT_ORDER_PRICE);
        assertThat(orderId2).as("User2 SHORT order on " + family).isNotEmpty();
        System.out.println("[" + family + "] User2 SHORT placed: " + orderId2);

        cancelOrder(user1, subMarketId, orderId1);
        cancelOrder(user2, subMarketId, orderId2);
    }

    /**
     * Limit order amount per API: LONG = price * qty / 100, SHORT = (100 - price) * qty / 100 (see docs/API_DOCUMENTATION.md).
     */
    private static String limitOrderAmount(String side, String priceStr, String quantityStr) {
        double p = Double.parseDouble(priceStr);
        double q = Double.parseDouble(quantityStr);
        double raw = "short".equalsIgnoreCase(side) ? (100 - p) * q / 100.0 : p * q / 100.0;
        return String.format("%.2f", raw);
    }

    private String signAndPlace(UserSession session, String subMarketId, String side, String price) {
        String amount = limitOrderAmount(side, price, ORDER_QUANTITY);
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;
        int intent = "long".equals(side) ? 0 : 1;

        SignOrderRequest signReq = SignOrderRequest.builder()
                .salt(salt)
                .price(price)
                .quantity(ORDER_QUANTITY)
                .questionId(subMarketId)
                .timestamp(timestampSec)
                .feeRateBps(0)
                .intent(intent)
                .signatureType(2)
                .taker("0x0000000000000000000000000000000000000000")
                .expiration("0")
                .nonce("0")
                .maker(session.getProxy())
                .signer(session.getEoa())
                .priceInCents(false)
                .build();

        String pk = session.getPrivateKey();
        if (pk == null || pk.isBlank()) {
            UserSession u2 = SecondUserContext.getSecondUser();
            if (u2 != null && session.getUserId() != null && session.getUserId().equals(u2.getUserId())) {
                pk = Config.getSecondUserPrivateKey();
            } else {
                pk = Config.getPrivateKey();
            }
        }
        if (pk != null && !pk.isBlank()) {
            signReq.setPrivateKey(pk);
        }

        SignOrderResponse sigResp = signatureService.signOrder(Config.getSigServerUrl(), signReq);
        assertThat(sigResp).isNotNull();
        assertThat(sigResp.isOk()).as("Sig-server must return ok=true for " + subMarketId).isTrue();

        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(session.getUserId())
                .marketId(subMarketId)
                .side(side)
                .tokenId(Config.getTokenId())
                .price(price)
                .quantity(ORDER_QUANTITY)
                .amount(amount)
                .isLowPriority(false)
                .signature(sigResp.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();

        String cookie = session.getRefreshCookieHeaderValue();
        Response resp = orderService.placeOrder(
                session.getAccessToken(), cookie, session.getEoa(), session.getProxy(),
                subMarketId, orderBody);

        if (resp.getStatusCode() != 202) {
            System.out.println("FAIL [" + side + "] status=" + resp.getStatusCode()
                    + " body=" + resp.getBody().asString());
        }
        assertThat(resp.getStatusCode()).as("Place " + side + " order").isEqualTo(202);

        String orderId = resp.jsonPath().getString("order_id");
        return orderId != null ? orderId.trim() : "";
    }

    private void cancelOrder(UserSession session, String subMarketId, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        String cookie = session.getRefreshCookieHeaderValue();
        orderService.cancelOrder(session.getAccessToken(), cookie, subMarketId, orderId);
    }
}
