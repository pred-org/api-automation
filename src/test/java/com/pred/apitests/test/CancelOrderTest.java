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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

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
    private String tokenId;
    private String eoa;
    private String proxyWallet;
    private String userId;

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
        orderService = new OrderService();
        portfolioService = new PortfolioService();
        signatureService = new SignatureService();
        marketId = Config.getMarketId();
        tokenId = Config.getTokenId();
        eoa = TokenManager.getInstance().getEoa();
        if (eoa == null || eoa.isBlank()) eoa = Config.getEoaAddress();
        proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        userId = TokenManager.getInstance().getUserId();
    }

    @Test(description = "Cancel order with valid order_id and market_id returns 2xx")
    public void cancelOrder_withValidOrderId_returns2xx() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        String orderId = placeLimitOrderAndReturnOrderId();
        assertThat(orderId).as("place order should return order_id").isNotBlank();

        Response response = orderService.cancelOrder(token(), cookie(), marketId, orderId);
        assertThat(response.getStatusCode()).as("cancel order response").isBetween(200, 299);
        response.then().body("status", equalTo("user_cancelled"))
                .body("order_id", equalTo(orderId))
                .body("message", equalTo("Order cancellation submitted successfully"));
        String body = response.getBody().asString();
        assertThat(body).as("cancel response body").contains("user_cancelled").contains("order_id");
    }

    @Test(description = "Cancel order with non-existent order id returns 4xx")
    public void cancelOrder_withInvalidOrderId_returns4xx() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");

        Response response = orderService.cancelOrder(token(), cookie(), marketId, "non-existent-order-id-000");
        response.then().statusCode(greaterThanOrEqualTo(400))
                .statusCode(lessThan(500));
        assertThat(response.path("error") != null || response.path("message") != null).isTrue();
    }

    @Test(description = "Cancelled unmatched limit order does not appear in trade-history")
    public void cancelOrder_limitOrder_doesNotAppearInTradeHistory() {
        if (marketId == null || marketId.isBlank() || tokenId == null || tokenId.isBlank()) throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("EOA or proxy not set");

        Response historyBefore = portfolioService.getTradeHistory(token(), cookie());
        assertThat(historyBefore.getStatusCode()).isEqualTo(200);
        List<?> listBefore = historyBefore.path("data");
        int countBefore = listBefore != null ? listBefore.size() : 0;

        String orderId = placeLimitOrderAndReturnOrderId();
        assertThat(orderId).isNotBlank();
        Response cancelRes = orderService.cancelOrder(token(), cookie(), marketId, orderId);
        assertThat(cancelRes.getStatusCode()).isBetween(200, 299);

        Response historyAfter = portfolioService.getTradeHistory(token(), cookie());
        assertThat(historyAfter.getStatusCode()).isEqualTo(200);
        List<?> listAfter = historyAfter.path("data");
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
    public void cancelOrder_limitOrder_balanceFullyRestored() {
        if (marketId == null || marketId.isBlank() || tokenId == null || tokenId.isBlank()) throw new SkipException("MARKET_ID or TOKEN_ID not set");
        if (eoa == null || eoa.isBlank() || proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("EOA or proxy not set");

        Response balanceBefore = portfolioService.getBalance(token(), cookie());
        assertThat(balanceBefore.getStatusCode()).isEqualTo(200);
        String usdcBeforeStr = balanceBefore.path("usdc_balance");
        assertThat(usdcBeforeStr).isNotNull();
        BigDecimal balanceBeforeVal = new BigDecimal(String.valueOf(usdcBeforeStr).trim());

        String orderId = placeLimitOrderAndReturnOrderId();
        assertThat(orderId).isNotBlank();
        Response cancelRes = orderService.cancelOrder(token(), cookie(), marketId, orderId);
        assertThat(cancelRes.getStatusCode()).isBetween(200, 299);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        Response balanceAfter = portfolioService.getBalance(token(), cookie());
        assertThat(balanceAfter.getStatusCode()).isEqualTo(200);
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
}
