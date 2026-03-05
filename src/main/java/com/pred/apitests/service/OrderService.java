package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import com.pred.apitests.model.request.PlaceOrderRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Place order API.
 */
public class OrderService extends BaseService {

    private static final String PLACE_ORDER_PATH_TEMPLATE = "/api/v1/order/%s/place";

    /**
     * POST {publicBase}/api/v1/order/{marketId}/place
     * Headers: Authorization, Cookie, X-Wallet-Address, X-Proxy-Address.
     */
    public Response placeOrder(String accessToken, String refreshCookie, String eoa, String proxyAddress, String marketId, PlaceOrderRequest body) {
        String path = String.format(PLACE_ORDER_PATH_TEMPLATE, marketId);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie)
                .header("X-Wallet-Address", eoa)
                .header("X-Proxy-Address", proxyAddress);
        return spec.body(body).when().post(path);
    }
}
