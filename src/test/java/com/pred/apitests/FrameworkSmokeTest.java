package com.pred.apitests;

import com.pred.apitests.config.Config;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RestAssured and config load. No HTTP call — just framework smoke.
 */
public class FrameworkSmokeTest {

    @Test(description = "ApiConfig provides base URI")
    public void configLoads() {
        String base = Config.getPublicBaseUri();
        assertThat(base).isNotBlank();
    }
}
