package com.ziyadsamhaoui.messagingchatservice.config;

import com.ziyadsamhaoui.messagingchatservice.security.InternalServiceTokenFilter;
import com.ziyadsamhaoui.messagingchatservice.security.RestAccessDeniedHandler;
import com.ziyadsamhaoui.messagingchatservice.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String INTERNAL_ENDPOINTS = "/internal/**";

    @Bean
    SecurityFilterChain chatServiceSecurityFilterChain(HttpSecurity http, ChatProperties chatProperties,
            RestAuthenticationEntryPoint authenticationEntryPoint, RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.requestMatchers(INTERNAL_ENDPOINTS).hasRole("INTERNAL")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler).jwt(Customizer.withDefaults()))
                .addFilterBefore(new InternalServiceTokenFilter(chatProperties.internalServiceToken()),
                        BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
