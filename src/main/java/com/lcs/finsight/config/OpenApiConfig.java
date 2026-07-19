package com.lcs.finsight.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${app.version}")
    private String appVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinSight API")
                        .version(appVersion)
                        .description("API for personal finance management.")
                        .contact(new Contact().name("Lucas Martins").email("lucas.mrt.dev@gmail.com"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .tags(List.of(
                        new Tag().name("Authentication")
                                .description("Login and token issuance."),
                        new Tag().name("Users")
                                .description("The account resource: register, read, and update the current user."),
                        new Tag().name("Plans")
                                .description("Plans and their membership: create, rename, delete, members, roles, ownership."),
                        new Tag().name("Invitations")
                                .description("Manage a plan's invitations (owner), or preview/accept one by token."),
                        new Tag().name("Financial Transactions")
                                .description("Plan-scoped transactions, including recurring series and CSV import."),
                        new Tag().name("Financial Transaction Categories")
                                .description("Plan-scoped transaction categories."),
                        new Tag().name("Dashboard")
                                .description("Plan-scoped financial summary and breakdowns.")
                ));
    }
}
