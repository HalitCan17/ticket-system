package com.itsm.backend.security;

import java.util.Locale;
import java.util.regex.Pattern;

import com.itsm.backend.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY: trusts the {@code X-User-Id} / {@code X-User-Role} headers.
 * Anyone can claim any identity, so this bean exists only in the {@code dev} profile
 * (see docs/adr/0003-temporary-dev-header-identity.md).
 */
@Component
@Profile("dev")
public class DevHeaderCurrentUserProvider implements CurrentUserProvider {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";

    private static final Pattern USER_ID = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

    @Override
    public CurrentUser resolve(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        String role = request.getHeader(ROLE_HEADER);
        if (userId == null || role == null) {
            throw new UnauthorizedException("Missing " + USER_ID_HEADER + " or " + ROLE_HEADER + " header.");
        }
        if (!USER_ID.matcher(userId).matches()) {
            throw new UnauthorizedException("Invalid " + USER_ID_HEADER + " header.");
        }
        return new CurrentUser(userId, parseRole(role));
    }

    private static Role parseRole(String value) {
        try {
            // Locale.ROOT avoids locale-sensitive case mapping (e.g. Turkish dotted/dotless i).
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid " + ROLE_HEADER + " header.");
        }
    }
}
