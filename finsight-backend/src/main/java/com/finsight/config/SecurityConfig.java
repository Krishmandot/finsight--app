package com.finsight.config;

import com.finsight.security.JwtAuthenticationFilter;
import com.finsight.security.LoginAttemptService;
import com.finsight.security.ProblemJsonSecurityHandler;
import com.finsight.security.RateLimitedAuthenticationProvider;
import com.finsight.ui.auth.LoginView;
import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Two independent security filter chains share one process:
 * <ol>
 *   <li>{@code /api/**}: stateless, JWT bearer tokens, JSON problem responses (never redirects).</li>
 *   <li>Everything else: the Vaadin UI with session-based form login.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final ProblemJsonSecurityHandler problemJsonSecurityHandler;
    private final LoginAttemptService loginAttemptService;

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                    "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Unauthenticated/forbidden API calls get JSON 401/403 instead of a redirect to the Vaadin login page.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(problemJsonSecurityHandler)
                .accessDeniedHandler(problemJsonSecurityHandler))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error",
                    "/styles/**", "/icons/**", "/images/**", "/favicon.ico").permitAll()
            )
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .authenticationProvider(authenticationProvider())
            .with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));

        return http.build();
    }

    /**
     * The JWT filter is a Spring bean only so it can be injected into the API chain. Without this,
     * Spring Boot would also register it as a global servlet filter running on every request.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        RateLimitedAuthenticationProvider provider =
            new RateLimitedAuthenticationProvider(userDetailsService, loginAttemptService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
