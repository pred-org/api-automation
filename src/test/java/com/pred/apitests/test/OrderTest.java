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
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrderTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String ORDER_AMOUNT = "30";

    private OrderService orderService;
    private SignatureService signatureService;
    private PortfolioService portfolioService;
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
        portfolioService = new PortfolioService();
        token = TokenManager.getInstance().getAccessToken();
        cookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
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

        Response response = orderService.placeOrder(token, cookie, eoa, proxyWallet, marketId, orderBody);
        int status = response.getStatusCode();
        if (status != 202) {
            String body = response.getBody().asString();
            System.out.println("DEBUG [place-order FAIL] status=" + status + " body=" + (body != null ? body : ""));
        }
        assertThat(status).isEqualTo(202);
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

        Response response = orderService.placeOrder(token, cookie, eoa, proxyWallet, marketId, orderBody);
        assertThat(response.getStatusCode()).isGreaterThanOrEqualTo(400);
    }

    @Test(description = "Place order with balance check before and after; balance should reflect open order")
    public void placeOrder_balanceBeforeAndAfterReflectsOrder() {
        String marketId = Config.getMarketId();
        String tokenId = Config.getTokenId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");
        if (tokenId == null || tokenId.isBlank()) throw new SkipException("TOKEN_ID not set");
        if (eoa == null || eoa.isBlank()) throw new SkipException("EOA not in TokenManager");
        if (proxyWallet == null || proxyWallet.isBlank()) throw new SkipException("Proxy wallet not in TokenManager");

        // 1) Balance before
        Response balanceOverallBefore = portfolioService.getBalance(token, cookie);
        Response balanceByMarketBefore = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(balanceOverallBefore.getStatusCode()).isEqualTo(200);
        assertThat(balanceByMarketBefore.getStatusCode()).isEqualTo(200);

        BalanceResponse beforeOverall = balanceOverallBefore.as(BalanceResponse.class);
        BalanceResponse beforeByMarket = balanceByMarketBefore.as(BalanceResponse.class);
        logBalance("BEFORE (overall)", beforeOverall);
        logBalance("BEFORE (by market)", beforeByMarket);

        // 2) Place order
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

        Response orderResponse = orderService.placeOrder(token, cookie, eoa, proxyWallet, marketId, orderBody);
        assertThat(orderResponse.getStatusCode()).isEqualTo(202);

        // 3) Balance after
        Response balanceOverallAfter = portfolioService.getBalance(token, cookie);
        Response balanceByMarketAfter = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(balanceOverallAfter.getStatusCode()).isEqualTo(200);
        assertThat(balanceByMarketAfter.getStatusCode()).isEqualTo(200);

        BalanceResponse afterOverall = balanceOverallAfter.as(BalanceResponse.class);
        BalanceResponse afterByMarket = balanceByMarketAfter.as(BalanceResponse.class);
        logBalance("AFTER (overall)", afterOverall);
        logBalance("AFTER (by market)", afterByMarket);

        // 4) Assert balance reflects open order: reserved should increase or available decrease (when API provides these)
        assertBalanceReflectsOrder(beforeByMarket, afterByMarket, ORDER_AMOUNT);
    }

    @Test(description = "Balance (overall or by market) when total/available/reserved present: available = total - reserved")
    public void balance_availableEqualsTotalMinusReserved_whenFieldsPresent() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) throw new SkipException("MARKET_ID not set");

        Response overallRes = portfolioService.getBalance(token, cookie);
        Response byMarketRes = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(overallRes.getStatusCode()).isEqualTo(200);
        assertThat(byMarketRes.getStatusCode()).isEqualTo(200);

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
        long orderAmount = parseBalance(orderAmountStr);
        if (orderAmount < 0) return;

        Long reservedBefore = parseBalance(before.getReservedBalance());
        Long reservedAfter = parseBalance(after.getReservedBalance());
        if (reservedBefore != null && reservedAfter != null) {
            assertThat(reservedAfter).as("reserved_balance should increase after placing order").isGreaterThanOrEqualTo(reservedBefore);
        }

        Long availableBefore = parseBalance(before.getAvailableBalance());
        Long availableAfter = parseBalance(after.getAvailableBalance());
        if (availableBefore != null && availableAfter != null) {
            assertThat(availableAfter).as("available_balance should decrease or stay same after placing order").isLessThanOrEqualTo(availableBefore);
        }

        // When API only returns usdc_balance (e.g. by market), assert it decreased by order amount (balance reflects open order)
        Long usdcBefore = parseBalance(before.getUsdcBalance());
        Long usdcAfter = parseBalance(after.getUsdcBalance());
        if (usdcBefore != null && usdcAfter != null && reservedBefore == null && availableBefore == null) {
            assertThat(usdcAfter).as("usdc_balance (by market) should decrease by order amount when reserved/available not returned").isEqualTo(usdcBefore - orderAmount);
        }
    }

    private static void assertAvailableEqualsTotalMinusReserved(BalanceResponse b) {
        if (b == null) return;
        Long total = parseBalance(b.getTotalBalance());
        Long available = parseBalance(b.getAvailableBalance());
        Long reserved = parseBalance(b.getReservedBalance());
        if (total != null && available != null && reserved != null) {
            assertThat(available).as("available_balance should equal total_balance - reserved_balance").isEqualTo(total - reserved);
        }
    }

    private static Long parseBalance(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
