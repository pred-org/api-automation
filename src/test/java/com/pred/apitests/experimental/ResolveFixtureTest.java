package com.pred.apitests.experimental;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.service.SettlementService;
import com.pred.apitests.util.MarketContext;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Standalone utility test: re-submits sports-info resolve for a fixture
 * that already has a manual resolve submission (needs 2nd source for consensus).
 *
 * Run:  mvn test -Dtest=com.pred.apitests.experimental.ResolveFixtureTest -DforkCount=0
 */
public class ResolveFixtureTest extends BaseApiTest {

    private static final int HOME_SCORE_P1 = 1;
    private static final int AWAY_SCORE_P1 = 0;
    private static final int HOME_SCORE_P2 = 1;
    private static final int AWAY_SCORE_P2 = 1;

    private SettlementService settlementService;
    private String fixtureId;

    @BeforeClass
    public void init() {
        settlementService = new SettlementService();
        MarketContext ctx = MarketContext.getInstance();
        ctx.init();
        fixtureId = ctx.getFixtureId();
        System.out.println("[ResolveFixtureTest] fixture_id = " + fixtureId);
    }

    @Test(description = "Re-submit sports-info resolve to push remaining markets past consensus")
    public void resolveSportsInfo() {
        System.out.println("[ResolveFixtureTest] Calling sports-info resolve (score: "
                + (HOME_SCORE_P1 + HOME_SCORE_P2) + "-" + (AWAY_SCORE_P1 + AWAY_SCORE_P2)
                + "). This may take 60-90s (on-chain)...");

        try {
            Response resp = settlementService.resolveMarketSportsInfo(
                    fixtureId, HOME_SCORE_P1, AWAY_SCORE_P1, HOME_SCORE_P2, AWAY_SCORE_P2);
            System.out.println("[ResolveFixtureTest] sports-info status: " + resp.getStatusCode());
            System.out.println("[ResolveFixtureTest] sports-info body: " + resp.getBody().asString());
        } catch (Exception e) {
            System.out.println("[ResolveFixtureTest] sports-info call exception: " + e.getMessage());
            System.out.println("[ResolveFixtureTest] This is expected if on-chain takes >90s. Check discover API for status.");
        }
    }
}
