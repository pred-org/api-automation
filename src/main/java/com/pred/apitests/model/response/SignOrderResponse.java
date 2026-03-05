package com.pred.apitests.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SignOrderResponse {

    private boolean ok;
    private String signature;

    @JsonProperty("eoa_address")
    private String eoaAddress;

    private String maker;
    private String signer;

    @JsonProperty("signed_message")
    private Map<String, Object> signedMessage;

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getEoaAddress() { return eoaAddress; }
    public void setEoaAddress(String eoaAddress) { this.eoaAddress = eoaAddress; }
    public String getMaker() { return maker; }
    public void setMaker(String maker) { this.maker = maker; }
    public String getSigner() { return signer; }
    public void setSigner(String signer) { this.signer = signer; }
    public Map<String, Object> getSignedMessage() { return signedMessage; }
    public void setSignedMessage(Map<String, Object> signedMessage) { this.signedMessage = signedMessage; }
}
