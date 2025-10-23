package com.lmg.online.chatbot.ai.tools.order.helper;



import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderDetail;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;

import java.util.Objects;

public class OrderDataProcessor {

    public static void enrichOrderDetails(OrderResponse response, String conceptCode, String envPrefix) {
        if (response == null || response.getOrderDetailsList() == null) return;

        for (OrderDetail detail : response.getOrderDetailsList()) {
            // --- Ensure orderNo format is standardized
            if (detail.getOrderNo() != null && !detail.getOrderNo().startsWith(conceptCode.substring(0, 2))) {
                detail.setOrderNo(ConceptBaseUrlResolver.buildReactUrl(conceptCode,envPrefix,"my-account/order/"+detail.getOrderNo()+"?iS=false&p=0"));
            }

            // --- Safely rebuild product URL using ConceptBaseUrlResolver
            if (Objects.nonNull(detail.getProductURL()) && !detail.getProductURL().startsWith("http")) {
                         detail.setProductURL(ConceptBaseUrlResolver.buildReactUrl(conceptCode,envPrefix,"/p/"+detail.getProductURL()));
            }


        }
    }
}
