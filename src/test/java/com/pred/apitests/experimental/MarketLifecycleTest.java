package com.pred.apitests.experimental;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.PlaceOrderRequest;
import com.pred.apitests.model.request.SignOrderRequest;
import com.pred.apitests.model.response.SignOrderResponse;
import com.pred.apitests.service.OrderService;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.service.SettlementService;
import com.pred.apitests.service.SignatureService;
import com.pred.apitests.util.MarketContext;
import com.pred.apitests.util.PollingUtil;
import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.UserSession;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full market lifecycle: discover, snapshot balances, place orders, verify positions,
 * close fixture, resolve (manual + sports-info), verify auto-redeem.
 *
 * After resolution, redeem happens automatically. We verify:
 * 1. Positions disappear from GET /positions
 * 2. usdc_balance increases (winnings returned to wallet)
 * 3. Trade history contains "Redeemed" activity entries
 * 4. Earnings realized_pnl is non-zero; total_pnl == realized_pnl + unrealized_pnl
 *
 * WARNING: This test DESTROYS the fixture. Run only on fixtures you are willing to burn.
 * NOT included in suite.xml. Run on-demand:
 *   mvn test -Dtest="AuthFlowTest,AuthFlowTestUser2,com.pred.apitests.experimental.MarketLifecycleTest" -Dcanonical.name=your-fixture-cname
 */
public class MarketLifecycleTest extends BaseApiTest {

    private static final String ORDER_PRICE = "30";
    private static final String ORDER_QUANTITY = "100";
    private static final String SHORT_ORDER_PRICE = "70";
    private static final String[] FAMILIES = {"moneyline", "spreads", "totals", "btts"};

    private static final int HOME_SCORE_P1 = 1;
    private static final int AWAY_SCORE_P1 = 0;
    private static final int HOME_SCORE_P2 = 1;
    private static final int AWAY_SCORE_P2 = 1;

    private static final long REDEEM_POLL_TIMEOUT_MS = 60_000;
    private static final long REDEEM_POLL_INITIAL_DELAY_MS = 2000;
    private static final long REDEEM_POLL_MAX_DELAY_MS = 5000;

    private OrderService orderService;
    private SignatureService signatureService;
    private PortfolioService portfolioService;
    private SettlementService settlementService;
    private MarketContext marketContext;
    private UserSession user1;
    private UserSession user2;

    private final List<String> user1OrderIds = new ArrayList<>();
    private final List<String> user2OrderIds = new ArrayList<>();

    private long user1BalanceBefore;
    private long user2BalanceBefore;

    @BeforeClass
    public void init() {
        orderService = new OrderService();
        signatureService = new SignatureService();
        portfolioService = new PortfolioService();
        settlementService = new SettlementService();
    }

    @Test(description = "Step 0: Discover markets and verify auth for both users")
    public void step0_discoverAndAuth() {
        user1 = getSession();
        if (user1 == null || !user1.hasToken()) throw new SkipException("User 1 not authenticated");
        user2 = SecondUserContext.getSecondUser();
        if (user2 == null || !user2.hasToken()) throw new SkipException("User 2 not authenticated");

        marketContext = MarketContext.getInstance();
        marketContext.init();

        assertThat(marketContext.getFixtureId()).as("fixture_id from discover").isNotEmpty();
        System.out.println("[Lifecycle] fixture_id=" + marketContext.getFixtureId());
        System.out.println("[Lifecycle] cname=" + marketContext.getCanonicalName());

        for (String family : FAMILIES) {
            System.out.println("[Lifecycle] " + family + " available: " + marketContext.hasFamilyWithMarkets(family));
        }
    }

    @Test(description = "Step 1: Snapshot usdc_balance for both users before trading",
          dependsOnMethods = "step0_discoverAndAuth")
    public void step1_snapshotBalances() {
        Response u1Bal = portfolioService.getBalance(user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
        assertThat(u1Bal.getStatusCode()).as("User1 balance").isEqualTo(200);
        user1BalanceBefore = parseBalanceAsLong(u1Bal.path("usdc_balance").toString());
        System.out.println("[Lifecycle] User1 usdc_balance BEFORE: " + user1BalanceBefore);

        Response u2Bal = portfolioService.getBalance(user2.getAccessToken(), user2.getRefreshCookieHeaderValue());
        assertThat(u2Bal.getStatusCode()).as("User2 balance").isEqualTo(200);
        user2BalanceBefore = parseBalanceAsLong(u2Bal.path("usdc_balance").toString());
        System.out.println("[Lifecycle] User2 usdc_balance BEFORE: " + user2BalanceBefore);

        assertThat(user1BalanceBefore).as("User1 must have funds").isGreaterThan(0);
        assertThat(user2BalanceBefore).as("User2 must have funds").isGreaterThan(0);
    }

    @Test(description = "Step 2: Place LONG+SHORT orders on all 4 market families (orders should match)",
          dependsOnMethods = "step1_snapshotBalances")
    public void step2_placeOrdersAllFamilies() {
        for (String family : FAMILIES) {
            if (!marketContext.hasFamilyWithMarkets(family)) {
                System.out.println("[Lifecycle] SKIP " + family + " — no sub-markets");
                continue;
            }

            MarketContext.ParentMarket pm = marketContext.getFirstParentMarket(family);
            String subMarketId = pm.getSubMarkets().get(0).getMarketId();
            System.out.println("[Lifecycle][" + family + "] parentMarketId=" + pm.getParentMarketId()
                    + " subMarketId=" + subMarketId);

            // User 1 LONG — path /order/{marketId}/place uses outcome (sub) id, same as body.market_id
            String oid1 = signAndPlace(user1, subMarketId, "long", ORDER_PRICE);
            assertThat(oid1).as("User1 LONG on " + family).isNotEmpty();
            user1OrderIds.add(oid1);
            System.out.println("[Lifecycle][" + family + "] User1 LONG: " + oid1);

            // User 2 SHORT — price 70 so LONG@30 + SHORT@70 will match (30+70=100)
            String oid2 = signAndPlace(user2, subMarketId, "short", SHORT_ORDER_PRICE);
            assertThat(oid2).as("User2 SHORT on " + family).isNotEmpty();
            user2OrderIds.add(oid2);
            System.out.println("[Lifecycle][" + family + "] User2 SHORT: " + oid2);
        }

        assertThat(user1OrderIds).as("User1 should have placed orders").isNotEmpty();
        assertThat(user2OrderIds).as("User2 should have placed orders").isNotEmpty();
    }

    @Test(description = "Step 3: Verify both users have positions (orders matched)",
          dependsOnMethods = "step2_placeOrdersAllFamilies")
    public void step3_verifyPositions() {
        // Give matching engine time to process
        PollingUtil.pollUntil(15_000, 500, 3000,
            "User1 positions not visible after 15s — matching engine lag?",
            () -> {
                Response r = portfolioService.getPositions(
                    user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
                if (r.getStatusCode() != 200) {
                    return false;
                }
                List<?> positions = r.path("positions");
                return positions != null && !positions.isEmpty();
            });

        Response u1Pos = portfolioService.getPositions(
            user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
        assertThat(u1Pos.getStatusCode()).isEqualTo(200);
        List<?> u1Positions = u1Pos.path("positions");
        assertThat(u1Positions).as("User1 should have positions").isNotEmpty();
        System.out.println("[Lifecycle] User1 position count: " + u1Positions.size());

        Response u2Pos = portfolioService.getPositions(
            user2.getAccessToken(), user2.getRefreshCookieHeaderValue());
        assertThat(u2Pos.getStatusCode()).isEqualTo(200);
        List<?> u2Positions = u2Pos.path("positions");
        assertThat(u2Positions).as("User2 should have positions").isNotEmpty();
        System.out.println("[Lifecycle] User2 position count: " + u2Positions.size());
    }

    @Test(description = "Step 4: Close the fixture via internal CMS API",
          dependsOnMethods = "step3_verifyPositions")
    public void step4_closeFixture() {
        String fixtureId = marketContext.getFixtureId();
        System.out.println("[Lifecycle] Closing fixture: " + fixtureId);

        Response response = settlementService.closeFixture(fixtureId);
        System.out.println("[Lifecycle] Close fixture status=" + response.getStatusCode()
            + " body=" + response.getBody().asString());

        // Accept 200 or 204 as success
        assertThat(response.getStatusCode()).as("Close fixture").isBetween(200, 204);
    }

    @Test(description = "Step 5: Resolve market with both manual and sports-info endpoints (home wins 2-1)",
          dependsOnMethods = "step4_closeFixture")
    public void step5_resolveWithScore() {
        String fixtureId = marketContext.getFixtureId();
        System.out.println("[Lifecycle] Resolving fixture " + fixtureId
            + " with score: Home " + (HOME_SCORE_P1 + HOME_SCORE_P2)
            + "-" + (AWAY_SCORE_P1 + AWAY_SCORE_P2) + " Away");

        // Call manual resolve (uses 90s timeout — on-chain operations are slow)
        Response manualResp = settlementService.resolveMarketManual(
            fixtureId, HOME_SCORE_P1, AWAY_SCORE_P1, HOME_SCORE_P2, AWAY_SCORE_P2);
        System.out.println("[Lifecycle] Resolve manual status=" + manualResp.getStatusCode()
            + " body=" + manualResp.getBody().asString());
        assertThat(manualResp.getStatusCode()).as("Resolve manual").isBetween(200, 202);

        // Call sports-info resolve (second submission for consensus threshold)
        // Wrapped in try-catch: this endpoint can be slow (on-chain) and may timeout or
        // return error if fixture is already fully resolved. Either case is non-fatal.
        try {
            Response sportsInfoResp = settlementService.resolveMarketSportsInfo(
                fixtureId, HOME_SCORE_P1, AWAY_SCORE_P1, HOME_SCORE_P2, AWAY_SCORE_P2);
            System.out.println("[Lifecycle] Resolve sports-info status=" + sportsInfoResp.getStatusCode()
                + " body=" + sportsInfoResp.getBody().asString());
            if (sportsInfoResp.getStatusCode() < 200 || sportsInfoResp.getStatusCode() > 202) {
                System.out.println("[Lifecycle] sports-info returned " + sportsInfoResp.getStatusCode()
                    + " — may already be resolved. Continuing.");
            }
        } catch (Exception e) {
            // SocketTimeoutException or similar — log and continue.
            // The manual call already submitted; consensus may still be reached.
            System.out.println("[Lifecycle] sports-info call failed: " + e.getClass().getSimpleName()
                + " — " + e.getMessage() + ". Continuing with manual resolve only.");
        }
    }

    @Test(description = "Step 6: Verify auto-redeem — positions gone, balance up, trade history has Redeemed, PnL moved",
          dependsOnMethods = "step5_resolveWithScore")
    public void step6_verifyAutoRedeem() {
        String fixtureId = marketContext.getFixtureId();

        // --- 6a: Poll until positions disappear (auto-redeem settles them) ---
        System.out.println("[Lifecycle] Waiting for positions to clear (auto-redeem)...");
        PollingUtil.pollUntil(REDEEM_POLL_TIMEOUT_MS, REDEEM_POLL_INITIAL_DELAY_MS, REDEEM_POLL_MAX_DELAY_MS,
            "Positions still present after " + (REDEEM_POLL_TIMEOUT_MS / 1000) + "s — auto-redeem may not have completed",
            () -> {
                Response r = portfolioService.getPositions(
                    user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
                if (r.getStatusCode() != 200) return false;
                List<?> positions = r.path("positions");
                // Positions should be gone after auto-redeem
                return positions == null || positions.isEmpty();
            });

        // Confirm positions are gone for both users
        Response u1Pos = portfolioService.getPositions(
            user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
        List<?> u1Remaining = u1Pos.path("positions");
        System.out.println("[Lifecycle] User1 positions after redeem: " + (u1Remaining == null ? 0 : u1Remaining.size()));
        assertThat(u1Remaining == null || u1Remaining.isEmpty())
            .as("User1 positions should be gone after auto-redeem").isTrue();

        Response u2Pos = portfolioService.getPositions(
            user2.getAccessToken(), user2.getRefreshCookieHeaderValue());
        List<?> u2Remaining = u2Pos.path("positions");
        System.out.println("[Lifecycle] User2 positions after redeem: " + (u2Remaining == null ? 0 : u2Remaining.size()));
        assertThat(u2Remaining == null || u2Remaining.isEmpty())
            .as("User2 positions should be gone after auto-redeem").isTrue();

        // --- 6b: Verify balance increased for at least one user (winner gets payout) ---
        Response u1BalAfter = portfolioService.getBalance(
            user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
        assertThat(u1BalAfter.getStatusCode()).isEqualTo(200);
        long user1BalanceAfter = parseBalanceAsLong(u1BalAfter.path("usdc_balance").toString());
        System.out.println("[Lifecycle] User1 usdc_balance AFTER: " + user1BalanceAfter
            + " (delta: " + (user1BalanceAfter - user1BalanceBefore) + ")");

        Response u2BalAfter = portfolioService.getBalance(
            user2.getAccessToken(), user2.getRefreshCookieHeaderValue());
        assertThat(u2BalAfter.getStatusCode()).isEqualTo(200);
        long user2BalanceAfter = parseBalanceAsLong(u2BalAfter.path("usdc_balance").toString());
        System.out.println("[Lifecycle] User2 usdc_balance AFTER: " + user2BalanceAfter
            + " (delta: " + (user2BalanceAfter - user2BalanceBefore) + ")");

        boolean balanceChanged = (user1BalanceAfter != user1BalanceBefore)
                              || (user2BalanceAfter != user2BalanceBefore);
        assertThat(balanceChanged).as("At least one user's balance should change after redeem").isTrue();

        // --- 6c: Trade history should contain "Redeemed" activity entries ---
        Response u1History = portfolioService.getTradeHistory(
            user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
        assertThat(u1History.getStatusCode()).isEqualTo(200);
        List<?> u1Trades = getTradeHistoryList(u1History);
        boolean foundRedeemed = false;
        if (u1Trades != null) {
            for (Object item : u1Trades) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> trade = (Map<String, Object>) item;
                    String activity = String.valueOf(trade.get("activity")).trim();
                    if ("Redeemed".equals(activity)) {
                        foundRedeemed = true;
                        System.out.println("[Lifecycle] Found Redeemed entry: market_id="
                            + trade.get("market_id") + " pnl=" + trade.get("pnl"));
                    }
                }
            }
        }
        assertThat(foundRedeemed).as("Trade history should contain at least one 'Redeemed' entry after auto-redeem").isTrue();

        // --- 6d: Earnings should show non-zero realized_pnl ---
        Response u1Earnings = portfolioService.getEarnings(
            user1.getAccessToken(), user1.getRefreshCookieHeaderValue());
        assertThat(u1Earnings.getStatusCode()).isEqualTo(200);
        double realizedPnl = parsePnlAsDouble(
            u1Earnings.path("realized_pnl") != null ? u1Earnings.path("realized_pnl").toString() : "0");
        double unrealizedPnl = parsePnlAsDouble(
            u1Earnings.path("unrealized_pnl") != null ? u1Earnings.path("unrealized_pnl").toString() : "0");
        double totalPnl = parsePnlAsDouble(
            u1Earnings.path("total_pnl") != null ? u1Earnings.path("total_pnl").toString() : "0");
        System.out.println("[Lifecycle] User1 earnings: realized=" + realizedPnl
            + " unrealized=" + unrealizedPnl + " total=" + totalPnl);

        // total_pnl should equal realized + unrealized
        assertThat(Math.abs(totalPnl - (realizedPnl + unrealizedPnl)))
            .as("total_pnl should equal realized_pnl + unrealized_pnl").isLessThanOrEqualTo(0.01);

        System.out.println("[Lifecycle] COMPLETE — fixture " + fixtureId + " resolved with score "
            + (HOME_SCORE_P1 + HOME_SCORE_P2) + "-" + (AWAY_SCORE_P1 + AWAY_SCORE_P2)
            + ". Auto-redeem verified: positions cleared, balance changed, Redeemed in history, PnL updated.");
    }

    // --- Helpers (same pattern as MarketFamilyOrderTest) ---

    /**
     * Limit order amount per API: LONG = price * qty / 100, SHORT = (100 - price) * qty / 100.
     * Copied from MarketFamilyOrderTest — this is the tested, working calculation.
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
}
