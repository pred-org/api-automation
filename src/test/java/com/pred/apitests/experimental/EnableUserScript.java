package com.pred.apitests.experimental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.EnableTradingService;
import com.pred.apitests.service.SignatureService;
import io.restassured.response.Response;

public class EnableUserScript {

    private static final String DEFAULT_MAINNET_BASE_URL = "https://pred.app";
    private static final String DEFAULT_SIG_SERVER_URL = "http://localhost:5050";
    private static final String PRIVATE_KEY = "ecbc669516c0a6b3d0e745b1348f3418d52a5886e8581457663505662fa5a07c";

    public static void main(String[] args) {
        // Mainnet by default; can override with env/system props.
        setIfBlank("api.base.uri.public", firstNonBlank(System.getenv("API_BASE_URI_PUBLIC"), DEFAULT_MAINNET_BASE_URL));
        setIfBlank("api.base.uri.internal", firstNonBlank(System.getenv("API_BASE_URI_INTERNAL"), System.getProperty("api.base.uri.public")));
        setIfBlank("sig.server.url", firstNonBlank(System.getenv("SIG_SERVER_URL"), DEFAULT_SIG_SERVER_URL));

        AuthService authService = new AuthService();
        SignatureService signatureService = new SignatureService();
        EnableTradingService enableTradingService = new EnableTradingService();
        String sigServerUrl = Config.getSigServerUrl();
        String baseUrl = Config.getPublicBaseUri();
        String privateKey = firstNonBlank(System.getenv("PRIVATE_KEY"), PRIVATE_KEY);
        String chainType = firstNonBlank(System.getenv("CHAIN_TYPE"), "base-sepolia");

        System.out.println("Using API base URL: " + baseUrl);
        System.out.println("Using sig-server URL: " + sigServerUrl);
        System.out.println("Using chain type: " + chainType);

        System.out.println("=== Step 1: Get API Key ===");
        String apiKey = Config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("No API_KEY found, attempting create-api call on: " + Config.getInternalBaseUri());
            Response keyResp = authService.createApiKey();
            System.out.println("createApiKey status: " + keyResp.getStatusCode());
            if (keyResp.getStatusCode() != 200) {
                System.out.println("Body: " + keyResp.getBody().asString());
                System.out.println("FAILED: Cannot get API key. Try setting API_KEY env var.");
                return;
            }
            apiKey = authService.parseApiKey(keyResp);
        }
        System.out.println("API Key: " + apiKey.substring(0, Math.min(20, apiKey.length())) + "...");

        System.out.println("\n=== Step 2: Sign Create Proxy ===");
        SignCreateProxyResponse sigResp = signatureService.signCreateProxy(sigServerUrl, privateKey);
        if (sigResp == null || !sigResp.isOk()) {
            System.out.println("FAILED: sig-server returned error. Is it running on " + sigServerUrl + "?");
            return;
        }
        String walletAddress = sigResp.getWalletAddress();
        String signature = sigResp.getSignature();
        System.out.println("Wallet (EOA): " + walletAddress);
        System.out.println("Signature: " + signature.substring(0, 20) + "...");

        System.out.println("\n=== Step 3: Login ===");
        long now = System.currentTimeMillis();
        LoginRequest loginReq = LoginRequest.builder()
                .walletAddress(walletAddress)
                .signature(signature)
                .message("Sign in to PRED Trading Platform")
                .nonce("nonce-" + now + "-" + (now / 1000))
                .chainType(chainType)
                .timestamp(now / 1000)
                .build();

        Response loginResp = authService.login(apiKey, loginReq);
        System.out.println("Login status: " + loginResp.getStatusCode());
        if (loginResp.getStatusCode() != 200) {
            System.out.println("Body: " + loginResp.getBody().asString());
            System.out.println("FAILED: Login failed.");
            return;
        }

        String accessToken = authService.extractAccessTokenFromResponse(loginResp);
        String refreshCookie = loginResp.getHeader("Set-Cookie");
        String refreshToken = authService.extractRefreshTokenFromResponse(loginResp);
        String userId = firstNonBlank(
                loginResp.jsonPath().getString("user_id"),
                loginResp.jsonPath().getString("data.user_id"),
                loginResp.jsonPath().getString("data.data.user_id"));
        String proxyWallet = firstNonBlank(
                loginResp.jsonPath().getString("proxy_wallet_address"),
                loginResp.jsonPath().getString("proxy_wallet_addr"),
                loginResp.jsonPath().getString("data.proxy_wallet_address"),
                loginResp.jsonPath().getString("data.proxy_wallet_addr"),
                loginResp.jsonPath().getString("data.data.proxy_wallet_address"),
                loginResp.jsonPath().getString("data.data.proxy_wallet_addr"));

        System.out.println("User ID: " + userId);
        System.out.println("Proxy Wallet: " + proxyWallet);
        if (accessToken == null || accessToken.isBlank()) {
            System.out.println("FAILED: access token missing in login response.");
            System.out.println("Body: " + loginResp.getBody().asString());
            return;
        }
        if (proxyWallet == null || proxyWallet.isBlank()) {
            System.out.println("FAILED: proxy wallet missing in login response.");
            System.out.println("Body: " + loginResp.getBody().asString());
            return;
        }
        System.out.println("Access Token: " + accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            System.out.println("Refresh Token: " + refreshToken);
        } else {
            System.out.println("Refresh Token: (not found)");
        }
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            System.out.println("Refresh Cookie Header: " + refreshCookie);
        }

        System.out.println("\n=== Step 4: Enable Trading ===");
        // Prepare
        Response prepareResp = null;
        int prepareStatus = -1;
        for (int attempt = 1; attempt <= 3; attempt++) {
            prepareResp = enableTradingService.prepare(accessToken, refreshCookie, proxyWallet);
            prepareStatus = prepareResp.getStatusCode();
            String body = prepareResp.getBody().asString();
            System.out.println("Prepare attempt " + attempt + " status: " + prepareStatus);
            if (prepareStatus == 500 && body.contains("trading is already enabled")) {
                System.out.println("Trading is ALREADY ENABLED for this user. Done!");
                return;
            }
            if (prepareStatus == 500 && body.contains("no contract code")) {
                System.out.println("Safe not deployed yet, retrying in 4s...");
                try { Thread.sleep(4000); } catch (InterruptedException e) { break; }
                continue;
            }
            if (prepareStatus == 200) break;
            System.out.println("Body: " + body);
        }
        if (prepareStatus != 200) {
            System.out.println("FAILED: Prepare did not return 200.");
            return;
        }

        // Get transaction hash
        String txHash = null;
        String[] paths = {"data.transactionHash", "data.data.transactionHash", "transactionHash"};
        for (String path : paths) {
            txHash = prepareResp.jsonPath().getString(path);
            if (txHash != null && !txHash.isBlank()) break;
        }
        if (txHash == null || txHash.isBlank()) {
            System.out.println("FAILED: transactionHash missing in prepare response.");
            System.out.println("Body: " + prepareResp.getBody().asString());
            return;
        }
        System.out.println("Transaction Hash: " + txHash);

        // Sign
        var safeSig = signatureService.signSafeApproval(sigServerUrl, txHash, privateKey);
        if (safeSig == null || !safeSig.isOk()) {
            System.out.println("FAILED: Safe approval signing failed.");
            return;
        }
        System.out.println("Safe signature: " + safeSig.getSignature().substring(0, 20) + "...");

        // Execute
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(prepareResp.getBody().asString());
            JsonNode prepareData = root.path("data").path("data");
            if (prepareData.isMissingNode()) prepareData = root.path("data");

            Response execResp = enableTradingService.execute(accessToken, refreshCookie, prepareData, safeSig.getSignature());
            System.out.println("Execute status: " + execResp.getStatusCode());
            if (execResp.getStatusCode() == 200) {
                System.out.println("\n=== SUCCESS: Trading enabled for " + walletAddress + " ===");
            } else {
                String body = execResp.getBody().asString();
                if (body.contains("trading is already enabled")) {
                    System.out.println("\nTrading is ALREADY ENABLED. Done!");
                } else {
                    System.out.println("FAILED: " + body);
                }
            }
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static void setIfBlank(String key, String value) {
        String current = System.getProperty(key);
        if (current == null || current.isBlank()) {
            System.setProperty(key, value);
        }
    }
}
