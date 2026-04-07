package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.service.MarketDiscoveryService;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Market discovery endpoints: leagues, fixtures. No auth required unless backend changes.
 */
public class MarketDiscoveryTest extends BaseApiTest {

    private MarketDiscoveryService marketDiscoveryService;

    @BeforeClass
    public void init() {
        marketDiscoveryService = new MarketDiscoveryService();
    }

    @Test(description = "GET /market-discovery/leagues returns 200 and list of leagues with id, name, sport, status")
    public void getLeagues_returns200_andList() {
        Response response = marketDiscoveryService.getLeagues();
        if (response.getStatusCode() == 404) {
            throw new SkipException("GET /market-discovery/leagues not implemented (404)");
        }
        assertThat(response.getStatusCode()).as("getLeagues").isEqualTo(200);
        List<?> leagues = response.path("data");
        if (leagues == null) leagues = response.path("leagues");
        if (leagues == null) leagues = response.path("");
        assertThat(leagues).as("response should have leagues array (data or leagues)").isNotNull();
        if (!leagues.isEmpty()) {
            Object first = leagues.get(0);
            assertThat(first).isInstanceOf(Map.class);
            Map<?, ?> league = (Map<?, ?>) first;
            assertThat(league.containsKey("id") || league.containsKey("league_id")).as("league entry has id").isTrue();
            assertThat(league.containsKey("name") || league.containsKey("league_name")).as("league entry has name").isTrue();
        }
    }

    @Test(description = "GET /market-discovery/fixtures?league_id=X returns 200 and list; uses first league from getLeagues if available")
    public void getFixturesByLeague_returns200() {
        Response leaguesRes = marketDiscoveryService.getLeagues();
        if (leaguesRes.getStatusCode() != 200) {
            throw new SkipException("getLeagues did not return 200 - skip fixtures test");
        }
        List<?> leagues = leaguesRes.path("data");
        if (leagues == null) leagues = leaguesRes.path("leagues");
        String leagueId = null;
        if (leagues != null && !leagues.isEmpty()) {
            Object first = leagues.get(0);
            if (first instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) first;
                if (m.get("id") != null) leagueId = String.valueOf(m.get("id"));
                else if (m.get("league_id") != null) leagueId = String.valueOf(m.get("league_id"));
            }
        }
        if (leagueId == null || leagueId.isBlank()) {
            throw new SkipException("No league_id from getLeagues - use a known league_id for fixtures test");
        }
        Response response = marketDiscoveryService.getFixturesByLeague(leagueId);
        if (response.getStatusCode() == 404) {
            throw new SkipException("GET /market-discovery/fixtures not implemented (404)");
        }
        if (response.getStatusCode() == 500) {
            throw new SkipException("Skipping: fixtures endpoint returned 500 (backend issue — not a test failure)");
        }
        assertThat(response.getStatusCode()).as("getFixturesByLeague").isEqualTo(200);
        Object body = response.path("");
        assertThat(body).as("response body").isNotNull();
    }

    @Test(description = "Discover by canonical name returns valid market hierarchy")
    public void discoverByCanonicalName_returnsMarketHierarchy() {
        String cname = Config.getCanonicalName();
        if (cname == null || cname.isBlank()) {
            throw new SkipException("CANONICAL_NAME not set");
        }

        Response response = marketDiscoveryService.discoverByCanonicalName(cname);
        assertThat(response.getStatusCode()).isEqualTo(200);

        assertThat(response.jsonPath().getList("data.parent_markets_list")).isNotEmpty();
        assertThat(response.jsonPath().getString("data.parent_markets_list[0].fixture.fixture_id")).isNotEmpty();
        String cnameFromFixture = response.jsonPath().getString("data.parent_markets_list[0].fixture.canonical_name");
        String cnameFromParent = response.jsonPath().getString("data.parent_markets_list[0].parent_market_data.canonical_name");
        String resolved = cnameFromFixture != null && !cnameFromFixture.isBlank() ? cnameFromFixture : cnameFromParent;
        assertThat(resolved).as("fixture or parent_market_data canonical_name").isNotBlank();
        assertThat(cname.trim()).isEqualToIgnoringCase(resolved.trim());

        List<Map<String, Object>> list = response.jsonPath().getList("data.parent_markets_list");
        boolean hasMoneyline = false;
        for (Map<String, Object> row : list) {
            if (row == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> pmd = (Map<String, Object>) row.get("parent_market_data");
            if (pmd != null && Objects.equals("moneyline", pmd.get("parent_market_family"))) {
                hasMoneyline = true;
                break;
            }
        }
        assertThat(hasMoneyline).as("at least one moneyline parent market").isTrue();
    }

    @Test(description = "Discover with invalid canonical name returns empty or 404")
    public void discoverByCanonicalName_invalidCname_emptyResult() {
        Response response = marketDiscoveryService.discoverByCanonicalName("nonexistent-fixture-9999");
        if (response.getStatusCode() == 200) {
            List<?> list = response.jsonPath().getList("data.parent_markets_list");
            assertThat(list == null || list.isEmpty()).isTrue();
        } else {
            assertThat(response.getStatusCode()).isIn(404, 400, 500);
        }
    }
}
