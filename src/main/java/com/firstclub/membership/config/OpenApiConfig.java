package com.firstclub.membership.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI membershipEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FirstClub Membership Engine API")
                        .description("Production-grade Membership Engine for FirstClub quick commerce platform. " +
                                "Supports tier-based subscriptions, configurable benefits, eligibility rules, " +
                                "and checkout integration.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("FirstClub Engineering")
                                .email("engineering@firstclub.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")
                ));
    }
}
