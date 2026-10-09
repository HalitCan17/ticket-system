package com.itsm.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.itsm.backend.common.error.ApiException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtCurrentUserProviderTest {

    final JwtCurrentUserProvider provider = new JwtCurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsSubjectAndRealmRole() {
        authenticate(jwt("user-1", List.of("CUSTOMER", "default-roles-itsm")));

        assertThat(provider.resolve()).isEqualTo(new CurrentUser("user-1", Role.CUSTOMER));
    }

    @Test
    void picksMostPrivilegedRole() {
        authenticate(jwt("user-2", List.of("CUSTOMER", "MANAGER", "AGENT")));

        assertThat(provider.resolve().role()).isEqualTo(Role.MANAGER);
    }

    @Test
    void tokenWithoutApplicationRoleIsForbidden() {
        authenticate(jwt("user-3", List.of("default-roles-itsm")));

        assertThatThrownBy(provider::resolve)
                .isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("NO_ROLE");
    }

    @Test
    void tokenWithoutRealmAccessIsForbidden() {
        Jwt token = Jwt.withTokenValue("t").header("alg", "none").subject("user-4").build();
        authenticate(token);

        assertThatThrownBy(provider::resolve).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nonJwtAuthenticationIsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y"));

        assertThatThrownBy(provider::resolve).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void noAuthenticationIsUnauthorized() {
        assertThatThrownBy(provider::resolve).isInstanceOf(UnauthorizedException.class);
    }

    private static Jwt jwt(String subject, List<String> roles) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
