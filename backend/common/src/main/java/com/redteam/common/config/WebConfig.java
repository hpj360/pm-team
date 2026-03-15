package com.redteam.common.config;

import com.redteam.common.util.JwtUtil;
import com.redteam.common.util.UserContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * Web 配置类
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 跨域配置
     *
     * @return 跨域过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的域名
        config.addAllowedOriginPattern("*");
        // 允许的请求头
        config.addAllowedHeader("*");
        // 允许的请求方法
        config.addAllowedMethod("*");
        // 允许携带凭证
        config.setAllowCredentials(true);
        // 预检请求缓存时间
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * 用户上下文过滤器
     *
     * @return 过滤器
     */
    @Bean
    public Filter userContextFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                try {
                    // 从请求头获取Token
                    String token = httpRequest.getHeader(JwtUtil.HEADER_NAME);
                    if (token != null && token.startsWith(JwtUtil.TOKEN_PREFIX)) {
                        token = token.substring(JwtUtil.TOKEN_PREFIX.length());

                        // 验证Token
                        if (JwtUtil.validateToken(token)) {
                            // 设置用户上下文
                            UserContext.setUserId(JwtUtil.getUserId(token));
                            UserContext.setUsername(JwtUtil.getUsername(token));
                            UserContext.setToken(token);
                        }
                    }

                    chain.doFilter(request, response);
                } finally {
                    // 清除上下文
                    UserContext.clear();
                }
            }
        };
    }
}
