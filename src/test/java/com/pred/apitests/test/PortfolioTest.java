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
    }

    @Test(description = "GET portfolio positions returns 200")
    public void getPositions_returns200() {
        Response response = portfolioService.getPositions(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test(description = "GET balance by market returns 200")
    public void getBalanceByMarket_returns200() {
        String marketId = Config.getMarketId();
        if (marketId == null || marketId.isBlank()) {
            throw new SkipException("MARKET_ID not set in config");
        }
        Response response = portfolioService.getBalanceByMarket(token, cookie, marketId);
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test(description = "GET portfolio earnings returns 200; use earnings for PnL data (user_id, realized_pnl, unrealized_pnl, total_pnl)")
    public void getEarnings_returns200() {
        Response response = portfolioService.getEarnings(token, cookie);
        assertThat(response.getStatusCode()).isEqualTo(200);
        String body = response.getBody().asString();
        assertThat(body).contains("user_id").contains("realized_pnl").contains("unrealized_pnl").contains("total_pnl");
    }
}
