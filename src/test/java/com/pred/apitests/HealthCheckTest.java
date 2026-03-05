package com.pred.apitests;

import com.pred.apitests.base.BaseApiTest;
import io.restassured.RestAssured;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

/**
 * Placeholder test to verify framework runs. Replace with real API coverage later.
 * Disabled by default — enable when you have a real health/status endpoint.
 */
public class HealthCheckTest extends BaseApiTest {

    @Test(enabled = false, description = "GET health/status returns 200 or 204. Enable when health endpoint is available.")
    public void healthEndpointResponds() {
        RestAssured
                .given()
                .when()
                .get("/health")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(204)));
    }
}
