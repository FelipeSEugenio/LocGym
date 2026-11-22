package com.locgym.identidade.infraestrutura;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API stateless autenticada por Bearer JWT (RS256). CSRF desligado: não há sessão por cookie;
 * o único cookie (refresh) é SameSite=Strict e restrito a /api/v1/auth.
 */
@Configuration(proxyBeanMethods = false)
class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/cadastro-administrador",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(ChavesJwt chaves) {
        return NimbusJwtDecoder.withPublicKey(chaves.publica()).build();
    }

    @Bean
    JwtEncoder jwtEncoder(ChavesJwt chaves) {
        RSAKey chave = new RSAKey.Builder(chaves.publica()).privateKey(chaves.privada()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(chave)));
    }
}
