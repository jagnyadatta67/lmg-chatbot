package com.lmg.online.chatbot.ai.business.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;


@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "LMG Chatbot Menu API",
                version = "1.0",
                description = "API documentation for dynamic chatbot menu and submenu service",
                contact = @Contact(
                        name = "LMG Dev Team",
                        email = "support@lmg.ai",
                        url = "https://www.lmg.ai"
                ),
                license = @License(name = "LMG License", url = "https://www.lmg.ai/license")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Environment"),
                @Server(url = "https://api.lmg.ai", description = "Production Environment")
        }
)
public class OpenApiConfig {
}


