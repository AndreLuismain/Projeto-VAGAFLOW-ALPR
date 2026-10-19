package com.vagaflow.alpr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuracao global de CORS e servico estatico do frontend modularizado ("Asphalt & Signal"),
 * permitindo acessar a aplicacao completa diretamente em http://localhost:8080/.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path frontendDir = Paths.get("..", "frontend").toAbsolutePath().normalize();
        String frontendUri = frontendDir.toUri().toString();
        if (!frontendUri.endsWith("/")) {
            frontendUri = frontendUri + "/";
        }
        registry.addResourceHandler("/**")
                .addResourceLocations(frontendUri, "classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
