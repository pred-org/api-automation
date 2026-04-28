package com.pred.apitests.util;

import com.pred.apitests.config.Config;
import com.pred.apitests.service.MarketDiscoveryService;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton cache for market data discovered from canonical name.
 * Call init() once before tests. All getters return data from cache.
 */
public final class MarketContext {

    private static MarketContext instance;

    private String canonicalName;
    private String fixtureId;
    private final Map<String, ParentMarket> parentMarketsByFamily = new LinkedHashMap<>();
    private final List<ParentMarket> allParentMarkets = new ArrayList<>();

    private MarketContext() {}

    public static synchronized MarketContext getInstance() {
        if (instance == null) {
            instance = new MarketContext();
        }
        return instance;
    }

    /**
     * Discover and cache all markets for the configured CANONICAL_NAME.
     * Safe to call multiple times — only discovers once.
     */
    public synchronized void init() {
        if (canonicalName != null) {
            return;
        }

        String cname = Config.getCanonicalName();
        if (cname == null || cname.isBlank()) {
            if (Config.isAutomationFixtureBootstrapEnabled()) {
                AutomationFixtureBootstrap.ensureCanonicalName();
                cname = Config.getCanonicalName();
            }
        }
        if (cname == null || cname.isBlank()) {
            throw new IllegalStateException("CANONICAL_NAME not set in .env or system properties "
                    + "(enable AUTOMATION_FIXTURE_BOOTSTRAP or set CANONICAL_NAME)");
        }

        MarketDiscoveryService discoveryService = new MarketDiscoveryService();
        Response response = discoveryService.discoverByCanonicalName(cname);
        if (response.getStatusCode() != 200) {
            throw new IllegalStateException("Market discovery failed for cname=" + cname
                    + " status=" + response.getStatusCode()
                    + " body=" + response.getBody().asString());
        }

        JsonPath json = response.jsonPath();
        this.canonicalName = cname;

        String fid = json.getString("data.parent_markets_list[0].fixture.fixture_id");
        this.fixtureId = fid != null ? fid : "";

        List<Map<String, Object>> parentMarketsList = json.getList("data.parent_markets_list");
        if (parentMarketsList == null || parentMarketsList.isEmpty()) {
            throw new IllegalStateException("No parent markets found for cname=" + cname);
        }

        for (int i = 0; i < parentMarketsList.size(); i++) {
            String basePath = "data.parent_markets_list[" + i + "]";

            String parentMarketId = json.getString(basePath + ".parent_market_data.parent_market_id");
            String family = json.getString(basePath + ".parent_market_data.parent_market_family");
            String title = json.getString(basePath + ".parent_market_data.title");
            String status = json.getString(basePath + ".parent_market_data.status");
            String marketLine = json.getString(basePath + ".parent_market_data.market_line");

            List<Map<String, Object>> marketsRaw = json.getList(basePath + ".markets");
            List<SubMarket> subMarkets = new ArrayList<>();
            if (marketsRaw != null) {
                for (int j = 0; j < marketsRaw.size(); j++) {
                    String mPath = basePath + ".markets[" + j + "]";
                    subMarkets.add(new SubMarket(
                            json.getString(mPath + ".market_id"),
                            json.getString(mPath + ".name"),
                            json.getString(mPath + ".status")
                    ));
                }
            }

            ParentMarket pm = new ParentMarket(parentMarketId, family, title, status, marketLine, subMarkets);
            allParentMarkets.add(pm);

            if (family != null && !parentMarketsByFamily.containsKey(family) && !subMarkets.isEmpty()) {
                parentMarketsByFamily.put(family, pm);
            }
        }

        System.out.println("[MarketContext] Discovered " + allParentMarkets.size()
                + " parent markets for cname=" + cname + " fixtureId=" + fixtureId);
        System.out.println("[MarketContext] Families with sub-markets: " + parentMarketsByFamily.keySet());
    }

    /**
     * Try to discover markets from CANONICAL_NAME; on failure logs and falls back to MARKET_ID via {@link #getDefaultMarketId()}.
     */
    public static String resolveMarketId() {
        try {
            getInstance().init();
        } catch (Exception e) {
            System.out.println("MarketContext init skipped: " + e.getMessage());
        }
        return getInstance().getDefaultMarketId();
    }

    /**
     * Parent market id for URL paths: /api/v1/order/{parent}/place, cancel, cancel/all, orderbook/{sub}.
     * Sub-market id for body and EIP-712 questionId comes from {@link #resolveMarketId()}.
     */
    public static String resolveParentMarketIdForPath() {
        try {
            getInstance().init();
        } catch (Exception e) {
            System.out.println("MarketContext init skipped: " + e.getMessage());
        }
        return getInstance().getDefaultParentMarketId();
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public String getFixtureId() {
        return fixtureId;
    }

    /** Get the first parent market for a given family (moneyline, spreads, totals, btts). */
    public ParentMarket getFirstParentMarket(String family) {
        return parentMarketsByFamily.get(family);
    }

    /** Get all parent markets across all families. */
    public List<ParentMarket> getAllParentMarkets() {
        return Collections.unmodifiableList(allParentMarkets);
    }

    /** Get the moneyline parent market (most common for tests). */
    public ParentMarket getMoneylineParent() {
        return parentMarketsByFamily.get("moneyline");
    }

    /**
     * Convenience: get the first sub-market ID under moneyline.
     * Falls back to Config.getMarketId() if discovery has not run or moneyline missing.
     */
    public String getDefaultMarketId() {
        ParentMarket ml = getMoneylineParent();
        if (ml != null && !ml.getSubMarkets().isEmpty()) {
            return ml.getSubMarkets().get(0).getMarketId();
        }
        return Config.getMarketId();
    }

    /**
     * Convenience: get the moneyline parent_market_id.
     * Used in the URL path for order placement.
     */
    public String getDefaultParentMarketId() {
        ParentMarket ml = getMoneylineParent();
        return ml != null ? ml.getParentMarketId() : Config.getMarketId();
    }

    /** Check if a specific family exists and has sub-markets. */
    public boolean hasFamilyWithMarkets(String family) {
        return parentMarketsByFamily.containsKey(family);
    }

    public static class ParentMarket {
        private final String parentMarketId;
        private final String family;
        private final String title;
        private final String status;
        private final String marketLine;
        private final List<SubMarket> subMarkets;

        public ParentMarket(String parentMarketId, String family, String title, String status, String marketLine,
                            List<SubMarket> subMarkets) {
            this.parentMarketId = parentMarketId;
            this.family = family;
            this.title = title;
            this.status = status;
            this.marketLine = marketLine;
            this.subMarkets = subMarkets;
        }

        public String getParentMarketId() {
            return parentMarketId;
        }

        public String getFamily() {
            return family;
        }

        public String getTitle() {
            return title;
        }

        public String getStatus() {
            return status;
        }

        public String getMarketLine() {
            return marketLine;
        }

        public List<SubMarket> getSubMarkets() {
            return subMarkets;
        }
    }

    public static class SubMarket {
        private final String marketId;
        private final String name;
        private final String status;

        public SubMarket(String marketId, String name, String status) {
            this.marketId = marketId;
            this.name = name;
            this.status = status;
        }

        public String getMarketId() {
            return marketId;
        }

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }
    }
}
