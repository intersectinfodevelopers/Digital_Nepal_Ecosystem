package np.gov.digital.auth.config;

import lombok.RequiredArgsConstructor;
import np.gov.digital.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // FIX: these previously read "/api/v1/auth/login" etc.
                        // Spring Security's requestMatchers operate on the path
                        // AFTER server.servlet.context-path (/api) is stripped,
                        // so the old value never actually matched the real
                        // request path once this module is combined into the
                        // bootstrap deployable — meaning /auth/login was
                        // effectively NOT in the permitAll list, and depending
                        // on filter order, either legitimately blocked login
                        // entirely, or (worse) the mismatch meant anyRequest()
                        // .authenticated() below was the only rule actually in
                        // effect for these paths too. Paths here must match
                        // the corrected @RequestMapping values exactly.
                        .requestMatchers(
                                "/v1/auth/login",
                                "/v1/auth/refresh"
                        ).permitAll()
                        // Public, unauthenticated per SDD Section 6.5/6.6 —
                        // QR verification and grievance tracking are
                        // deliberately reachable without a JWT.
                        .requestMatchers(
                                "/v1/idcards/verify/**",
                                "/v1/grievances/track/**"
                        ).permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/openapi.yaml",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}