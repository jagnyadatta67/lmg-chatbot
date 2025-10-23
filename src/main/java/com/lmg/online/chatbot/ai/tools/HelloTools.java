package com.lmg.online.chatbot.ai.tools;

import com.lmg.online.chatbot.ai.tools.order.dto.OrderDetail;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class HelloTools {
    @Autowired
    private RestTemplate restTemplate;

    @Tool(description = """
        Get all active/completed orders for a customer by mobile number.
        Use when user asks: "show my orders", "order history", "all orders"
        """)
    public OrderResponse getOrderStatus(String mobileNo) {
        // Your existing implementation
        return callApi("/getActiveOrder", mobileNo);
    }

    @Tool(description = """
        Get cancelled orders for a customer by mobile number.
        Use when user asks: "cancelled orders", "orders I cancelled", "show cancellations"
        """)
    public OrderResponse getCancelledOrders(String mobileNo) {
        return callApi("/getCancelledOrders", mobileNo);
    }

    @Tool(description = """
        Get returned orders for a customer by mobile number.
        Use when user asks: "returned orders", "my returns", "return history"
        """)
    public OrderResponse getReturnedOrders(String mobileNo) {
        return callApi("/getReturnedOrders", mobileNo);
    }

    @Tool(description = """
        Get in-progress/pending orders for a customer by mobile number.
        Use when user asks: "pending orders", "orders in progress", "current orders"
        """)
    public OrderResponse getInProgressOrders(String mobileNo) {
        return callApi("/getInProgressOrders", mobileNo);
    }

    @Tool(description = """
        Get order details by specific order number.
        Use when user provides order number like: "track order 9419110481", "status of order 9419110481"
        """)
    public OrderDetail getOrderByNumber(String orderNo) {
       // return callApiForSingleOrder("/getOrderByNumber", orderNo);
        return new OrderDetail();
    }

    // Common API call method
    private OrderResponse callApi(String endpoint, String mobileNo) {
        String url = "https://uat1.lifestylestores.com/landmarkshopscommercews/v2/lifestylein/chatBotManagement/orders"
                + endpoint + "?mobileNo=" + mobileNo + "&appId=ANDROID";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("access_token", "ac35228a-asd0c0");

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, OrderResponse.class
            );

            OrderResponse orderResponse = response.getBody();

            // Sanitize to prevent XSS
            if (orderResponse != null && orderResponse.getCustomerName() != null) {
                String sanitized = orderResponse.getCustomerName().replaceAll("<[^>]*>", "");
                return new OrderResponse();

            }

            return orderResponse;
        } catch (Exception e) {
            throw new RuntimeException("Error fetching orders: " + e.getMessage());
        }
    }
}

