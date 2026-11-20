package com.loleague.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CommonSecurityConfig implements WebMvcConfigurer {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret,
                                 @Value("${app.jwt.issuer}") String issuer) {
        return JwtSupport.decoder(secret, issuer, JwtSupport.TYPE_ACCESS);
    }

    /** Maps our single "role" claim to ROLE_USER / ROLE_ADMIN authorities. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(JwtSupport.CLAIM_ROLE);
            return List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role)));
        });
        return converter;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
