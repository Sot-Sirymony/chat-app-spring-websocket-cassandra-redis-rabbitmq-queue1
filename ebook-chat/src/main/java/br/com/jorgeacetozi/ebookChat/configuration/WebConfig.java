package br.com.jorgeacetozi.ebookChat.configuration;

import java.util.Arrays;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
public class WebConfig extends WebMvcConfigurerAdapter {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration global = new CorsConfiguration();
        global.setAllowCredentials(false); // must be false when using "*" origin per CORS spec
        global.setAllowedOrigins(Collections.singletonList("*"));
        global.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        global.setAllowedHeaders(Collections.singletonList("*"));
        global.setExposedHeaders(Arrays.asList("Authorization", "X-File-Deny-Reason", "Content-Disposition"));
        UrlBasedCorsConfigurationSource defaultSource = new UrlBasedCorsConfigurationSource();
        defaultSource.registerCorsConfiguration("/**", global);

        return new CorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                String path = request.getRequestURI();
                if (path != null && path.contains("/api/files/") && path.endsWith("/download")) {
                    String origin = request.getHeader("Origin");
                    CorsConfiguration fileCors = new CorsConfiguration();
                    if (origin != null && !origin.isEmpty()) {
                        fileCors.setAllowCredentials(true);
                        fileCors.setAllowedOrigins(Collections.singletonList(origin));
                    } else {
                        fileCors.setAllowCredentials(false);
                        fileCors.setAllowedOrigins(Collections.singletonList("*"));
                    }
                    fileCors.setAllowedMethods(Arrays.asList("GET", "OPTIONS"));
                    fileCors.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
                    fileCors.setExposedHeaders(Arrays.asList("X-File-Deny-Reason", "Content-Disposition"));
                    return fileCors;
                }
                return defaultSource.getCorsConfiguration(request);
            }
        };
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new SessionLocaleResolver();
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName("lang");
        return localeChangeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
