package com.lmg.online.chatbot.ai.tools.order;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;
import com.lmg.online.chatbot.ai.tools.order.helper.OrderDataProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class OrderTrackingTool {
    @Autowired
   private AuthenticationServiceUtil authenticationServiceUtil;

    public OrderTrackingTool() {
    }



    @Tool(
            name = "getOrderStatus",
            description = """
        Fetches active order details for an authenticated customer.
        
        Parameters:
        - userId (required): The customer's user ID for authentication
        - concept (required): Business concept/domain (e.g., 'retail', 'grocery')
        - env (required): Environment identifier (e.g., 'prod', 'staging')
        - appid (required) : exactly from caller part like (e.g., 'Mobile', 'Desktop','ANDROID', 'IPHONE' "
        Returns:
        Structured order information containing:
        - Customer name and contact details
        - List of active orders with:
          * Order number and status
          * Order date and amount
          * Product count and items
          * Delivery information
        
        Usage example:
        getOrderStatus("USER123", "MAX", "prod")
        
        Note: All parameters are mandatory. Ensure userId is authenticated before calling.
        """
    )
    public OrderResponse getOrderStatus(
            @ToolParam(required = true, description = "Customer's unique user identifier")
            String userId,

            @ToolParam(required = true, description = "Business concept/domain (e.g., 'MAX', 'LIFESTYLE')")
            String concept,

            @ToolParam(required = true, description = "Environment identifier (e.g., 'prod', 'staging')")
            String env,
            @ToolParam(required = true, description = "appid for apps  (e.g., 'Mobile', 'Desktop','ANDROID', 'IPHONE' ")
            String appid
    ) {
        log.info("Toll called for ordertracking with appid {}",appid);
        Map<String, String> queryParams=new HashMap<>();
        queryParams.put("orderRefineCode","12");
        String url= ConceptBaseUrlResolver.buildApiUrl(concept,env,"/en/orders/",appid,queryParams);

           HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", userId);
        try {
        OrderResponse res=  authenticationServiceUtil.callWithAuthRetry(appid,url,HttpMethod.GET,headers,null,OrderResponse.class,env).getBody();
         log.info("Order resp {}",res.toString());
        OrderDataProcessor.enrichOrderDetails(res,concept,env);
        return res;
        } catch (Exception e) {
            log.error("❌ Error in order tracking for concept {}: {}", concept, e.getMessage(), e);

            OrderResponse response = new OrderResponse();
            response.setChat_message(
                    String.format(
                            "Please contact our customer care for more details: We are currently unable to serve your request. Call %s for assistance.",
                            ConceptBaseUrlResolver.getPhoneNumber(concept)
                    )
            );
            return response;
        }
    }
    }



