package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * User and platform health endpoints.
 * GET /api/v1/user/me, GET /api/v1/user/maintainance.
 */
public class UserService extends BaseService {

    private static final String ME_PATH = "/api/v1/user/me";
    private static final String MAINTAINANCE_PATH = "/api/v1/user/maintainance";

    /**
     * GET {publicBase}/api/v1/user/me
     * Returns user profile: id, pred_uid, is_enabled_trading, status, wallet addresses.
     * Requires: Authorization Bearer token (and Cookie for refresh).
     */
    public Response getMe(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(ME_PATH);
    }

    /**
     * GET {publicBase}/api/v1/user/maintainance
     * Returns { downtime: false }. Used as platform health check.
     * Requires: Authorization Bearer token (and Cookie for refresh).
     */
    public Response getMaintainance(String accessToken, String refreshCookie) {
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().get(MAINTAINANCE_PATH);
    }
}
