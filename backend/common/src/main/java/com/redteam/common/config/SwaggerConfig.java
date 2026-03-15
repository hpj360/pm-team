package com.redteam.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger API 文档配置
 *
 * @author 红方团队
 */
@Configuration
public class SwaggerConfig {

    /**
     * 配置 OpenAPI
     *
     * @return OpenAPI
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                // 添加全局认证
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .schemaRequirement("Bearer", securityScheme());
    }

    /**
     * API 基本信息
     *
     * @return API 信息
     */
    private Info apiInfo() {
        return new Info()
                .title("网络安全红方文件汇聚平台 API")
                .description("网络安全红方文件汇聚平台后端服务接口文档")
                .version("v1.0.0")
                .contact(new Contact()
                        .name("红方团队")
                        .email("redteam@example.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    /**
     * 安全认证配置
     *
     * @return 安全方案
     */
    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("Bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .description("请输入JWT Token");
    }
}
