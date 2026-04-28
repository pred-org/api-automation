package com.pred.apitests.experimental;

import com.pred.apitests.base.BaseApiTest;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.DepositService;
import com.pred.apitests.service.SignatureService;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NewUserLoginDepositTest extends BaseApiTest {

    private static final String NEW_USER_EOA = "0x20550f7Ab84cF68d3cb2536eF0af3b0ca8Ea34ad";
    private static final String NEW_USER_PRIVATE_KEY = "bcc682570ce255937ed455ab7d3594fd6ffeced565073b0b07919d5a45e8a629";
    private static final String NEW_USER_API_KEY = "9a655429-ce86-456f-82db-78fa4d949874-74001355-1c17-44b8-a6e5-bddca8650daa";

    // 1000 USDC in atomic units (6 decimals)
    private static final long DEPOSIT_AMOUNT = 1_000_000_000L;

    private AuthService authService;
    private SignatureService signatureService;
    private DepositService depositService;

    private String userId;
    private String transactionHash;

    @BeforeClass
    public void init() {
        authService = new AuthService();
        signatureService = new SignatureService();
        depositService = new DepositService();
    }

    @Test(priority = 1, description = "Login with signature only (no enable trading)")
    public void loginWithSignature() {
        String sigServerUrl = Config.getSigServerUrl();
        assertThat(sigServerUrl).isNotBlank();

        SignCreateProxyResponse sigResponse = signatureService.signCreateProxy(sigServerUrl, NEW_USER_PRIVATE_KEY);
        assertThat(sigResponse).isNotNull();
        assertThat(sigResponse.isOk()).isTrue();
        assertThat(sigResponse.getSignature()).isNotBlank();

        long now = System.currentTimeMillis();
        LoginRequest loginRequest = LoginRequest.builder()
                .walletAddress(sigResponse.getWalletAddress())
                .signature(sigResponse.getSignature())
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-" + now + "-" + (now / 1000))
                .chainType("base-sepolia")
                .timestamp(now / 1000)
                .build();

        Response response = authService.login(NEW_USER_API_KEY, loginRequest);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);

        var json = response.jsonPath();
        String[] userIdPaths = {"data.user_id", "data.data.user_id", "user_id"};
        for (String path : userIdPaths) {
            String v = json.getString(path);
            if (v != null && !v.isBlank()) {
                userId = v.trim();
                break;
            }
        }
        assertThat(userId).as("userId").isNotBlank();

        System.out.println("[NewUser] Login success");
        System.out.println("[NewUser] EOA: " + NEW_USER_EOA);
        System.out.println("[NewUser] userId: " + userId);
    }

    @Test(priority = 2, dependsOnMethods = "loginWithSignature",
            description = "Simple deposit: internal deposit then cashflow")
    public void depositFunds() {
        assertThat(userId).as("userId must be set").isNotBlank();

        System.out.println("[NewUser] Deposit requested (atomic units): " + DEPOSIT_AMOUNT);

        Response internalResp = depositService.internalDeposit(userId, DEPOSIT_AMOUNT);
        assertThat(internalResp.getStatusCode()).isGreaterThanOrEqualTo(200).isLessThan(300);

        transactionHash = depositService.extractTransactionHashFromInternalDeposit(internalResp);
        assertThat(transactionHash).as("transaction_hash").isNotBlank();

        Boolean internalSuccess = internalResp.jsonPath().getBoolean("success");
        System.out.println("[NewUser] Internal deposit success: " + internalSuccess);
        System.out.println("[NewUser] transaction_hash: " + transactionHash);

        Response cashflowResp = depositService.cashflowDeposit(userId, transactionHash);
        assertThat(cashflowResp.getStatusCode()).isGreaterThanOrEqualTo(200).isLessThan(300);

        Boolean cashflowSuccess = cashflowResp.jsonPath().getBoolean("success");
        System.out.println("[NewUser] Cashflow success: " + cashflowSuccess);
        System.out.println("[NewUser] Cashflow body: " + cashflowResp.getBody().asString());

        assertThat(Boolean.TRUE.equals(cashflowSuccess))
                .as("cashflow success should be true for confirmed deposit")
                .isTrue();
    }
}
