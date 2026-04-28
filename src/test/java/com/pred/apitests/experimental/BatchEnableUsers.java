package com.pred.apitests.experimental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pred.apitests.config.Config;
import com.pred.apitests.model.request.LoginRequest;
import com.pred.apitests.model.response.SignCreateProxyResponse;
import com.pred.apitests.service.AuthService;
import com.pred.apitests.service.DepositService;
import com.pred.apitests.service.EnableTradingService;
import com.pred.apitests.service.SignatureService;
import io.restassured.response.Response;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch enable trading for multiple users.
 * Reads private keys from a file (one per line), authenticates each, enables trading.
 *
 * Usage:
 *   mvn compile test-compile exec:java \
 *     -Dexec.mainClass="com.pred.apitests.experimental.BatchEnableUsers" \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="keys.txt"
 *
 * keys.txt format (one private key per line, no 0x prefix needed):
 *   4bde9ff00468d0109793e3c3910b8f5f3bf43bbf8682f30a6a42307eeaea8556
 *   634a4e32089613a0a1fb3bc51372630956859cc40ba90db23b1a7c48f2a857c4
 *   ...
 *
 * Output: prints summary to console and writes batch-enable-results.csv
 */
public class BatchEnableUsers {

    private static final AuthService authService = new AuthService();
    private static final SignatureService signatureService = new SignatureService();
    private static final EnableTradingService enableTradingService = new EnableTradingService();
    private static final DepositService depositService = new DepositService();

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: BatchEnableUsers <keys-file>");
            System.out.println("  keys-file: path to file with one private key per line");
            return;
        }

        // Init config (loads .env)
        String sigServerUrl = Config.getSigServerUrl();
        String baseUrl = Config.getPublicBaseUri();
        String internalUrl = Config.getInternalBaseUri();
        System.out.println("API Base: " + baseUrl);
        System.out.println("Internal: " + internalUrl);
        System.out.println("Sig Server: " + sigServerUrl);

        // Read keys
        Path keysFile = Paths.get(args[0]);
        List<String> keys = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(keysFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // Strip 0x prefix if present
                if (line.startsWith("0x") || line.startsWith("0X")) line = line.substring(2);
                keys.add(line);
            }
        } catch (Exception e) {
            System.out.println("ERROR reading keys file: " + e.getMessage());
            return;
        }

        System.out.println("Loaded " + keys.size() + " private keys\n");
        int estSeconds = keys.size() * 15;
        String estTime = estSeconds < 60 ? estSeconds + " seconds"
                : estSeconds < 3600 ? (estSeconds / 60) + " minutes"
                : (estSeconds / 3600) + "h " + ((estSeconds % 3600) / 60) + "m";
        System.out.println("Estimated time: ~" + estTime + " (sequential, ~15s per user)\n");

        // Process each key
        List<String[]> results = new ArrayList<>(); // [privateKey, eoa, userId, proxyWallet, status]
        int success = 0;
        int failed = 0;
        int alreadyEnabled = 0;

        for (int i = 0; i < keys.size(); i++) {
            String pk = keys.get(i);
            String shortKey = pk.substring(0, 8) + "...";
            System.out.println("=== User " + (i + 1) + "/" + keys.size() + " [" + shortKey + "] ===");

            String[] result = processUser(pk, sigServerUrl, i + 1, keys.size());
            results.add(result);

            String status = result[4];
            if ("SUCCESS".equals(status) || "ALREADY_ENABLED".equals(status)) {
                success++;
                if ("ALREADY_ENABLED".equals(status)) alreadyEnabled++;
            } else {
                failed++;
            }
            System.out.println("  → " + status + "\n");
        }

        // Summary
        System.out.println("========================================");
        System.out.println("BATCH COMPLETE: " + keys.size() + " users");
        System.out.println("  Success: " + success + " (already enabled: " + alreadyEnabled + ")");
        System.out.println("  Failed:  " + failed);
        System.out.println("========================================\n");

        // Write CSV
        String csvFile = "batch-enable-results.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
            pw.println("private_key,eoa,user_id,proxy_wallet,status");
            for (String[] r : results) {
                pw.println(String.join(",", r));
            }
            System.out.println("Results written to " + csvFile);
        } catch (Exception e) {
            System.out.println("ERROR writing CSV: " + e.getMessage());
        }
    }

    private static String[] processUser(String privateKey, String sigServerUrl, int index, int total) {
        String eoa = "";
        String userId = "";
        String proxyWallet = "";
        String shortKey = privateKey.length() >= 8 ? privateKey.substring(0, 8) + "..." : privateKey;
        String progress = "[" + index + "/" + total + " " + shortKey + "]";
        try {
            // Step 1: Create API key
            String apiKey = null;
            Response keyResp = authService.createApiKey();
            if (keyResp.getStatusCode() == 200 || keyResp.getStatusCode() == 201) {
                apiKey = authService.parseApiKey(keyResp);
            }
            if (apiKey == null || apiKey.isBlank()) {
                return new String[]{privateKey, "", "", "", "FAILED:api_key_creation"};
            }
            System.out.println("  " + progress + " API key created");

            // Step 2: Sign create proxy
            SignCreateProxyResponse sigResp = signatureService.signCreateProxy(sigServerUrl, privateKey);
            if (sigResp == null || !sigResp.isOk()) {
                return new String[]{privateKey, "", "", "", "FAILED:sign_create_proxy"};
            }
            eoa = sigResp.getWalletAddress();
            System.out.println("  " + progress + " EOA: " + eoa);

            // Step 3: Login
            long now = System.currentTimeMillis();
            LoginRequest loginReq = LoginRequest.builder()
                    .walletAddress(eoa)
                    .signature(sigResp.getSignature())
                    .message("Sign in to PRED Trading Platform")
                    .nonce("nonce-" + now + "-" + (now / 1000))
                    .chainType("base-sepolia")
                    .timestamp(now / 1000)
                    .build();

            Response loginResp = authService.login(apiKey, loginReq);
            if (loginResp.getStatusCode() != 200) {
                return new String[]{privateKey, eoa, "", "", "FAILED:login_" + loginResp.getStatusCode()};
            }

            String accessToken = authService.extractAccessTokenFromResponse(loginResp);
            String refreshCookie = loginResp.getHeader("Set-Cookie");
            userId = firstNonBlank(
                    loginResp.jsonPath().getString("user_id"),
                    loginResp.jsonPath().getString("data.user_id"),
                    loginResp.jsonPath().getString("data.data.user_id"));
            proxyWallet = firstNonBlank(
                    loginResp.jsonPath().getString("proxy_wallet_address"),
                    loginResp.jsonPath().getString("data.proxy_wallet_address"),
                    loginResp.jsonPath().getString("data.data.proxy_wallet_address"));

            if (accessToken == null || proxyWallet == null) {
                return new String[]{privateKey, eoa, userId != null ? userId : "", "", "FAILED:missing_token_or_proxy"};
            }
            System.out.println("  " + progress + " Logged in: userId=" + userId + " proxy=" + proxyWallet);

            // Step 4: Enable trading
            Response prepareResp = null;
            int prepareStatus = -1;
            for (int attempt = 1; attempt <= 3; attempt++) {
                prepareResp = enableTradingService.prepare(accessToken, refreshCookie, proxyWallet);
                prepareStatus = prepareResp.getStatusCode();
                String body = prepareResp.getBody().asString();
                if (prepareStatus == 500 && body.contains("trading is already enabled")) {
                    System.out.println("  " + progress + " Trading already enabled");
                    doDeposit(userId, String.valueOf(Config.getDepositAmount()), shortKey, index, total);
                    return new String[]{privateKey, eoa, userId, proxyWallet, "ALREADY_ENABLED"};
                }
                if (prepareStatus == 500 && body.contains("no contract code")) {
                    System.out.println("  Safe not deployed, retry " + attempt + "/3...");
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
                if (prepareStatus == 200) break;
            }
            if (prepareStatus != 200) {
                return new String[]{privateKey, eoa, userId, proxyWallet, "FAILED:prepare_" + prepareStatus};
            }

            // Get txHash
            String txHash = null;
            for (String path : new String[]{"data.transactionHash", "data.data.transactionHash", "transactionHash"}) {
                txHash = prepareResp.jsonPath().getString(path);
                if (txHash != null && !txHash.isBlank()) break;
            }
            if (txHash == null || txHash.isBlank()) {
                return new String[]{privateKey, eoa, userId, proxyWallet, "FAILED:no_txHash"};
            }

            // Sign safe approval
            var safeSig = signatureService.signSafeApproval(sigServerUrl, txHash, privateKey);
            if (safeSig == null || !safeSig.isOk()) {
                return new String[]{privateKey, eoa, userId, proxyWallet, "FAILED:safe_sign"};
            }

            // Execute
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(prepareResp.getBody().asString());
            JsonNode prepareData = root.path("data").path("data");
            if (prepareData.isMissingNode()) prepareData = root.path("data");

            Response execResp = enableTradingService.execute(accessToken, refreshCookie, prepareData, safeSig.getSignature());
            if (execResp.getStatusCode() == 200) {
                System.out.println("  " + progress + " Trading enabled!");
                doDeposit(userId, String.valueOf(Config.getDepositAmount()), shortKey, index, total);
                return new String[]{privateKey, eoa, userId, proxyWallet, "SUCCESS"};
            }
            String execBody = execResp.getBody().asString();
            if (execBody.contains("trading is already enabled")) {
                System.out.println("  " + progress + " Trading already enabled");
                doDeposit(userId, String.valueOf(Config.getDepositAmount()), shortKey, index, total);
                return new String[]{privateKey, eoa, userId, proxyWallet, "ALREADY_ENABLED"};
            }
            return new String[]{privateKey, eoa, userId, proxyWallet, "FAILED:execute_" + execResp.getStatusCode()};

        } catch (Exception e) {
            return new String[]{privateKey, eoa, userId, proxyWallet, "FAILED:" + e.getMessage()};
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static void doDeposit(String userId, String depositAmount, String shortKey, int index, int total) {
        String progress = "[" + index + "/" + total + " " + shortKey + "]";
        try {
            Response depositResp = depositService.internalDeposit(userId, Long.parseLong(depositAmount));
            if (depositResp.getStatusCode() == 200 || depositResp.getStatusCode() == 201) {
                String txHash = firstNonBlank(
                        depositResp.jsonPath().getString("transaction_hash"),
                        depositResp.jsonPath().getString("data.transaction_hash"));
                if (txHash != null && !txHash.isBlank()) {
                    depositService.cashflowDeposit(userId, txHash);
                }
                System.out.println("  " + progress + " Deposited");
            } else {
                System.out.println("  " + progress + " WARNING: deposit failed " + depositResp.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("  " + progress + " WARNING: deposit error " + e.getMessage());
        }
    }
}
