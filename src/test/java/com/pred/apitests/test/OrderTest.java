package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
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
    private static final String SHORT_ORDER_PRICE = "70";

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
        String orderId = response.path("order_id");
        response.then().body("status", equalTo("open_order"))
                .body("order_id", notNullValue())
                .body("message", equalTo("Order placed successfully"))
                .body("filled_quantity", equalTo("0"));

        Response openOrdersRes = portfolioService.getOpenOrders(token(), cookie());
        assertThat(openOrdersRes.getStatusCode()).isEqualTo(200);
        assertThat(openOrdersRes.getBody().asString()).as("placed order should appear in open-orders").contains(orderId);
    }

    @Test(description = "Place order with side short returns 202; cleanup cancel")
    public void placeOrder_shortSide_returns202() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank() || tokenId == null || tokenId.isBlank()) throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("EOA or proxy not set");
        String salt = String.valueOf(System.currentTimeMillis());
        long timestampSec = System.currentTimeMillis() / 1000;
        SignOrderRequest signRequest = SignOrderRequest.builder()
                .salt(salt)
                .price(SHORT_ORDER_PRICE)
                .quantity(ORDER_QUANTITY)
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
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        double shortAmountVal = (100 - Long.parseLong(SHORT_ORDER_PRICE)) * Long.parseLong(ORDER_QUANTITY) / 100.0;
        String shortAmount = String.format("%.2f", shortAmountVal);
        PlaceOrderRequest orderBody = PlaceOrderRequest.builder()
                .salt(salt)
                .userId(userId)
                .marketId(marketId)
                .side("short")
                .tokenId(tokenId)
                .price(SHORT_ORDER_PRICE)
                .quantity(ORDER_QUANTITY)
                .amount(shortAmount)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        Response response = orderService.placeOrder(token(), cookie(), eoa, proxyWallet, marketId, orderBody);
        response.then().statusCode(202).body("status", equalTo("open_order"));
        String orderId = response.jsonPath().getString("order_id");
        if (orderId != null && !orderId.isBlank()) {
            Response cancelResponse = orderService.cancelOrder(token(), cookie(), marketId, orderId.trim());
            assertThat(cancelResponse.getStatusCode()).isBetween(200, 299);
        }
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
        assertThat(response.getStatusCode()).isGreaterThanOrEqualTo(400).isLessThan(500);
        Object err = response.path("error");
        Object msg = response.path("message");
        assertThat(err != null || msg != null).as("4xx response should have error or message").isTrue();
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
