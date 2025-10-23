package com.lmg.online.chatbot.ai.tools.user;


import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.user.dto.UserWsDTO;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@AllArgsConstructor
public class MyProfileDetailsTool
{
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;
    @Tool(
            name = "customerProfile",
            description = """
             get The current user profile details my profile get my details for profile userId is mandatory filed 
             
            - concept: Concept name (e.g., LIFESTYLE, MAX, BABYSHOP, HOMECENTRE)
            - env: Environment prefix (e.g., uat5, stg, prod)
            - token:  user token (used for personalization)
            - appId: most important attribute  for apps  (e.g., 'Mobile', 'Desktop','ANDROID', 'IPHONE'
           
            Returns: A list of UserWsDTO
            """
    )

    public UserWsDTO fetchStoreLocator(String concept, String env,String userId,String appId) {
        // Build API URL dynamically
        String url = ConceptBaseUrlResolver.buildApiUrl(concept, env, "/chatBotManagement/we",appId);

        // Prepare headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", userId); // optional if userId availabl
        try{
        UserWsDTO res=  authenticationServiceUtil.callWithAuthRetry(appId,url,HttpMethod.GET,headers,null,UserWsDTO.class,env).getBody();
        return res;

        } catch (Exception e) {
            throw new RuntimeException("Error fetching store locator details: " + e.getMessage(), e);
        }
    }



}