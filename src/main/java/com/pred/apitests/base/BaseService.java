package com.pred.apitests.base;

import com.pred.apitests.config.Config;
import com.pred.apitests.filters.LoggingFilter;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Single HTTP wrapper over RestAssured. Holds base URI, default headers, registers LoggingFilter.
 * All services use this for HTTP; no raw given/when in tests.
 */
public abstract class BaseService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    static {
        RestAssured.filters(new LoggingFilter());
        RestAssured.config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", CONNECT_TIMEOUT_MS)
                        .setParam("http.socket.timeout", READ_TIMEOUT_MS));
    }

    protected static RequestSpecification given(String baseUri) {
        return RestAssured.given()
                .baseUri(baseUri)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    protected static RequestSpecification given(String baseUri, Headers headers) {
        return given(baseUri).headers(headers);
    }

    protected static RequestSpecification given(String baseUri, Map<String, String> headers) {
        return given(baseUri).headers(headers);
    }

    protected static RequestSpecification givenWithAuth(String baseUri, String bearerToken) {
        return given(baseUri).header("Authorization", "Bearer " + bearerToken);
    }

    /** Bearer + Cookie: refresh_token=... (all authenticated requests must send the refresh cookie). */
    protected static RequestSpecification givenWithAuthAndCookie(String baseUri, String bearerToken, String refreshCookieValue) {
        RequestSpecification spec = given(baseUri).header("Authorization", "Bearer " + bearerToken);
        if (refreshCookieValue != null && !refreshCookieValue.isBlank()) {
            spec = spec.header("Cookie", refreshCookieValue);
        }
        return spec;
    }

    public static String getPublicBaseUri() {
        return Config.getPublicBaseUri();
    }

    public static String getInternalBaseUri() {
        return Config.getInternalBaseUri();
    }

    protected Response get(String baseUri, String path) {
        return given(baseUri).when().get(path);
    }

    protected Response get(String baseUri, String path, Map<String, String> headers) {
        return given(baseUri, headers).when().get(path);
    }

    protected Response post(String baseUri, String path, Object body) {
        return given(baseUri).body(body).when().post(path);
    }

    protected Response post(String baseUri, String path, Object body, Map<String, String> headers) {
        return given(baseUri, headers).body(body).when().post(path);
    }

    protected Response put(String baseUri, String path, Object body) {
        return given(baseUri).body(body).when().put(path);
    }

    protected Response delete(String baseUri, String path) {
        return given(baseUri).when().delete(path);
    }
}
