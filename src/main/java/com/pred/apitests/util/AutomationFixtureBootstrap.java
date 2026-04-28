package com.pred.apitests.util;

import com.pred.apitests.config.Config;
import com.pred.apitests.service.MarketDiscoveryService;
import io.restassured.response.Response;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * When {@link Config#isAutomationFixtureBootstrapEnabled()} is true, resolves a canonical name
 * by reusing only-for-automation-1, only-for-automation-2, ... with successful market discovery.
 * Does not create fixtures. Thread-safe (single-flight).
 */
public final class AutomationFixtureBootstrap {

    private static final Object LOCK = new Object();

    private AutomationFixtureBootstrap() {}

    /**
     * Resolves a usable canonical name. Returns null if bootstrap is disabled, no reusable cname, or failed (caller may fall back to MARKET_ID).
     */
    public static String ensureCanonicalName() {
        if (!Config.isAutomationFixtureBootstrapEnabled()) {
            return null;
        }
        synchronized (LOCK) {
            String prefix = Config.getAutomationCnamePrefix().trim();
            if (prefix.isEmpty()) {
                return null;
            }
            int max = Config.getAutomationCnameMaxScan();

            MarketDiscoveryService discovery = new MarketDiscoveryService();
            if (tryReuseExisting(discovery, prefix, max)) {
                return Config.getCanonicalName();
            }

            System.out.println("[AutomationFixtureBootstrap] No reusable only-for-automation-N market found. "
                    + "Create one manually via CMS if needed. Skipping bootstrap.");
            return null;
        }
    }

    private static boolean tryReuseExisting(MarketDiscoveryService discovery, String prefix, int max) {
        String marker = Config.getAutomationTitleMarker();
        for (int i = 1; i <= max; i++) {
            String cname = prefix + "-" + i;
            if (!isDiscoverableWithMarkets(discovery, cname)) {
                continue;
            }
            if (marker != null && !marker.isBlank() && !titleMatchesMarker(discovery, cname, marker)) {
                continue;
            }
            System.out.println("[AutomationFixtureBootstrap] Reusing existing cname=" + cname);
            System.setProperty("canonical.name", cname);
            return true;
        }
        return false;
    }

    private static boolean isDiscoverableWithMarkets(MarketDiscoveryService discovery, String cname) {
        Response r = discovery.discoverByCanonicalName(cname);
        if (r.getStatusCode() != 200) {
            return false;
        }
        List<?> list = r.jsonPath().getList("data.parent_markets_list");
        return list != null && !list.isEmpty();
    }

    private static boolean titleMatchesMarker(MarketDiscoveryService discovery, String cname, String marker) {
        Response r = discovery.discoverByCanonicalName(cname);
        if (r.getStatusCode() != 200) {
            return false;
        }
        String m = marker.trim().toLowerCase(Locale.ROOT);
        List<?> rows = r.jsonPath().getList("data.parent_markets_list");
        if (rows == null) {
            return false;
        }
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) rowObj;
            @SuppressWarnings("unchecked")
            Map<String, Object> pmd = (Map<String, Object>) row.get("parent_market_data");
            if (pmd != null) {
                Object title = pmd.get("title");
                if (title != null && String.valueOf(title).toLowerCase(Locale.ROOT).contains(m)) {
                    return true;
                }
            }
        }
        return false;
    }
}
