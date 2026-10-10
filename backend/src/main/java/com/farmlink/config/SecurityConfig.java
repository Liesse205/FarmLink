package com.farmlink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   GoogleOAuth2UserService googleOAuth2UserService,
                                                   SecurityContextRepository securityContextRepository,
                                                   @Value("${farmlink.frontend-url}") String frontendUrl)
            throws Exception {
        http
                .cors(Customizer.withDefaults())

                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**", "/error",
                                "/api/health")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings/mine").hasRole("FARMER")
                        .requestMatchers(HttpMethod.GET, "/api/listings", "/api/listings/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/listings").hasRole("FARMER")
                        .requestMatchers(HttpMethod.PUT, "/api/listings/*").hasAnyRole("FARMER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/requests").hasRole("BUYER")
                        .requestMatchers(HttpMethod.PUT, "/api/requests/*").hasRole("FARMER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .userInfoEndpoint(endpoint -> endpoint
                                .userService(googleOAuth2UserService)
                                .oidcUserService(googleOAuth2UserService::loadOidcUser))
                        .successHandler((request, response, authentication) ->
                                response.sendRedirect(frontendUrl + "/"))
                        .failureHandler((request, response, exception) -> {

                            String errorCode = exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth2
                                    ? oauth2.getError().getErrorCode()
                                    : "login_failed";
                            log.warn("Google login failed ({}): {}", errorCode, exception.getMessage());
                            response.sendRedirect(frontendUrl + "/?login=error&e=" + errorCode);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.OK.value())))
                .exceptionHandling(ex -> ex

                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${farmlink.frontend-url}") String frontendUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
