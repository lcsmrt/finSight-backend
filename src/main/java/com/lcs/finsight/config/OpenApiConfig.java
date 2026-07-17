package com.lcs.finsight.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

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
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT"))
                );
    }
}
