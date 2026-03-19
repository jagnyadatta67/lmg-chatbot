package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from the chatbot delivery-detail endpoint:
 *   GET /landmarkshopscommercews/v2/{siteId}/chatbot/user/{userId}/delivery
 *       ?appId=...&orderNo=...&entryPk=...
 *
 * Returned by ORDER_ENTRY_INTENT — carries per-item consignment status,
 * return initiation details, and exchange/return eligibility.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEntryDetailResponse {

    /** Set by tool on error — surfaced to the user as a friendly message */
    @JsonProperty("chat_message")
    private String chatMessage;

    @JsonProperty("orderEntryPk")
    private String orderEntryPk;

    @JsonProperty("orderNo")
    private String orderNo;

    @JsonProperty("productCode")
    private String productCode;

    @JsonProperty("productImageUrl")
    private String productImageUrl;

    @JsonProperty("productUrl")
    private String productUrl;

    /** Whether this entry can be exchanged */
    @JsonProperty("exchangeable")
    private boolean exchangeable;

    /** Whether this entry can be returned */
    @JsonProperty("returnable")
    private boolean returnable;

    /** One element per physical shipment for this entry */
    @JsonProperty("item_details")
    private List<ItemDetail> itemDetails;

    // ─── Nested DTOs ──────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemDetail {

        @JsonProperty("position")
        private Integer position;

        @JsonProperty("quantity")
        private Integer quantity;

        /** Current fulfilment status e.g. "DELIVERED", "SHIPPED" */
        @JsonProperty("status")
        private String status;

        @JsonProperty("consignment")
        private ConsignmentInfo consignment;

        /** Null when no return has been initiated yet */
        @JsonProperty("returnDetail")
        private ReturnDetail returnDetail;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConsignmentInfo {

        /** ISO timestamp when item was actually delivered */
        @JsonProperty("actualDeliveryDate")
        private String actualDeliveryDate;

        @JsonProperty("consignmentCode")
        private String consignmentCode;

        /** e.g. "DELIVERED", "SHIPPED", "OUT_FOR_DELIVERY" */
        @JsonProperty("consignmentStatus")
        private String consignmentStatus;

        /** true → delivery breached the promised TAT — show warning to user */
        @JsonProperty("deliveryTatBreached")
        private boolean deliveryTatBreached;

        /** true → dispatch is running later than expected */
        @JsonProperty("dispatchDelayed")
        private boolean dispatchDelayed;

        @JsonProperty("quantityShipped")
        private Integer quantityShipped;

        /** true → customer can no longer raise a return on this item */
        @JsonProperty("returnWindowExpired")
        private boolean returnWindowExpired;

        /** ISO timestamp when item left the warehouse */
        @JsonProperty("shippedDate")
        private String shippedDate;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReturnDetail {

        /** Link to brand return FAQ */
        @JsonProperty("faqLink")
        private String faqLink;

        /** ISO timestamp when return was raised */
        @JsonProperty("returnCreationDate")
        private String returnCreationDate;

        /** Return request ID e.g. "05264001" */
        @JsonProperty("returnId")
        private String returnId;

        @JsonProperty("returnInitiated")
        private boolean returnInitiated;

        /** true → pickup is delayed — show warning to user */
        @JsonProperty("returnPickupDelayed")
        private boolean returnPickupDelayed;

        /** e.g. "WAIT", "PICKED", "REFUNDED" */
        @JsonProperty("returnStatus")
        private String returnStatus;

        /** Return Merchandise Authorization number */
        @JsonProperty("rma")
        private String rma;
    }
}
