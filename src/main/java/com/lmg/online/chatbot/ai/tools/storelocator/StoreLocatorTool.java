package com.lmg.online.chatbot.ai.tools.storelocator;

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreList;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreLocatorAPIResponse;
import com.lmg.online.chatbot.ai.tools.storelocator.helper.StoreLocatorHelper;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@AllArgsConstructor
public class StoreLocatorTool
{
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;
    @Tool(
            name = "fetchStoreLocator",
            description = """
            Fetches nearby store locations for a given concept and environment.
            Uses Landmark's Store Locator API to return details like store ID, name,
            city, address, and contact number.
            
            Requires:
            - concept: Concept name (e.g., LIFESTYLE, MAX, BABYSHOP, HOMECENTRE)
            - env: Environment prefix (e.g., uat5, stg, prod)
            - userId: Optional user token (used for personalization)
            -lat : latitude from user
            - lng : longitude from user 
            -appId for appid (e.g., 'Mobile', 'Desktop','ANDROID', 'IPHONE' )
            Returns: A list of StoreView
            """
    )

    public StoreList fetchStoreLocator(String concept, String env, String userId,double lat,double lng,String appId) {
        // Build API URL dynamically
        String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, "/en/storeLocator/",appId);
        // Prepare headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try{
            StoreLocatorAPIResponse res=  authenticationServiceUtil.callWithAuthRetry(appId,url,HttpMethod.POST,headers,null,StoreLocatorAPIResponse.class,env).getBody();
            return mapStores(res,lat,lng);

        } catch (Exception e) {
            throw new RuntimeException("Error fetching store locator details: " + e.getMessage(), e);
        }
    }



    public static StoreList mapStores(StoreLocatorAPIResponse response,double lat ,double lng) {
       return StoreLocatorHelper.getNearestStores(response,lat,lng,10);
    }


}