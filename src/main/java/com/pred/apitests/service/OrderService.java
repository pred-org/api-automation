package com.pred.apitests.service;

import com.pred.apitests.base.BaseService;
import com.pred.apitests.model.request.PlaceOrderRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Place order, cancel order, and orderbook (public) APIs.
 */
public class OrderService extends BaseService {

    private static final String PLACE_ORDER_PATH_TEMPLATE = "/api/v1/order/%s/place";
    private static final String CANCEL_ORDER_PATH_TEMPLATE = "/api/v1/order/%s/cancel";
    private static final String CANCEL_ALL_ORDERS_PATH_TEMPLATE = "/api/v1/order/%s/cancel/all";
    private static final String ORDERBOOK_PATH_TEMPLATE = "/api/v1/order/%s/orderbook/%s";

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

    /**
     * DELETE {publicBase}/api/v1/order/{marketId}/cancel
     * Body: order_id (UUID), market_id (hex).
     */
    public Response cancelOrder(String accessToken, String refreshCookie, String marketId, String orderId) {
        String path = String.format(CANCEL_ORDER_PATH_TEMPLATE, marketId);
        Map<String, String> body = Map.of(
                "order_id", orderId,
                "market_id", marketId
        );
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.body(body).when().delete(path);
    }

    /**
     * DELETE {publicBase}/api/v1/order/{marketId}/cancel/all
     * Cancels all open orders for the given market. No request body.
     */
    public Response cancelAllOrders(String accessToken, String refreshCookie, String marketId) {
        String path = String.format(CANCEL_ALL_ORDERS_PATH_TEMPLATE, marketId);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie);
        return spec.when().delete(path);
    }

    /**
     * DELETE {publicBase}/api/v1/order/{marketId}/cancel/all
     * Cancels all open orders for the given market. No request body.
     * Uses extended read timeout since cancelling multiple orders server-side can take longer.
     */
    public Response cancelAllOrders(String accessToken, String refreshCookie, String marketId, int readTimeoutMs) {
        String path = String.format(CANCEL_ALL_ORDERS_PATH_TEMPLATE, marketId);
        RequestSpecification spec = givenWithAuthAndCookie(getPublicBaseUri(), accessToken, refreshCookie)
                .config(io.restassured.config.RestAssuredConfig.config()
                        .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                                .setParam("http.socket.timeout", readTimeoutMs)));
        return spec.when().delete(path);
    }

    /**
     * GET {publicBase}/api/v1/order/{parentMarketId}/orderbook/{subMarketId}
     * Public (no auth). Returns bids[], asks[], metadata: { spread, mid_price, total_bid_quantity, total_ask_quantity }.
     */
    public Response getOrderbook(String parentMarketId, String subMarketId) {
        String path = String.format(ORDERBOOK_PATH_TEMPLATE, parentMarketId, subMarketId);
        RequestSpecification spec = given(getPublicBaseUri());
        return spec.when().get(path);
    }
}
