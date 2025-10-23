package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OrderDetail {
    @JsonProperty(required = true, value = "orderAmount")
    private Double orderAmount;
    @JsonProperty(required = true, value = "orderDate")
    private String orderDate;
    @JsonProperty(required = true, value = "orderNo")
    private String orderNo;
    @JsonProperty(required = true, value = "orderStatus")
    private String orderStatus;
    @JsonProperty(required = true, value = "totalProducts")
    private Integer totalProducts;
    @JsonProperty(required = true, value = "productName")
    private String productName;
    @JsonProperty(required = true, value = "imageURL")
    private String       imageURL;
    @JsonProperty(required = true, value = "productURL")
    private String productURL;
    @JsonProperty(required = true, value = "netAmount")
    private String       netAmount;
    @JsonProperty(required = true, value = "color")
    private String       color;
    @JsonProperty(required = true, value = "size")
    private String       size;
    @JsonProperty(required = true, value = "qty")
    private String       qty;
    @JsonProperty(required = true, value = "tat")
    private String      tat;
    @JsonProperty(required = true, value = "estmtDate")
    private String  estmtDate;
    @JsonProperty(required = true, value = "latestStatus")
    private String       latestStatus;
    @JsonProperty(required = true, value = "returnAllow")
    private boolean returnAllow;
    @JsonProperty(required = true, value = "exchangeAllow")
    private boolean    exchangeAllow;
    @JsonProperty(required = true, value = "exchangeDay")
    private String     exchangeDay;

}