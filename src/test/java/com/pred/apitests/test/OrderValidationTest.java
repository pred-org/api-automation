package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Order validation tests. placeOrder_shortSide_returns202 uses intent=1 for sig-server (short side).
 */
public class OrderValidationTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String SHORT_ORDER_PRICE = "70";

    private OrderService orderService;
    private SignatureService signatureService;
    private String token;
    private String cookie;
    private String eoa;
    private String proxyWallet;
    private String userId;
    private String marketId;
    private String tokenId;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        orderService = new OrderService();
        signatureService = new SignatureService();
        token = TokenManager.getInstance().getAccessToken();
        cookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
        eoa = TokenManager.getInstance().getEoa();
        if (eoa == null || eoa.isBlank()) eoa = Config.getEoaAddress();
        proxyWallet = TokenManager.getInstance().getProxyWalletAddress();
        userId = TokenManager.getInstance().getUserId();
        marketId = Config.getMarketId();
        tokenId = Config.getTokenId();
    }

    @Test(description = "Place order with side short returns 202; cleanup cancel")
    public void placeOrder_shortSide_returns202() {
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
        // Short: amount = (100 - price) * quantity / 100 (2 decimals), per API payload convention
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
        Response response = orderService.placeOrder(token, cookie, eoa, proxyWallet, marketId, orderBody);
        response.then().statusCode(202).body("status", equalTo("open_order"));
        String orderId = response.jsonPath().getString("order_id");
        if (orderId != null && !orderId.isBlank()) {
            Response cancelResponse = orderService.cancelOrder(token, cookie, marketId, orderId.trim());
            assertThat(cancelResponse.getStatusCode()).isBetween(200, 299);
        }
    }
}
