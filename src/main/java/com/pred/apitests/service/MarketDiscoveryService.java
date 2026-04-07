package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import io.restassured.response.Response;

/**
 * Market discovery APIs (e.g. leagues, fixtures). May be public or use same auth.
 */
public class MarketDiscoveryService extends BaseService {

    private static final String LEAGUES_PATH = "/api/v1/market-discovery/leagues";
    private static final String FIXTURES_PATH = "/api/v1/market-discovery/fixtures";
    private static final String DISCOVER_PATH = "/api/v1/market-discovery/discover";

    /**
     * GET /market-discovery/leagues. Public (no auth) unless backend requires it.
     */
    public Response getLeagues() {
        return given(getPublicBaseUri()).when().get(LEAGUES_PATH);
    }

    /**
     * GET /market-discovery/fixtures?league_id={leagueId}.
     */
    public Response getFixturesByLeague(String leagueId) {
        return given(getPublicBaseUri())
                .queryParam("league_id", leagueId)
                .when().get(FIXTURES_PATH);
    }

    /**
     * GET /api/v1/market-discovery/discover?cname={canonicalName}&verbose=true
     * Public endpoint. Returns full market hierarchy for the given fixture.
     */
    public Response discoverByCanonicalName(String canonicalName) {
        return given(getPublicBaseUri())
                .queryParam("cname", canonicalName)
                .queryParam("verbose", "true")
                .when().get(DISCOVER_PATH);
    }
}
