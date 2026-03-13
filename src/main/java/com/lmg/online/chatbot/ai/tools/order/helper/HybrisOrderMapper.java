package com.lmg.online.chatbot.ai.tools.order.helper;

import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse.HybrisEntry;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse.HybrisEntry.ConsignmentStatusData.ConsignmentHistory;
import com.lmg.online.chatbot.ai.tools.order.dto.HybrisSingleOrderResponse.HybrisEntry.HybrisProduct;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderDetail;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure data mapper — converts the raw Hybris single-order response into
 * the chatbot's {@link OrderResponse} DTO without any AI involvement.
 *
 * Field mapping:
 * <pre>
 *   Hybris field                              → OrderDetail / OrderResponse field
 *   ─────────────────────────────────────────────────────────────────────────────
 *   code                                      → orderNo
 *   statusDisplay / status                    → orderStatus
 *   created                                   → orderDate
 *   totalPrice.value                          → orderAmount
 *   entries.size()                            → totalProducts
 *   deliveryAddress.firstName                 → customerName
 *   deliveryAddress.cellphone                 → mobileNo
 *   entry.product.name                        → productName
 *   entry.product.images[0].url               → imageURL
 *   entry.product.url  (abs)                  → productURL
 *   entry.totalPrice.formattedValue           → netAmount
 *   entry.product.variants[0].color           → color
 *   entry.productSize                         → size
 *   entry.quantity                            → qty
 *   entry.orderEntryMessage / TatMessage      → tat
 *   last consignmentHistoryData[].status      → latestStatus
 *   entry.product.returnableProduct           → returnAllow
 *   entry.exchangeEnabled                     → exchangeAllow
 *   entry.exchangeDeliveryEstimateTatData     → exchangeDay
 * </pre>
 */
public class HybrisOrderMapper {

    private HybrisOrderMapper() { /* utility class */ }

    /**
     * Maps a {@link HybrisSingleOrderResponse} to {@link OrderResponse}.
     *
     * @param hybris  the Hybris API response (may be {@code null} on API error)
     * @param concept brand code — used to build absolute URLs
     * @param env     environment prefix — used to build absolute URLs
     * @return populated {@link OrderResponse}, never {@code null}
     */
    public static OrderResponse toOrderResponse(HybrisSingleOrderResponse hybris,
                                                String concept, String env) {
        OrderResponse response = new OrderResponse();

        if (hybris == null) {
            response.setChat_message("Unable to fetch order details right now. "
                    + ConceptBaseUrlResolver.getPhoneNumber(concept));
            response.setOrderDetailsList(Collections.emptyList());
            return response;
        }

        // ── Customer info from delivery address ───────────────────────────────
        if (hybris.getDeliveryAddress() != null) {
            response.setCustomerName(hybris.getDeliveryAddress().getFirstName());
            response.setMobileNo(sanitiseMobile(hybris.getDeliveryAddress().getCellphone()));
        }

        // ── Build one OrderDetail per order entry ─────────────────────────────
        List<HybrisEntry> entries =
                hybris.getEntries() != null ? hybris.getEntries() : Collections.emptyList();

        List<OrderDetail> details = new ArrayList<>();
        for (HybrisEntry entry : entries) {
            OrderDetail detail = new OrderDetail();

            // Order-level fields (same for every entry)
            detail.setOrderNo(hybris.getCode());
            detail.setOrderStatus(
                    hybris.getStatusDisplay() != null ? hybris.getStatusDisplay() : hybris.getStatus());
            detail.setOrderDate(hybris.getCreated());
            detail.setOrderAmount(
                    hybris.getTotalPrice() != null ? hybris.getTotalPrice().getValue() : null);
            detail.setTotalProducts(entries.size());

            // Product info
            HybrisProduct product = entry.getProduct();
            if (product != null) {
                detail.setProductName(product.getName());
                detail.setImageURL(resolveImageUrl(product));
                detail.setProductURL(resolveProductUrl(product, concept, env));
                detail.setColor(resolveColor(product, entry));
                detail.setReturnAllow(product.isReturnableProduct());
            }

            // Entry-level fields
            detail.setNetAmount(
                    entry.getTotalPrice() != null ? entry.getTotalPrice().getFormattedValue() : null);
            detail.setSize(resolveSize(entry));
            detail.setQty(entry.getQuantity() != null ? String.valueOf(entry.getQuantity()) : "1");
            detail.setTat(resolveTat(entry));
            detail.setLatestStatus(resolveLatestStatus(entry));
            detail.setExchangeAllow(entry.isExchangeEnabled());
            detail.setExchangeDay(entry.getExchangeDeliveryEstimateTatData());
            detail.setReturnAllow(entry.isReturnEligible()
                    || (product != null && product.isReturnableProduct()));

            details.add(detail);
        }

        if (details.isEmpty()) {
            response.setChat_message("No items found for this order.");
        }

        response.setOrderDetailsList(details);
        return response;
    }

    // ── Field-resolution helpers ──────────────────────────────────────────────

    /** Returns the first available image URL from the product's images list. */
    private static String resolveImageUrl(HybrisProduct product) {
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            String url = product.getImages().get(0).getUrl();
            if (url != null && !url.isBlank()) return url;
        }
        return null;
    }

    /**
     * Returns the absolute product URL.
     * If the product URL is relative (starts with "/"), it is prefixed with
     * the concept's React base URL via {@link ConceptBaseUrlResolver#buildReactUrl}.
     */
    private static String resolveProductUrl(HybrisProduct product, String concept, String env) {
        String url = product.getUrl();
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http")) return url;
        return ConceptBaseUrlResolver.buildReactUrl(concept, env, url);
    }

    /** Returns the color from variants[0], or from entry-level if absent. */
    private static String resolveColor(HybrisProduct product, HybrisEntry entry) {
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            return product.getVariants().get(0).getColor();
        }
        return null;
    }

    /**
     * Returns the size — prefers entry-level productSize over variant.
     */
    private static String resolveSize(HybrisEntry entry) {
        if (entry.getProductSize() != null && !entry.getProductSize().isBlank()) {
            return entry.getProductSize();
        }
        HybrisProduct product = entry.getProduct();
        if (product != null && product.getVariants() != null && !product.getVariants().isEmpty()) {
            return product.getVariants().get(0).getSize();
        }
        return null;
    }

    /**
     * Returns the TAT / delivery message.
     * Prefers the user-facing orderEntryMessage, falls back to orderEntryTatMessage.
     */
    private static String resolveTat(HybrisEntry entry) {
        if (entry.getOrderEntryMessage() != null && !entry.getOrderEntryMessage().isBlank()) {
            return entry.getOrderEntryMessage();
        }
        return entry.getOrderEntryTatMessage();
    }

    /**
     * Returns the latest consignment status by taking the LAST item in
     * consignmentHistoryData (most recent event).
     */
    private static String resolveLatestStatus(HybrisEntry entry) {
        if (entry.getConsignmentStatus() == null) return null;
        List<ConsignmentHistory> history =
                entry.getConsignmentStatus().getConsignmentHistoryData();
        if (history == null || history.isEmpty()) return null;
        // Last item = most recent status
        ConsignmentHistory last = history.get(history.size() - 1);
        return last.getStatus();
    }

    /** Strips the leading "+" country code prefix from the mobile number if present. */
    private static String sanitiseMobile(String cellphone) {
        if (cellphone == null) return null;
        return cellphone.startsWith("+") ? cellphone.substring(1) : cellphone;
    }
}
