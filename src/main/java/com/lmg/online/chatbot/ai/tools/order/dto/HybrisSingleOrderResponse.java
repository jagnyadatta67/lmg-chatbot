package com.lmg.online.chatbot.ai.tools.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Maps the raw Hybris single-order response from:
 *   GET /landmarkshopscommercews/v2/{siteId}/en/users/{userId}/orders/{orderNo}
 *       ?orderRevamp=true&appId=...&position=0&fields=DEFAULT
 *
 * Only the fields the chatbot actually uses are mapped.
 * Everything else is silently ignored via @JsonIgnoreProperties.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HybrisSingleOrderResponse {

    /** Order code / number e.g. "9419396447" */
    @JsonProperty("code")
    private String code;

    /** Machine-readable status e.g. "SUBMITTED_TO_WAREHOUSE" */
    @JsonProperty("status")
    private String status;

    /** Human-readable status e.g. "Placed" */
    @JsonProperty("statusDisplay")
    private String statusDisplay;

    /** ISO timestamp when order was placed e.g. "2026-02-10T13:10:31+0530" */
    @JsonProperty("created")
    private String created;

    @JsonProperty("totalPrice")
    private PriceData totalPrice;

    @JsonProperty("deliveryAddress")
    private DeliveryAddress deliveryAddress;

    @JsonProperty("entries")
    private List<HybrisEntry> entries;

    // ─── Nested DTOs ──────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceData {
        @JsonProperty("value")
        private Double value;

        @JsonProperty("formattedValue")
        private String formattedValue;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeliveryAddress {
        @JsonProperty("firstName")
        private String firstName;

        @JsonProperty("cellphone")
        private String cellphone;

        @JsonProperty("line1")
        private String line1;

        @JsonProperty("town")
        private String town;

        @JsonProperty("postalCode")
        private String postalCode;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HybrisEntry {

        @JsonProperty("entryNumber")
        private Integer entryNumber;

        @JsonProperty("orderEntryPk")
        private String orderEntryPk;

        @JsonProperty("quantity")
        private Integer quantity;

        /** Product size from the entry level e.g. "XXL" */
        @JsonProperty("productSize")
        private String productSize;

        @JsonProperty("exchangeEnabled")
        private boolean exchangeEnabled;

        @JsonProperty("returnEligible")
        private boolean returnEligible;

        /** TAT message e.g. "Delivery within 1-3 days" */
        @JsonProperty("orderEntryTatMessage")
        private String orderEntryTatMessage;

        /** User-facing delivery message e.g. "Delivery within 3-6 days" */
        @JsonProperty("orderEntryMessage")
        private String orderEntryMessage;

        /** Exchange TAT window e.g. "1-3" (days) */
        @JsonProperty("exchangeDeliveryEstimateTatData")
        private String exchangeDeliveryEstimateTatData;

        @JsonProperty("totalPrice")
        private PriceData totalPrice;

        @JsonProperty("product")
        private HybrisProduct product;

        @JsonProperty("consignmentStatus")
        private ConsignmentStatusData consignmentStatus;

        // ── Nested product ────────────────────────────────────────────────────

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class HybrisProduct {

            @JsonProperty("code")
            private String code;

            @JsonProperty("name")
            private String name;

            /** Relative product page path e.g. "/Men/Sports-Wear/.../p/1234" */
            @JsonProperty("url")
            private String url;

            @JsonProperty("images")
            private List<ImageData> images;

            @JsonProperty("returnableProduct")
            private boolean returnableProduct;

            @JsonProperty("variants")
            private List<VariantData> variants;

            @Data
            @NoArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ImageData {
                @JsonProperty("url")
                private String url;
            }

            @Data
            @NoArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class VariantData {
                @JsonProperty("color")
                private String color;

                @JsonProperty("size")
                private String size;
            }
        }

        // ── Consignment / shipment status ─────────────────────────────────────

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ConsignmentStatusData {

            @JsonProperty("consignmentCode")
            private String consignmentCode;

            @JsonProperty("consignmentHistoryData")
            private List<ConsignmentHistory> consignmentHistoryData;

            @Data
            @NoArgsConstructor
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ConsignmentHistory {
                @JsonProperty("status")
                private String status;

                @JsonProperty("formattedDate")
                private String formattedDate;

                @JsonProperty("isReturnStatus")
                private boolean isReturnStatus;
            }
        }
    }
}
