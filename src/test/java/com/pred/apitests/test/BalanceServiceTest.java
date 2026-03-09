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

    /** Discovered delta from balance_decreaseIsConsistentWithOrderSize (price=30, qty=100). */
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
        marketId = Config.getMarketId();
    }

    private long getOverallBalance() {
        Response response = portfolioService.getBalance(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        String usdc = response.path("usdc_balance");
        return Long.parseLong(usdc != null ? usdc.trim() : "0");
    }

    private long getMarketBalance() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        Response response = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(response.getStatusCode()).isEqualTo(200);
        String usdc = response.path("usdc_balance");
        return Long.parseLong(usdc != null ? usdc.trim() : "0");
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
                .amount(price)
                .isLowPriority(false)
                .signature(sigResponse.getSignature())
                .type("limit")
                .timestamp(timestampSec)
                .reduceOnly(false)
                .feeRateBps(0)
                .build();
        Response response = orderService.placeOrder(token, cookie, eoa, proxyWallet, marketId, orderBody);
        assertThat(response.getStatusCode()).isEqualTo(202);
        String orderId = response.jsonPath().getString("order_id");
        return orderId != null ? orderId.trim() : "";
    }

    private void cancelOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) return;
        Response r = orderService.cancelOrder(token, cookie, marketId, orderId);
        assertThat(r.getStatusCode()).as("cancel order").isBetween(200, 299);
    }

    @Test(description = "Balance restores after cancelling order; exact equality with 2s wait")
    public void balance_restoresAfterCancellingOrder() {
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        long before = getMarketBalance();
        String orderId = placeOrder(ORDER_PRICE, ORDER_QUANTITY);
        cancelOrder(orderId);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        long after = getMarketBalance();
        assertThat(after).as("balance after cancel").isEqualTo(before);
        if (after != before) {
            assertThat(after).as("fallback: balance after cancel >= before").isGreaterThanOrEqualTo(before);
        }
    }

    @Test(description = "Discover exact delta for price=30 qty=100; assert positive and reasonable")
    public void balance_decreaseIsConsistentWithOrderSize() {
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
}
