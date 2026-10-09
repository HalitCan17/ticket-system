package com.itsm.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itsm.backend.common.error.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class DevHeaderCurrentUserProviderTest {

    final DevHeaderCurrentUserProvider provider = new DevHeaderCurrentUserProvider();

    @Test
    void resolvesUserFromHeaders() {
        CurrentUser user = provider.resolve(request("agent1", "AGENT"));

        assertThat(user).isEqualTo(new CurrentUser("agent1", Role.AGENT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "Customer", " CUSTOMER "})
    void roleIsCaseInsensitive(String role) {
        assertThat(provider.resolve(request("c1", role)).role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void missingHeadersAreUnauthorized() {
        assertThatThrownBy(() -> provider.resolve(new MockHttpServletRequest()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void unknownRoleIsUnauthorized() {
        assertThatThrownBy(() -> provider.resolve(request("u1", "ADMIN")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "a/b"})
    void invalidUserIdIsUnauthorized(String userId) {
        assertThatThrownBy(() -> provider.resolve(request(userId, "AGENT")))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static MockHttpServletRequest request(String userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevHeaderCurrentUserProvider.USER_ID_HEADER, userId);
        request.addHeader(DevHeaderCurrentUserProvider.ROLE_HEADER, role);
        return request;
    }
}
