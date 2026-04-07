package com.pred.apitests.util;

import io.restassured.response.Response;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * Central place for response schema validation. Use alongside existing field assertions for coverage.
 */
public final class SchemaValidator {

    private SchemaValidator() {}

    /**
     * Asserts the response body matches the given JSON schema from classpath.
     *
     * @param response   RestAssured response (e.g. from getBalance, getPositions).
     * @param schemaFile Filename under schemas/ (e.g. "balance-response.json").
     */
    public static void assertMatchesSchema(Response response, String schemaFile) {
        response.then().body(matchesJsonSchemaInClasspath("schemas/" + schemaFile));
    }
}
