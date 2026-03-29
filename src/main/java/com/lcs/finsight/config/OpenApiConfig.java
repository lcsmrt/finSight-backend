package com.lcs.finsight.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinSight API")
                        .version("v0.0.0")
                        .description("API for personal finance management.")
                        .contact(new Contact().name("Lucas Martins").email("lucas.mrt.dev@gmail.com"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT"))
                );
    }
}
