package com.goatteen.trading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import com.goatteen.trading.auth.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.goatteen.trading.auth.security.AuthProperties;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.time.Clock;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import com.goatteen.trading.auth.security.AuthRateLimiter;
import com.goatteen.trading.auth.security.AuthRateLimitFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
                JwtProperties.class,
                AuthProperties.class
})
public class SecurityConfig {
        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final List<String> allowedOrigins;

        public SecurityConfig(
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        @Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
                this.allowedOrigins = List.copyOf(allowedOrigins);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(12);
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {

                CorsConfiguration configuration = new CorsConfiguration();

                configuration.setAllowedOrigins(
                                allowedOrigins);

                configuration.setAllowedMethods(
                                List.of(
                                                "GET",
                                                "POST",
                                                "PUT",
                                                "PATCH",
                                                "DELETE",
                                                "OPTIONS"));

                configuration.setAllowedHeaders(
                                List.of("*"));

                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration(
                                "/**",
                                configuration);

                return source;
        }

        @Bean
        public AuthRateLimiter authRateLimiter(AuthProperties properties) {
                return new AuthRateLimiter(properties, Clock.systemUTC());
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthRateLimiter limiter)
                        throws Exception {

                CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
                csrfTokens.setCookiePath("/api/auth");
                csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));

                http
                                .cors(cors -> {}) 
                                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                                        .requireCsrfProtectionMatcher(request ->
                                                !List.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())
                                                && request.getRequestURI().substring(request.getContextPath().length())
                                                        .startsWith("/api/auth/")))

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(
                                                                (request, response, authException) -> response
                                                                                .setStatus(
                                                                                                HttpStatus.UNAUTHORIZED
                                                                                                                .value()))
                                                .accessDeniedHandler(
                                                                (request, response, accessDeniedException) -> response
                                                                                .setStatus(
                                                                                                HttpStatus.FORBIDDEN
                                                                                                                .value())))

                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/auth/**")
                                                .permitAll()

                                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                                .permitAll()

                                                .requestMatchers("/api/health")
                                                .permitAll()

                                                .requestMatchers("/error")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**")
                                                .permitAll()

                                                .anyRequest()
                                                .authenticated())

                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())
                                .logout(logout -> logout.disable());

                http.addFilterBefore(
                                jwtAuthenticationFilter,
                                UsernamePasswordAuthenticationFilter.class);

                http.addFilterBefore(new AuthRateLimitFilter(limiter),
                                CsrfFilter.class);

                return http.build();
        }
}
