package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;

import java.util.Map;

/**
 * Internal settlement APIs: close fixture and resolve market on-chain.
 * Uses INTERNAL base URI (api-internal.uat-frankfurt.pred.app).
 * No auth headers needed — these are internal endpoints.
 *
 * IMPORTANT: Resolve endpoints do on-chain work and can take 30-90 seconds.
 * Uses extended read timeout (90s) instead of the default 15s from BaseService.
 */
public class SettlementService extends BaseService {

    private static final String CLOSE_FIXTURE_PATH = "/api/v1/cms/internal/fixtures/close";
    private static final String RESOLVE_MANUAL_PATH = "/api/v1/settlement/internal/resolve-market-onchain/score/manual";
    private static final String RESOLVE_SPORTS_INFO_PATH = "/api/v1/settlement/internal/resolve-market-onchain/score/sports-info";

    /** Extended read timeout for resolve endpoints (on-chain operations are slow). */
    private static final int SETTLEMENT_READ_TIMEOUT_MS = 90_000;

    /** RestAssured config with extended read timeout for settlement calls. */
    private static final RestAssuredConfig SETTLEMENT_CONFIG = RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                    .setParam("http.connection.timeout", 10_000)
                    .setParam("http.socket.timeout", SETTLEMENT_READ_TIMEOUT_MS));

    /**
     * PUT /api/v1/cms/internal/fixtures/close
     * Marks a fixture as closed. Internal endpoint, no auth needed.
     * Body: JSON with cms_fixture_id set to the fixture UUID.
     */
    public Response closeFixture(String fixtureId) {
        Map<String, String> body = Map.of("cms_fixture_id", fixtureId);
        return given(getInternalBaseUri())
                .body(body)
                .when()
                .put(CLOSE_FIXTURE_PATH);
    }

    /**
     * POST /api/v1/settlement/internal/resolve-market-onchain/score/manual
     * Resolves market with manually provided score. Internal endpoint, no auth needed.
     * Triggers auto-redeem — positions are settled and balances updated automatically.
     * Uses extended 90s read timeout (on-chain operations are slow).
     */
    public Response resolveMarketManual(String fixtureId,
                                         int homeScoreP1, int awayScoreP1,
                                         int homeScoreP2, int awayScoreP2) {
        Map<String, Object> body = Map.ofEntries(
            Map.entry("fixture_id", fixtureId),
            Map.entry("home_score_period_1", homeScoreP1),
            Map.entry("away_score_period_1", awayScoreP1),
            Map.entry("home_score_period_2", homeScoreP2),
            Map.entry("away_score_period_2", awayScoreP2),
            Map.entry("home_score_overtime_period_1", 0),
            Map.entry("away_score_overtime_period_1", 0),
            Map.entry("home_score_overtime_period_2", 0),
            Map.entry("away_score_overtime_period_2", 0),
            Map.entry("home_score_penalties", 0),
            Map.entry("away_score_penalties", 0)
        );
        return given(getInternalBaseUri())
                .config(SETTLEMENT_CONFIG)
                .body(body)
                .when()
                .post(RESOLVE_MANUAL_PATH);
    }

    /**
     * POST /api/v1/settlement/internal/resolve-market-onchain/score/sports-info
     * Resolves market using sports info source. Same body structure as manual.
     * Triggers auto-redeem — positions are settled and balances updated automatically.
     * Uses extended 90s read timeout (on-chain operations are slow).
     */
    public Response resolveMarketSportsInfo(String fixtureId,
                                             int homeScoreP1, int awayScoreP1,
                                             int homeScoreP2, int awayScoreP2) {
        Map<String, Object> body = Map.ofEntries(
            Map.entry("fixture_id", fixtureId),
            Map.entry("home_score_period_1", homeScoreP1),
            Map.entry("away_score_period_1", awayScoreP1),
            Map.entry("home_score_period_2", homeScoreP2),
            Map.entry("away_score_period_2", awayScoreP2),
            Map.entry("home_score_overtime_period_1", 0),
            Map.entry("away_score_overtime_period_1", 0),
            Map.entry("home_score_overtime_period_2", 0),
            Map.entry("away_score_overtime_period_2", 0),
            Map.entry("home_score_penalties", 0),
            Map.entry("away_score_penalties", 0)
        );
        return given(getInternalBaseUri())
                .config(SETTLEMENT_CONFIG)
                .body(body)
                .when()
                .post(RESOLVE_SPORTS_INFO_PATH);
    }
}
