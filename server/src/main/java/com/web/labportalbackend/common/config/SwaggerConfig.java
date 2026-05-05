package com.web.labportalbackend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI labPortalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lab Portal API")
                        .version("1.0.0")
                        .description("""
                                RESTful API for Lab Portal System.
                                
                                ## Authentication
                                1. Call `POST /auth/login` with credentials
                                2. Copy the `accessToken` from response
                                3. Click **Authorize** button → paste token
                                
                                ## Modules
                                - **Auth**: Authentication & authorization (JWT)
                                - **Lab**: Laboratory management
                                - **Booking**: Lab booking management
                                - **Research**: Research project management
                                """)
                        .contact(new Contact()
                                .name("Lab Portal Team")
                                .email("dev@labportal.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort + contextPath)
                                .description("Local Development Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT access token")
                        ));
    }
}
