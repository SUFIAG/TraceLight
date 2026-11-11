package com.sentinel.common.config;

import com.sentinel.common.util.RequestIdFilter;
import com.sentinel.security.adapter.in.web.PasswordChangeInterceptor;
import com.sentinel.security.adapter.in.web.StepUpInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StepUpInterceptor stepUpInterceptor;
    private final PasswordChangeInterceptor passwordChangeInterceptor;

    @Value("${sentinel.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestIdFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(stepUpInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // An explicit list rather than "*": a wildcard here would let a
                // hostile page send any header it liked, including ones a future
                // integration might trust.
                .allowedHeaders("Authorization", "Content-Type", "X-Organization-Id",
                        "X-Request-Id", "X-Api-Key", "Accept")
                .exposedHeaders("X-Request-Id", "Retry-After")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
