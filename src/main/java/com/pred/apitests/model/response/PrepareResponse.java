package com.pred.apitests.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrepareResponse {

    @JsonProperty("data")
    private JsonNode data;

    public JsonNode getData() { return data; }
    public void setData(JsonNode data) { this.data = data; }

    /** Transaction hash from data.transactionHash. */
    public String getTransactionHash() {
        if (data == null || !data.has("transactionHash")) return null;
        return data.get("transactionHash").asText();
    }
}
