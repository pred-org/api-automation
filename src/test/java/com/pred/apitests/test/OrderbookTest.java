package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.MarketContext;
import com.pred.apitests.util.PollingUtil;
import com.pred.apitests.util.SchemaValidator;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Orderbook (public) API tests. Guard tests for SHORT market order pre-conditions.
 */
public class OrderbookTest extends BaseApiTest {

    private OrderService orderService;
    private SignatureService signatureService;
    private String marketId;
    private String parentMarketId;
    private String tokenId;
    private String token;
    private String cookie;
    private String eoa;
    private String proxyWallet;
    private String userId;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        orderService = new OrderService();
        signatureService = new SignatureService();
        try {
            MarketContext.getInstance().init();
        } catch (Exception e) {
            System.out.println("MarketContext init skipped: " + e.getMessage());
        }
        marketId = MarketContext.resolveMarketId();
        parentMarketId = MarketContext.resolveParentMarketIdForPath();
        tokenId = Config.getTokenId();
        token = TokenManager.getInstance().getAccessToken();
        cookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
        eoa = TokenManager.getInstance().getEoa();
        if (eoa == null || eoa.isBlank()) eoa = Config.getEoaAddress();
        proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        userId = TokenManager.getInstance().getUserId();
    }

    @Test(description = "GET orderbook returns 200; structure has bids, asks, metadata; metadata.spread present (public, no auth)")
    public void orderbook_returnsValidStructure() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        Response response = orderService.getOrderbook(parentMarketId, marketId);
        assertThat(response.getStatusCode()).isEqualTo(200);
        SchemaValidator.assertMatchesSchema(response, "orderbook-response.json");
        response.then().body("bids", notNullValue()).body("asks", notNullValue()).body("metadata", notNullValue());
        response.then().body("metadata.spread", notNullValue());
        List<?> bids = response.path("bids");
        List<?> asks = response.path("asks");
        assertThat(bids).isNotNull();
        assertThat(asks).isNotNull();
    }

    @Test(description = "Guard: bids exist and bids[0].quantity > 0; no bid liquidity means SHORT tests cannot run")
    public void orderbook_hasBidsBeforeShortOrder() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        Response response = orderService.getOrderbook(parentMarketId, marketId);
        assertThat(response.getStatusCode()).isEqualTo(200);
        List<?> bids = response.path("bids");
        if (bids == null || bids.isEmpty()) {
            throw new SkipException("No bid liquidity in UAT for this market — SHORT orderbook tests need resting bids");
        }
        Object first = bids.get(0);
        assertThat(first).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> bid = (Map<String, Object>) first;
        Object q = bid.get("quantity");
        assertThat(q).as("bids[0].quantity must be present").isNotNull();
        long qty = (long) Double.parseDouble(String.valueOf(q).trim());
        assertThat(qty).as("No bid liquidity in UAT - SHORT tests cannot run").isGreaterThan(0);
    }

    @Test(description = "total_bid_quantity decreases by N after placing SHORT market order for N shares")
    public void orderbook_bidQuantityDecreasesAfterShort() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank() || eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) {
            throw new SkipException("TOKEN_ID, EOA or proxy not set");
        }
        Response beforeRes = orderService.getOrderbook(parentMarketId, marketId);
        assertThat(beforeRes.getStatusCode()).isEqualTo(200);
        List<?> bids = beforeRes.path("bids");
        if (bids == null || bids.isEmpty()) throw new SkipException("No bid liquidity in UAT - SHORT tests cannot run");
        Object totalBidBeforeObj = beforeRes.path("metadata.total_bid_quantity");
        if (totalBidBeforeObj == null) totalBidBeforeObj = beforeRes.path("total_bid_quantity");
        if (totalBidBeforeObj == null) throw new SkipException("Orderbook does not return total_bid_quantity");

        long totalBidBefore = (long) Double.parseDouble(String.valueOf(totalBidBeforeObj).trim());
        @SuppressWarnings("unchecked")
        Map<String, Object> bestBid = (Map<String, Object>) bids.get(0);
        String bestBidPrice = String.valueOf(bestBid.get("price")).trim();
        int nShares = 5;

        boolean placed = placeShortLimitOrderAtPrice(bestBidPrice, String.valueOf(nShares));
        if (!placed) {
            throw new SkipException("Could not place SHORT order");
        }

        final long bidBefore = totalBidBefore;
        final int expected = nShares;
        PollingUtil.pollUntil(60_000, 300, 1000,
                "total_bid_quantity did not decrease by " + nShares,
                () -> {
                    Response r = orderService.getOrderbook(parentMarketId, marketId);
                    if (r.getStatusCode() != 200) {
                        return false;
                    }
                    Object obj = r.path("metadata.total_bid_quantity");
                    if (obj == null) {
                        obj = r.path("total_bid_quantity");
                    }
                    if (obj == null) {
                        return false;
                    }
                    long after = (long) Double.parseDouble(String.valueOf(obj).trim());
                    return (bidBefore - after) == expected;
                });

        Response afterRes = orderService.getOrderbook(parentMarketId, marketId);
        assertThat(afterRes.getStatusCode()).isEqualTo(200);
        Object totalBidAfterObj = afterRes.path("metadata.total_bid_quantity");
        if (totalBidAfterObj == null) {
            totalBidAfterObj = afterRes.path("total_bid_quantity");
        }
        assertThat(totalBidAfterObj).isNotNull();
        long totalBidAfter = (long) Double.parseDouble(String.valueOf(totalBidAfterObj).trim());
        assertThat(totalBidBefore - totalBidAfter).as("total_bid_quantity should decrease by " + nShares + " after SHORT").isEqualTo(nShares);
    }

    private boolean placeShortLimitOrderAtPrice(String price, String quantity) {
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
        if (sigResponse == null || !sigResponse.isOk()) return false;

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

        Response response = orderService.placeOrder(token, cookie, eoa, proxyWallet, parentMarketId, orderBody);
        return response.getStatusCode() == 202;
    }
}
