# LOIPL Chatbot — Developer Guide for Claude

This file teaches Claude (and new developers) exactly how to add a new API feature
(Tool + Intent + Handler + Registration) to the LOIPL chatbot.

---

## Project Structure Quick Reference

```
src/main/java/com/lmg/online/chatbot/ai/
│
├── tools/                          ← Spring AI @Tool beans (raw API callers)
│   ├── order/
│   │   ├── OrderTrackingTool.java  ← existing: single order tracking
│   │   ├── OrderListingTool.java   ← existing: paginated order history
│   │   └── dto/                   ← DTOs for tool request/response
│   ├── storelocator/
│   ├── giftcard/
│   └── user/
│
├── project/
│   ├── handler/                   ← IntentHandlers (orchestrate tools + AI)
│   │   ├── IntentHandler.java     ← base interface (NEVER modify)
│   │   ├── order/
│   │   ├── customer/
│   │   ├── giftcard/
│   │   ├── storelocator/
│   │   └── general/
│   │
│   └── intent/
│       ├── IntentClassifier.java  ← AI-based classifier (add new intent names here)
│       ├── IntentClassification.java ← record: intent, confidence, extractedInfo
│       ├── IntentRouterService.java  ← auto-registers all IntentHandler @Components
│       └── ChatbotService.java    ← caching layer on top of IntentRouterService
│
├── common/
│   └── ConceptBaseUrlResolver.java ← builds URLs per brand/env
│
├── auth/
│   └── AuthenticationServiceUtil.java ← executes HTTP calls via RestTemplate
│
└── config/
    └── ChatClientConfig.java      ← declares named ChatClient beans
```

---

## Architecture Flow

```
HTTP POST /api/chat
    │
    ▼
ChatbotController
    │
    ▼
ChatbotService (cache check → cache hit returns immediately)
    │  cache miss
    ▼
IntentRouterService
    ├─► IntentClassifier (OpenAI) → returns intent string e.g. "ORDER_TRACKING"
    └─► intentHandlers.get("ORDER_TRACKING") → OrderTrackingIntentHandler
                                                        │
                                                        ▼
                                               Tool.getXxx(userId, concept, env, appid)
                                                        │
                                                        ▼
                                              AuthenticationServiceUtil
                                                        │
                                                        ▼
                                              External Hybris API
                                                        │
                                                        ▼
                                              ChatbotResponse<T>
```

---

## ChatRequest Fields (available in every handler)

| Field            | Type    | Description                                      |
|------------------|---------|--------------------------------------------------|
| `message`        | String  | User's chat message (required)                   |
| `userId`         | String  | `phone@landmarkmlogindomain.com` (if logged in)  |
| `concept`        | String  | Brand: `LIFESTYLE` / `MAX` / `HOMECENTRE` / `BABYSHOP` |
| `env`            | String  | Environment: `uat1` / `uat5` / `prod`            |
| `appid`          | String  | `ANDROID` / `IPHONE` / `Desktop` / `Mobile`     |
| `previousResponse` | String | Last AI reply (conversational context)          |
| `latitude`       | double  | For store-locator queries                        |
| `longitude`      | double  | For store-locator queries                        |
| `cardNumber`     | String  | Gift card number (gift card intent only)         |
| `pin`            | String  | Gift card PIN (gift card intent only)            |

---

## Step-by-Step: How to Add a New API Feature

### Step 1 — Create DTOs (`tools/{feature}/dto/`)

Create one DTO per API response object. Rules:
- Annotate with `@Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)`
- Use `@JsonProperty("fieldName")` on every field (match exact API JSON keys)
- Add a `chatMessage` / `chat_message` field for fallback/error messages
- Keep DTOs lean — only map fields the chatbot actually uses

```java
package com.lmg.online.chatbot.ai.tools.{feature}.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XxxResponse {

    @JsonProperty("someField")
    private String someField;

    /** Set by tool on error — AI surfaces this to the user */
    @JsonProperty("chat_message")
    private String chatMessage;
}
```

---

### Step 2 — Create the Tool (`tools/{feature}/XxxTool.java`)

The tool calls the raw external API. It does NOT call OpenAI.

Rules:
- Annotate class with `@Component @Slf4j`
- Annotate the method with `@Tool(name="...", description="...")`
- Every parameter gets `@ToolParam(required=true/false, description="...")`
- Build URL using `ConceptBaseUrlResolver.buildApiUrl(concept, env, uriPath, appid, queryParams)`
- Execute with `authenticationServiceUtil.callWithAuthRetry(appid, url, method, headers, body, ResponseClass.class, env)`
- On any exception: return a response object with `chatMessage` set to a friendly error string
- Use `ConceptBaseUrlResolver.getPhoneNumber(concept)` in fallback messages

```java
package com.lmg.online.chatbot.ai.tools.{feature};

import com.lmg.online.chatbot.ai.auth.AuthenticationServiceUtil;
import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.{feature}.dto.XxxResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class XxxTool {

    @Autowired
    private AuthenticationServiceUtil authenticationServiceUtil;

    @Tool(
            name = "getXxx",
            description = """
            One-line summary of what this tool does.

            Parameters:
            - userId      (required): Customer's user ID
            - concept     (required): Brand — LIFESTYLE | MAX | HOMECENTRE | BABYSHOP
            - env         (required): Environment — uat1 | uat5 | prod
            - appid       (required): App identifier — ANDROID | IPHONE | Desktop | Mobile

            Returns: { ... describe key fields returned ... }
            """
    )
    public XxxResponse getXxx(
            @ToolParam(required = true, description = "Customer's user ID") String userId,
            @ToolParam(required = true, description = "Brand concept")       String concept,
            @ToolParam(required = true, description = "Environment")         String env,
            @ToolParam(required = true, description = "App identifier")      String appid
    ) {
        log.info("🔧 [XxxTool] userId={}, concept={}, env={}", userId, concept, env);

        try {
            // Build URL
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("someParam", "value");

            String url = ConceptBaseUrlResolver.buildApiUrl(
                    concept, env, "/en/path/to/endpoint", appid, queryParams);

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("token", userId);           // add auth headers as needed

            // Call API
            XxxResponse response = authenticationServiceUtil
                    .callWithAuthRetry(appid, url, HttpMethod.GET, headers, null, XxxResponse.class, env)
                    .getBody();

            if (response == null) {
                return errorResponse(concept);
            }

            return response;

        } catch (Exception e) {
            log.error("❌ [XxxTool] Error: {}", e.getMessage(), e);
            return errorResponse(concept);
        }
    }

    private XxxResponse errorResponse(String concept) {
        XxxResponse r = new XxxResponse();
        r.setChatMessage("Unable to process your request. " +
                ConceptBaseUrlResolver.getPhoneNumber(concept));
        return r;
    }
}
```

---

### Step 3 — Create the Handler (`project/handler/{feature}/XxxIntentHandler.java`)

The handler orchestrates: auth check → tool call → AI formatting → response building.

Rules:
- Implement `IntentHandler<YourResponseDTO>`
- Annotate with `@Component @Slf4j @RequiredArgsConstructor`
- `getIntentType()` must return the **exact intent string** used in `IntentClassifier`
- `canHandle(String query)` — provide a regex Pattern for fast-path routing (optional)
- For authenticated-only features: check `request.getUserId()` before calling the tool
- Inject the named `ChatClient` bean using `@Qualifier("xxxClient")` if you need AI formatting
- For simple tool-only responses (no AI reformatting needed): call tool directly, skip ChatClient

```java
package com.lmg.online.chatbot.ai.project.handler.{feature};

import com.lmg.online.chatbot.ai.analytics.AiAnalyticsService;
import com.lmg.online.chatbot.ai.analytics.ChatbotResponse;
import com.lmg.online.chatbot.ai.project.handler.IntentHandler;
import com.lmg.online.chatbot.ai.request.ChatRequest;
import com.lmg.online.chatbot.ai.tools.{feature}.XxxTool;
import com.lmg.online.chatbot.ai.tools.{feature}.dto.XxxResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class XxxIntentHandler implements IntentHandler<XxxResponse> {

    // Optional: fast-path pattern matching (skips AI classifier for obvious queries)
    private static final Pattern XXX_PATTERN = Pattern.compile(
            ".*\\b(keyword1|keyword2|keyword3)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private final XxxTool xxxTool;
    private final AiAnalyticsService aiAnalyticsService;

    @Override
    public ChatbotResponse<XxxResponse> handle(ChatRequest request, long startTime) {
        log.info("🔍 Handling XXX_INTENT");

        // Auth guard (remove if endpoint is public)
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return unauthenticatedResponse(startTime);
        }

        // Call tool
        XxxResponse data = xxxTool.getXxx(
                request.getUserId(),
                request.getConcept(),
                request.getEnv(),
                request.getAppid()
        );

        return buildResponse(data, request, startTime);
    }

    @Override
    public String getIntentType() {
        return "XXX_INTENT";    // ← must match exactly what IntentClassifier returns
    }

    @Override
    public boolean canHandle(String query) {
        return XXX_PATTERN.matcher(query).matches();
    }

    private ChatbotResponse<XxxResponse> unauthenticatedResponse(long startTime) {
        XxxResponse r = new XxxResponse();
        r.setChatMessage("Please sign in to continue — once logged in, I can fetch your details.");
        return buildResponse(r, null, startTime);
    }

    private ChatbotResponse<XxxResponse> buildResponse(XxxResponse data, ChatRequest request, long startTime) {
        long responseTime = System.currentTimeMillis() - startTime;

        // Track analytics
        aiAnalyticsService.trackUsage(
                null, null,
                request != null ? request.getMessage() : "",
                data.getChatMessage(),
                0, 0,
                "gpt-4o-mini",
                "stop",
                true,
                "xxxTool",
                responseTime
        );

        log.info("📊 {} - Time: {}ms", getIntentType(), responseTime);

        return ChatbotResponse.<XxxResponse>builder()
                .data(data)
                .responseTimeMs(responseTime)
                .intent(getIntentType())
                .build();
    }
}
```

---

### Step 4 — Register the Intent in `IntentClassifier`

Open `IntentClassifier.java` and add your new intent to `CLASSIFICATION_PROMPT_TEMPLATE`:

```java
// BEFORE
- GENERAL_QUERY: Greetings, general or other uncategorized questions.

// AFTER
- XXX_INTENT: Brief description of when to classify as this intent. Example user queries.
- GENERAL_QUERY: Greetings, general or other uncategorized questions.
```

**Important rules for intent descriptions:**
- Be specific — tell the AI exactly what phrases/queries map to this intent
- Add a `Do not classify as XXX_INTENT if ...` line for common confusions
- Keep it one line max

---

### Step 5 — Register ChatClient Bean (if handler uses AI formatting)

Open `ChatClientConfig.java` and add a new named bean:

```java
@Bean(name = "xxxClient")
public ChatClient xxxClient(OpenAiChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
}
```

Then inject it in your handler:
```java
@Qualifier("xxxClient")
private final ChatClient xxxClient;
```

**Note:** If your handler calls the tool directly and returns the raw DTO
(no AI text formatting), you do NOT need a ChatClient bean.

---

### Step 6 — No manual registration needed in IntentRouterService

`IntentRouterService` auto-discovers all `@Component` classes that implement
`IntentHandler<?>` via Spring's constructor injection:

```java
// This constructor auto-collects ALL IntentHandler beans
public IntentRouterService(List<IntentHandler<?>> handlers, ...) {
    this.intentHandlers = handlers.stream()
            .collect(Collectors.toMap(IntentHandler::getIntentType, Function.identity()));
}
```

✅ Just annotate your handler with `@Component` — it registers automatically.

---

## URL Building Reference (`ConceptBaseUrlResolver`)

| Method | Usage | Example output |
|--------|-------|----------------|
| `buildApiUrl(concept, env, path, appId)` | API call, no extra params | `.../landmarkshopscommercews/v2/lifestylein/en/...?appId=ANDROID` |
| `buildApiUrl(concept, env, path, appId, Map<> queryParams)` | API call + extra params | adds `&key=val` pairs |
| `buildProfileApiUrl(concept, env, appId)` | User profile endpoint (v3) | `.../v3/.../en/users/current/profile` |
| `buildTokenDetailsUrl(concept, env, token, appId)` | Token validation | `.../chatbot/getTokenDetails?appId=...&token=...` |
| `buildTokenDetailsUri(concept, env, token, appId)` | Same but returns `URI` (no double-encoding) | Use when token has special chars |
| `buildReactUrl(concept, env, path)` | Frontend page links | `.../in/en/...` |
| `getPhoneNumber(concept)` | Fallback message support line | `"If unclear,: 'Call 1800-123-1555 for details."` |

**Concept → siteId mapping:**

| Concept | siteId |
|---------|--------|
| LIFESTYLE | lifestylein |
| MAX | maxin |
| HOMECENTRE | homecentrein |
| BABYSHOP | babyshopin |

**env → URL prefix:**
- `null` / `prod` → `www.lifestylestores.com`
- `uat1` → `uat1.lifestylestores.com`
- `uat5` → `uat5.lifestylestores.com`

---

## Existing Intents & Handlers Summary

| Intent String | Handler Class | Tool Used | Auth Required |
|---------------|--------------|-----------|---------------|
| `ORDER_TRACKING` | `OrderTrackingIntentHandler` | `OrderTrackingTool` | ✅ Yes |
| `ORDER_LISTING` | *(create)* | `OrderListingTool` | ✅ Yes |
| `STORE_LOCATOR` | `StoreLocatorIntentHandler` | `StoreLocatorTool` | ❌ No |
| `POLICY_QUESTION` | `PolicyIntentHandler` | RAG vector search | ❌ No |
| `CUSTOMER_PROFILE` | `CustomerProfileIntentHandler` | `MyProfileDetailsTool` | ✅ Yes |
| `GIFT_CARD_BALANCE` | `GiftCardBalanceIntentHandler` | `GiftCardBalanceTool` | ❌ No |
| `GENERAL_QUERY` | `GeneralQueryIntentHandler` | None (AI only) | ❌ No |

---

## Existing Named ChatClient Beans (`ChatClientConfig.java`)

| Bean name | Qualifier | Used by |
|-----------|-----------|---------|
| `generalClient` | `@Qualifier("generalClient")` | GeneralQueryIntentHandler |
| `orderTrackClient` | `@Qualifier("orderTrackClient")` | OrderTrackingIntentHandler |
| `customerProfile` | `@Qualifier("customerProfile")` | CustomerProfileIntentHandler |
| `storeLocator` | `@Qualifier("storeLocator")` | StoreLocatorIntentHandler |
| `giftCardClient` | `@Qualifier("giftCardClient")` | GiftCardBalanceIntentHandler |
| `policyClient` | `@Qualifier("policyClient")` | PolicyIntentHandler |
| `intentClassifierClient` | *(default)* | IntentClassifier |

---

## Checklist for Each New Feature

```
□  Step 1: Create DTO(s) in tools/{feature}/dto/
           - @Data @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown=true)
           - @JsonProperty on every field
           - Include chat_message field for fallback

□  Step 2: Create Tool in tools/{feature}/XxxTool.java
           - @Component @Slf4j
           - @Tool with name + description
           - @ToolParam on every parameter
           - Build URL with ConceptBaseUrlResolver
           - Call API with AuthenticationServiceUtil
           - Return error response (never throw) with phone number

□  Step 3: Create Handler in project/handler/{feature}/XxxIntentHandler.java
           - @Component @Slf4j @RequiredArgsConstructor
           - implements IntentHandler<YourDTO>
           - getIntentType() returns EXACT intent string
           - canHandle() provides regex fast-path (optional)
           - Auth guard if needed
           - buildResponse() + trackAnalytics()

□  Step 4: Add intent to IntentClassifier.CLASSIFICATION_PROMPT_TEMPLATE
           - One bullet point
           - Clear description + when NOT to use

□  Step 5: Add ChatClient bean in ChatClientConfig.java (only if using AI formatting)

□  Step 6: Verify auto-registration by checking startup log:
           "✅ IntentRouterService initialized with N handlers: [...]"
           Your new intent name should appear in the list.
```

---

## Common Mistakes to Avoid

| Mistake | Correct approach |
|---------|-----------------|
| Throwing exceptions from a tool | Always catch and return `errorResponse()` |
| Hardcoding brand URLs | Use `ConceptBaseUrlResolver` |
| Registering handler manually in `IntentRouterService` | Not needed — `@Component` is enough |
| `getIntentType()` typo vs classifier string | They must match exactly (case-sensitive) |
| Double-encoding tokens in URLs | Use `buildTokenDetailsUri()` not `buildTokenDetailsUrl()` |
| No `@JsonIgnoreProperties(ignoreUnknown=true)` on DTOs | Breaks when API adds new fields |
| Using `@Autowired` on `ChatClient` without `@Qualifier` | Gets wrong bean — always qualify |
| Caching gift card / sensitive responses | `ChatbotService.isCacheable()` blocks `cardNumber`/`pin` — never bypass |

---

## Package Naming Conventions

```
tools/{feature}/                    → tool class + dto/ subdirectory
project/handler/{feature}/          → intent handler class
project/handler/{feature}/dto/      → handler-specific DTOs (if different from tool DTOs)
```

Feature folder names used so far: `order`, `storelocator`, `giftcard`, `user`, `general`, `customer`
