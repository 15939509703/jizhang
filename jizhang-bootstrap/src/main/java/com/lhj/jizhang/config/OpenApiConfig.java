package com.lhj.jizhang.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI jizhangOpenApi() {
        SecurityScheme bearerAuth = new SecurityScheme()
                .name(BEARER_AUTH)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title("记账小程序后端 API")
                        .description("记账小程序登录、账本、分类、账户和账单接口")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerAuth));
    }
}
