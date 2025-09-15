package com.example.cloudfour.authservice.config;

import com.example.cloudfour.modulecommon.filter.JwtClaimsAuthFilter;
import com.example.cloudfour.modulecommon.passport.filter.InternalPassportFilter;
import com.example.cloudfour.modulecommon.filter.JwtClaimsAuthFilter;
import com.example.cloudfour.modulecommon.util.PassportUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    InternalPassportFilter internalPassportFilter(PassportUtil passportUtil) {
        return new InternalPassportFilter(passportUtil);
    }

    @Bean
    JwtClaimsAuthFilter jwtClaimsAuthFilter() {
        return new JwtClaimsAuthFilter();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, InternalPassportFilter internalPassportFilter, JwtClaimsAuthFilter jwtClaimsAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login", "/auth/register/**", "/auth/refresh",
                                "/auth/email/**", "/.well-known/jwks.json",
                                "/api/passports/**", "/internal/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/actuator/**"
                        ).permitAll()
                        .requestMatchers("/auth/password", "/auth/email/change/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtClaimsAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalPassportFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
