package com.pred.apitests.filters;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Logs request (URI, headers, body) and response (status, body). Masks Authorization and token headers in logs.
 */
public class LoggingFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String MASK = "****";

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {
        logRequest(requestSpec);
        Response response = ctx.next(requestSpec, responseSpec);
        logResponse(response);
        return response;
    }

    private void logRequest(FilterableRequestSpecification req) {
        if (!LOG.isDebugEnabled()) return;
        String base = req.getBaseUri() != null ? req.getBaseUri() : "";
        String path = req.getBasePath() != null ? req.getBasePath() : "";
        LOG.debug("Request: {} {}{}", req.getMethod(), base, path);
        List<Header> headers = req.getHeaders().asList();
        for (Header h : headers) {
            String value = isSensitive(h.getName()) ? MASK : h.getValue();
            LOG.debug("  Header: {}: {}", h.getName(), value);
        }
        if (req.getBody() != null && req.getBody().toString() != null) {
            LOG.debug("  Body: {}", maskTokenInBody(req.getBody().toString()));
        }
    }

    private void logResponse(Response response) {
        if (!LOG.isDebugEnabled()) return;
        LOG.debug("Response: status={}", response.getStatusCode());
        try {
            String body = response.getBody().asString();
            if (body != null && !body.isBlank()) {
                LOG.debug("  Body: {}", body.length() > 500 ? body.substring(0, 500) + "..." : body);
            }
        } catch (Exception ignored) {
            LOG.debug("  Body: [could not read]");
        }
    }

    private boolean isSensitive(String headerName) {
        if (headerName == null) return false;
        String lower = headerName.toLowerCase();
        return lower.equals("authorization") || lower.contains("token") || lower.contains("api-key");
    }

    private String maskTokenInBody(String body) {
        if (body == null) return "";
        return body.replaceAll("(\"(?:access_token|signature|token|privateKey|private_key)\"\\s*:\\s*\")[^\"]*", "$1" + MASK);
    }
}
