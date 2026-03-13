package com.lmg.online.chatbot.ai.project.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 configuration.
 *
 * Accessible at: /swagger-ui/index.html
 *
 * API key (X-API-Key header) is declared as a global security scheme so that
 * Swagger UI shows an "Authorize" button and sends the key with every request.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Landmark Chatbot API",
                version = "2.0",
                description = "Multi-brand AI Chatbot — RAG-powered policy answers, order tracking, store locator, gift cards & more.",
                contact = @Contact(
                        name = "LMG Dev Team",
                        email = "support@lmg.ai",
                        url = "https://www.lmg.ai"
                ),
                license = @License(name = "LMG License", url = "https://www.lmg.ai/license")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local"),
                @Server(url = "https://api.lmg.ai",   description = "Production")
        },
        security = @SecurityRequirement(name = "X-API-Key")
)
@SecurityScheme(
        name = "X-API-Key",
        type = SecuritySchemeType.APIKEY,
        paramName = "X-API-Key",
        in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
