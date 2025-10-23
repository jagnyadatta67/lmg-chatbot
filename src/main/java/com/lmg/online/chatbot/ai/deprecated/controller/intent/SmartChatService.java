package com.lmg.online.chatbot.ai.deprecated.controller.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.analytics.TokenCostCalculator;
import com.lmg.online.chatbot.ai.analytics.TokenUsage;
import com.lmg.online.chatbot.ai.business.docreader.SimpleInMemoryVectorStore;
import com.lmg.online.chatbot.ai.tools.giftcard.GiftCardBalanceTool;
import com.lmg.online.chatbot.ai.tools.giftcard.dto.GiftCardBalanceResponse;
import com.lmg.online.chatbot.ai.tools.order.OrderTrackingTool;
import com.lmg.online.chatbot.ai.tools.order.dto.OrderResponse;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.storelocator.StoreLocatorTool;
import com.lmg.online.chatbot.ai.tools.storelocator.dto.StoreList;

import com.lmg.online.chatbot.ai.tools.user.MyProfileDetailsTool;
import com.lmg.online.chatbot.ai.tools.user.dto.CustomerProfileResponseDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.lmg.online.chatbot.ai.deprecated.controller.intent.ChatIntent.CUSTOMER_PROFILE;


@Slf4j
@Service
public class SmartChatService {

    // Lightweight intent patterns - no AI needed for basic classification
    private static final Pattern CUSTOMER_PROFILE_PATTERN = Pattern.compile(
            ".*\\b(profile|my\\s*profile|account|my\\s*account|personal\\s*details|my\\s*details|about\\s*me|user\\s*info|my\\s*info|update\\s*profile|edit\\s*profile)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ORDER_PATTERN = Pattern.compile(
            ".*\\b(order|track|delivery|shipment|status|where.*order)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORE_PATTERN = Pattern.compile(
            ".*\\b(store|shop|outlet|location|branch|nearest|nearby|address|find store)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern POLICY_PATTERN = Pattern.compile(
            ".*\\b(return|exchange|refund|policy|shipping|cancel|how to)\\b.*", Pattern.CASE_INSENSITIVE);

    // Minimal format instructions
    private static final String ORDER_FORMAT = """
        Return JSON: {"chat_message":"text","customerName":"name","mobileNo":"phone",
        "orderDetailsList":[{"orderAmount":0,"orderDate":"date","orderNo":"num","orderStatus":"status",
        "totalProducts":0,"productName":"name","imageURL":"url","productURL":"url","netAmount":"amt",
        "color":"col","size":"sz","qty":"q","tat":"t","estmtDate":"date","latestStatus":"st",
        "returnAllow":false,"exchangeAllow":false,"exchangeDay":"days"}]}
        """;


    private static final String CUSTOMER_PROFILE_FORMAT = """
        Return JSON: {
            "chat_message": "text", 
            "customerProfile": {
                "name": "full name",
                "firstName": "first name",
                "lastName": "last name",
                "email": "email address",
                "mobileNo": "phone number",
                "uid": "unique user id",
                "gender": "MALE/FEMALE/OTHER",
                "loyaltyCardNumber": "card number",
               
                "signInMobile": "+91xxxxxxxxxx",
                
                "defaultAddress": {
                    "addressType": "Home/Office",
                    "firstName": "address first name",
                    "email": "address email",
                    "cellphone": "mobile number",
                    "line1": "address line 1",
                    "line2": "address line 2",
                    "landmark": "landmark text",
                    "town": "city/town",
                    "region": {
                        
                        "name": "KARNATAKA"
                    },
                    "country": {
                     
                        "name": "India"
                    },
                    "postalCode": "560037",
                    
                }
            }
        }
        """;

    private static final String STORE_FORMAT = """
        Return JSON: {"stores":[{"storeId":"id","storeName":"name","city":"city","address":"addr",
        "contactNumber":"num","workingHours":"hrs","latitude":0.0,"longitude":0.0,"distance":0.0,
        "line1":"l1","line2":"l2","postalCode":"code"}]}
        """;

    private static final String LOGIN_FORMAT = """
    Anonymous user, for order check please login to your account. If this message we will receive then 
    response must exact as 
     "chat_message": Please sign in to continue — once you’re logged in, I can fetch your latest details.
    """;

    private final ChatClient intentClassifierClient;
    private final ChatClient policyClient;

    @Resource
    @Qualifier("orderTrackClient")
    private ChatClient orderTrackClient;


    @Resource
    @Qualifier("customerProfile")
    private ChatClient customerProfile;

    @Resource
    @Qualifier("storeLocator")
    private ChatClient storeLocatorClient;

    @Resource
    private StoreLocatorTool storeLocatorTool;

    private final SimpleInMemoryVectorStore vectorStore;
    private final OrderTrackingTool orderTrackingTool;
    private final MyProfileDetailsTool myProfileDetailsTool ;
    private final BeanOutputConverter<OrderResponse> orderOutputConverter;
    private final BeanOutputConverter<CustomerProfileResponseDTO> userWsDTOBeanOutputConverter;
    private final BeanOutputConverter<StoreList> storeLocatorConverter;
    private final BeanOutputConverter<IntentClassification> intentOutputConverter;

    private final TokenCostCalculator tokenCostCalculator;
    private final AiAnalyticsService aiAnalyticsService;
    private final ObjectMapper objectMapper;

    private final BeanOutputConverter<GiftCardBalanceResponse> giftCardBalanceResponseConverter;
    private final GiftCardBalanceTool giftCardBalanceTool;
    @Autowired
    @Qualifier("giftCardClient")
    private  ChatClient giftCardClient;

    public SmartChatService(ChatClient.Builder chatClientBuilder,
                            SimpleInMemoryVectorStore vectorStore,
                            OrderTrackingTool orderTrackingTool,
                            MyProfileDetailsTool myProfileDetailsTool, BeanOutputConverter<CustomerProfileResponseDTO> userWsDTOBeanOutputConverter, BeanOutputConverter<StoreList> storeLocatorConverter,
                            BeanOutputConverter<GiftCardBalanceResponse> giftCardBalanceResponseConverter, GiftCardBalanceTool giftCardBalanceTool, TokenCostCalculator tokenCostCalculator,
                            AiAnalyticsService aiAnalyticsService,
                            ObjectMapper objectMapper) {

        this.intentClassifierClient = chatClientBuilder.build();
        this.policyClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.orderTrackingTool = orderTrackingTool;
        this.myProfileDetailsTool = myProfileDetailsTool;
        this.userWsDTOBeanOutputConverter = userWsDTOBeanOutputConverter;
        this.storeLocatorConverter = storeLocatorConverter;
        this.giftCardBalanceResponseConverter = giftCardBalanceResponseConverter;
        this.giftCardBalanceTool = giftCardBalanceTool;
        this.tokenCostCalculator = tokenCostCalculator;
        this.aiAnalyticsService = aiAnalyticsService;
        this.objectMapper = objectMapper;
        this.orderOutputConverter = new BeanOutputConverter<>(OrderResponse.class);
        this.intentOutputConverter = new BeanOutputConverter<>(IntentClassification.class);

        log.info("✅ SmartChatService initialized with optimized prompting");
    }

    /**
     * Main entry - Uses regex-based routing to avoid extra AI calls
     */
    public ChatbotResponse<?> handleUserQuery(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String query = request.getMessage().toLowerCase();

       /* // Fast pattern-based routing (no AI call needed)
        if (ORDER_PATTERN.matcher(query).matches()) {
            return handleOrderTracking(request, startTime);
        } else if (STORE_PATTERN.matcher(query).matches()) {
            return handleStoreLocator(request, startTime);
        } else if (POLICY_PATTERN.matcher(query).matches()) {
            return handlePolicyQuestion(request, startTime);
        } else {*/
            // Only use AI classifier for ambiguous queries
            IntentClassification intent = classifyIntentLightweight(query);
            return switch (intent.intent().toUpperCase()) {
                case "ORDER_TRACKING" -> handleOrderTracking(request, startTime);
                case "STORE_LOCATOR" -> handleStoreLocator(request, startTime);
                case "POLICY_QUESTION" -> handlePolicyQuestion(request, startTime);
                case "CUSTOMER_PROFILE" -> handleProfileQuestion(request, startTime);
                case "GIFT_CARD_BALANCE" -> handleGiftCardBBalance(request, startTime);
                default -> handleGeneralQuery(request, startTime);
            };

    }
    private static final String GIFT_CARD_BALANCE_FORMAT = """
    Return JSON:
    {
      "chat_message": "text",
      "giftCardDetails": {
        "cardNumber": "string",
        "status": "SUCCESS or FAILED",
        "message": "string",
        "balanceAmount": 0.0,
        "currency": "INR or other"
      }
    }

    Description:
    • Use this format only when the user asks to check or verify a gift card balance.
    • Inputs: concept, env, accessToken, appId, cardNumber, and pin.
    • Respond with balance, currency, and status.
    • Ignore unrelated queries.
    """;


    private ChatbotResponse<?> handleGiftCardBBalance(ChatRequest req, long startTime) {


       String prompt = String.format(
                "%s\nQuery: %s\nCall giftCardBalance(concept=%s,env=%s,accessToken=%s,appId=%s,cardNumber=%s,pin=%s) chat_message should be empty if giftCardBalance is used " + LOGIN_FORMAT,
                GIFT_CARD_BALANCE_FORMAT, req.getMessage(), req.getConcept(), req.getEnv(), req.getUserId(), req.getAppid(), req.getCardNumber(), req.getPin()
        );

        ChatResponse response = giftCardClient.prompt()
                .user(prompt)
                .tools(giftCardBalanceTool)
                .call()
                .chatResponse();

        return buildGiftCardResponse(response, req, startTime);
    }

    private ChatbotResponse<GiftCardBalanceResponse> buildGiftCardResponse(ChatResponse resp, ChatRequest req, long start) {
        long time = System.currentTimeMillis() - start;

        // Convert LLM output to DTO
        GiftCardBalanceResponse data = giftCardBalanceResponseConverter.convert(
                resp.getResult().getOutput().getText()
        );

        // Token usage calculation
        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                resp.getMetadata().getUsage(),
                resp.getMetadata().getModel()
        );

        // Track analytics
        trackAnalytics(req, "GIFT_CARD_BALANCE", "GIFT_CARD_BALANCE",
                resp, time, "GIFT_CARD_BALANCE", true);

        // Build final chatbot response
        return ChatbotResponse.<GiftCardBalanceResponse>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(time)
                .intent("GIFT_CARD_BALANCE")
                .build();
    }

    private ChatbotResponse<?> handleProfileQuestion(ChatRequest req, long startTime) {


    log.info("📦 CUSTOMER_PROFILE");

    boolean isAuth = req.getUserId() != null && !req.getUserId().isEmpty();
    String prompt;

    if (!isAuth) {
        // No tool call needed - direct response
        prompt =  "\nUser not logged in asking about my profile. " +
                "Response: 'Please log in to view order details.' Set orderDetailsList=[]";
    } else {
        // Tool call with minimal context
        prompt = String.format(
                "%s\nQuery: %s\nCall orderTrackingTool(concept=%s,env=%s,userId=%s,appid=%s) chat_message should empty if  orderTrackingTool used "+LOGIN_FORMAT,
                CUSTOMER_PROFILE_FORMAT, req.getMessage(), req.getConcept(), req.getEnv(), req.getUserId(),req.getAppid()
        );
    }

    ChatResponse response = customerProfile.prompt()
            .user(prompt)
            .tools(isAuth ? myProfileDetailsTool : null)
            .call()
            .chatResponse();

        return buildCustomerProfile(response, req, startTime);
}



    /**
     * Lightweight intent classifier - only for ambiguous queries
     */
    private IntentClassification classifyIntentLightweight(String query) {


        String prompt = String.format(
                "Classify intent: %s\nOptions: ORDER_TRACKING, STORE_LOCATOR, POLICY_QUESTION, GENERAL_QUERY,CUSTOMER_PROFILE,GIFT_CARD_BALANCE\n%s",
                query, intentOutputConverter.getFormat()
        );

        String json = intentClassifierClient.prompt().user(prompt).call().content();
        return intentOutputConverter.convert(json);
    }

    /**
     * ORDER TRACKING - Optimized prompt
     */
    private ChatbotResponse<OrderResponse> handleOrderTracking(ChatRequest req, long start) {
        log.info("📦 ORDER_TRACKING");

        boolean isAuth = req.getUserId() != null && !req.getUserId().trim().isEmpty();
        String prompt;
        ChatResponse response=null;
        if (!isAuth) {
            // No tool call needed - direct response
            prompt = ORDER_FORMAT + "\nUser not logged in asking about orders. " +
                    "Response: 'Please log in to view order details.' Set orderDetailsList=[]";
            response = orderTrackClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

        } else {
            // Tool call with minimal context
            prompt = String.format(
                    "%s\n Query: %s\n Call orderTrackingTool(concept=%s,env=%s,userId=%s,appId=%s ) chat_message should empty if  orderTrackingTool used "+LOGIN_FORMAT,
                    ORDER_FORMAT, req.getMessage(), req.getConcept(), req.getEnv(), req.getUserId(),req.getAppid()

            );
            response = orderTrackClient.prompt()
                    .user(prompt)
                    .tools( orderTrackingTool)
                    .call()
                    .chatResponse();
        }




        return buildOrderResponse(response, req, start);
    }

    /**
     * STORE LOCATOR - Optimized prompt
     */
    private ChatbotResponse<StoreList> handleStoreLocator(ChatRequest req, long start) {
        log.info("🏪 STORE_LOCATOR");

        String prompt = String.format(
                "%s\nQuery: %s\nCall storeLocatorTool(concept=%s,env=%s,lat=%s,lng=%s)",
                STORE_FORMAT, req.getMessage(), req.getConcept(), req.getEnv(),
                req.getLatitude(), req.getLongitude()
        );

        ChatResponse response = storeLocatorClient.prompt()
                .user(prompt)
                .tools(storeLocatorTool)
                .call()
                .chatResponse();

        return buildStoreResponse(response, req, start);
    }

    /**
     * POLICY QUESTION - RAG with minimal prompting
     */
    private ChatbotResponse<String> handlePolicyQuestion(ChatRequest req, long start) {
        log.info("📋 POLICY_QUESTION");

        List<Document> docs = vectorStore.similaritySearch(req.getMessage(), 3); // Reduced from 5
        String context = docs.stream()
                .map(Document::getFormattedContent)
                .limit(3)
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "Context:\n%s\n\nQ: %s\nA: Answer from context only. If unclear, say: 'Call 1800-123-1555 for details.'",
                context, req.getMessage()
        );

        ChatResponse response = policyClient.prompt().user(prompt).call().chatResponse();
        return buildStringResponse(response, req, start, "POLICY_QUESTION");
    }

    /**
     * GENERAL QUERY - Minimal prompt
     */
    private ChatbotResponse<String> handleGeneralQuery(ChatRequest req, long start) {
        log.info("💬 GENERAL_QUERY");

        String prompt = String.format(
                "Q: %s\nProvide brief help. Suggest: order tracking, store locator, policies. Call: 1800-123-1555",
                req.getMessage()
        );

        ChatResponse response = policyClient.prompt().user(prompt).call().chatResponse();
        return buildStringResponse(response, req, start, "GENERAL_QUERY");
    }

    // ========== RESPONSE BUILDERS ==========

    private ChatbotResponse<OrderResponse> buildOrderResponse(ChatResponse resp, ChatRequest req, long start) {
        long time = System.currentTimeMillis() - start;
        OrderResponse data = orderOutputConverter.convert(resp.getResult().getOutput().getText());
        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                resp.getMetadata().getUsage(), resp.getMetadata().getModel());

        trackAnalytics(req, data.getCustomerName(), data.getMobileNo(),
                resp, time, "ORDER_TRACKING", true);

        return ChatbotResponse.<OrderResponse>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(time)
                .intent("ORDER_TRACKING")
                .build();
    }
    private ChatbotResponse<CustomerProfileResponseDTO> buildCustomerProfile(ChatResponse resp, ChatRequest req, long start) {
        long time = System.currentTimeMillis() - start;
        CustomerProfileResponseDTO data = userWsDTOBeanOutputConverter.convert(resp.getResult().getOutput().getText());
        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                resp.getMetadata().getUsage(), resp.getMetadata().getModel());

        trackAnalytics(req,CUSTOMER_PROFILE.name() , CUSTOMER_PROFILE.name(),
                resp, time, "CUSTOMER_PROFILE", true);

        return ChatbotResponse.<CustomerProfileResponseDTO>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(time)
                .intent("CUSTOMER_PROFILE")
                .build();
    }

    private ChatbotResponse<StoreList> buildStoreResponse(ChatResponse resp, ChatRequest req, long start) {
        long time = System.currentTimeMillis() - start;
        StoreList data = storeLocatorConverter.convert(resp.getResult().getOutput().getText());
        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                resp.getMetadata().getUsage(), resp.getMetadata().getModel());

        trackAnalytics(req, null, null, resp, time, "STORE_LOCATOR", true);

        return ChatbotResponse.<StoreList>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(time)
                .intent("STORE_LOCATOR")
                .build();
    }

    private ChatbotResponse<String> buildStringResponse(ChatResponse resp, ChatRequest req,
                                                        long start, String intent) {
        long time = System.currentTimeMillis() - start;
        String data = resp.getResult().getOutput().getText();
        TokenUsage tokens = tokenCostCalculator.buildTokenUsage(
                resp.getMetadata().getUsage(), resp.getMetadata().getModel());

        trackAnalytics(req, null, null, resp, time, intent, false);

        return ChatbotResponse.<String>builder()
                .data(data)
                .tokenUsage(tokens)
                .responseTimeMs(time)
                .intent(intent)
                .build();
    }

    private void trackAnalytics(ChatRequest req, String customerName, String mobileNo,
                                ChatResponse resp, long time, String intent, boolean toolUsed) {
        var usage = resp.getMetadata().getUsage();
        aiAnalyticsService.trackUsage(
                customerName, mobileNo, req.getMessage(),
                resp.getResult().getOutput().getText(),
                usage.getPromptTokens().intValue(),
                usage.getCompletionTokens().intValue(),
                resp.getMetadata().getModel(),
                resp.getResult().getMetadata().getFinishReason(),
                toolUsed,
                toolUsed ? (intent.equals("STORE_LOCATOR") ? "storeLocatorTool" : "orderTrackingTool") : "none",
                time
        );

        log.info("📊 {} - Tokens: {} (↑{} ↓{}), Time: {}ms",
                intent, usage.getTotalTokens(), usage.getPromptTokens(),
                usage.getCompletionTokens(), time);
    }
}