package com.itsm.backend.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps a validated Keycloak access token to a {@link CurrentUser}:
 * {@code id} is the token subject, {@code role} the most privileged application role found in
 * {@code realm_access.roles} (MANAGER > AGENT > CUSTOMER). See docs/adr/0004-keycloak-jwt-resource-server.md.
 */
@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

    /** Most privileged first. */
    private static final List<Role> PRECEDENCE = List.of(Role.MANAGER, Role.AGENT, Role.CUSTOMER);

    @Override
    public CurrentUser resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new UnauthorizedException("A valid bearer token is required.");
        }
        return fromJwt(token.getToken());
    }

    static CurrentUser fromJwt(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException("Token has no subject.");
        }
        Collection<?> roles = realmRoles(jwt);
        Role role = PRECEDENCE.stream()
                .filter(r -> roles.contains(r.name()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("NO_ROLE", "The user has no application role."));
        return new CurrentUser(subject, role);
    }

    private static Collection<?> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            return roles;
        }
        return List.of();
    }
}
