package com.lmg.online.chatbot.ai.tools.chatbotorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A single product entry inside a chatbot order.
 *
 * Handles two image/URL shapes:
 *  - chatbot endpoint:  "productImage" and "productUrl" are direct string fields
 *  - standard Hybris:   nested under "product" → { "url": "...", "images": [{ "url": "..." }] }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatbotOrderEntry {

    @JsonProperty("orderEntryPk")
    private String orderEntryPk;

    @JsonProperty("entryNumber")
    private Integer entryNumber;

    @JsonProperty("position")
    private Integer position;

    @JsonProperty("productCode")
    private String productCode;

    /** Direct product name — populated by the chatbot-simplified endpoint */
    @JsonProperty("productName")
    private String productName;

    /** Direct image URL — populated by the chatbot-simplified endpoint */
    @JsonProperty("productImage")
    private String productImage;

    /** Direct product page URL — populated by the chatbot-simplified endpoint */
    @JsonProperty("productUrl")
    private String productUrl;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("exchangeable")
    private boolean exchangeable;

    @JsonProperty("returnable")
    private boolean returnable;

    /**
     * Nested product object — populated by the standard Hybris order API.
     * Contains "url" and "images[].url" when the chatbot endpoint fields are absent.
     */
    @JsonProperty("product")
    private ProductData product;

    // ─── Nested DTOs ──────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductData {

        @JsonProperty("code")
        private String code;

        @JsonProperty("name")
        private String name;

        /** Relative product page path, e.g. "/Men/Sports-Wear/.../p/1234" */
        @JsonProperty("url")
        private String url;

        @JsonProperty("images")
        private List<ImageData> images;

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ImageData {
            @JsonProperty("url")
            private String url;
        }
    }

    // ─── Derived helpers (used by widget serialisation) ───────────────────────

    /**
     * Returns the best available product name:
     * 1. Direct "productName" from the chatbot endpoint
     * 2. "product.name" from the standard Hybris API
     */
    public String getResolvedProductName() {
        if (productName != null && !productName.isBlank()) return productName;
        if (product != null && product.getName() != null && !product.getName().isBlank()) {
            return product.getName();
        }
        return null;
    }

    /**
     * Returns the best available product image URL:
     * 1. Direct "productImage" from the chatbot endpoint
     * 2. First image from "product.images[].url" from the standard Hybris API
     */
    public String getResolvedProductImage() {
        if (productImage != null && !productImage.isBlank()) return productImage;
        if (product != null && product.getImages() != null && !product.getImages().isEmpty()) {
            String url = product.getImages().get(0).getUrl();
            if (url != null && !url.isBlank()) return url;
        }
        return null;
    }

    /**
     * Returns the best available product page URL:
     * 1. Direct "productUrl" from the chatbot endpoint
     * 2. "product.url" from the standard Hybris API
     */
    public String getResolvedProductUrl() {
        if (productUrl != null && !productUrl.isBlank()) return productUrl;
        if (product != null && product.getUrl() != null && !product.getUrl().isBlank()) {
            return product.getUrl();
        }
        return null;
    }
}
