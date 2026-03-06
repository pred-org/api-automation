package com.pred.apitests.test;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.service.PortfolioService;
import com.pred.apitests.util.TokenManager;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class PortfolioTest extends BaseApiTest {

    private PortfolioService portfolioService;
    private String token;
    private String cookie;

    @BeforeClass
    public void init() {
        if (!TokenManager.getInstance().hasToken()) {
            throw new SkipException("No token - run AuthFlowTest first");
        }
        portfolioService = new PortfolioService();
        token = TokenManager.getInstance().getAccessToken();
        cookie = TokenManager.getInstance().getRefreshCookieHeaderValue();
    }

    @Test(description = "GET portfolio balance returns 200")
    public void getBalance_returns200() {
        Response response = portfolioService.getBalance(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(true))
                .body("usdc_balance", notNullValue())
                .body("position_balance", notNullValue());
        String usdcBalance = response.path("usdc_balance");
        assertThat(Long.parseLong(usdcBalance)).isGreaterThan(0L);
    }

    @Test(description = "GET portfolio positions returns 200")
    public void getPositions_returns200() {
        Response response = portfolioService.getPositions(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(true))
                .body("total_invested", notNullValue())
                .body("total_to_win", notNullValue())
                .body("position_league_summary", notNullValue())
                .body("positions", notNullValue());
    }

    @Test(description = "GET balance by market returns 200")
    public void getBalanceByMarket_returns200() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) {
            throw new SkipException("MARKET_ID not set in config");
        }
        Response response = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("success", equalTo(true))
                .body("usdc_balance", notNullValue())
                .body("position_balance", notNullValue());
        assertThat(Long.parseLong(response.path("usdc_balance").toString())).isGreaterThan(0);
    }

    @Test(description = "GET portfolio earnings returns 200; use earnings for PnL data (user_id, realized_pnl, unrealized_pnl, total_pnl)")
    public void getEarnings_returns200() {
        Response response = portfolioService.getEarnings(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        response.then().body("user_id", equalTo("e85379bd-2d17-4140-9f5d-4ab68eb847dd"))
                .body("realized_pnl", notNullValue())
                .body("unrealized_pnl", notNullValue())
                .body("total_pnl", notNullValue());
        String body = response.getBody().asString();
        assertThat(body).contains("user_id").contains("realized_pnl").contains("unrealized_pnl").contains("total_pnl");
    }
}
