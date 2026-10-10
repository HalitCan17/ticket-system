import Keycloak from 'keycloak-js';
import type { Auth } from './AuthContext.tsx';
import { roleFromRealmRoles } from './roles.ts';

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'itsm',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'itsm-web',
});

/** Tokens are refreshed when they have less than this many seconds left. */
const MIN_VALIDITY_SECONDS = 30;

export class NoApplicationRoleError extends Error {
  constructor() {
    super('Hesabınızın bu uygulamada bir rolü yok.');
    this.name = 'NoApplicationRoleError';
  }
}

interface TokenClaims {
  sub?: string;
  preferred_username?: string;
  name?: string;
  realm_access?: { roles?: string[] };
}

let initialization: Promise<Auth> | null = null;

/**
 * Logs in with Authorization Code + PKCE (redirects to Keycloak if needed) and resolves to the
 * signed-in user. Memoized: Keycloak must be initialized only once, also under React StrictMode.
 */
export function initAuth(): Promise<Auth> {
  initialization ??= (async () => {
    await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
    const claims = (keycloak.tokenParsed ?? {}) as TokenClaims;
    const role = roleFromRealmRoles(claims.realm_access?.roles);
    if (!claims.sub || !role) {
      throw new NoApplicationRoleError();
    }
    return {
      user: {
        id: claims.sub,
        username: claims.preferred_username ?? claims.sub,
        displayName: claims.name ?? claims.preferred_username ?? claims.sub,
        role,
      },
      getToken: async (forceRefresh = false) => {
        await keycloak.updateToken(forceRefresh ? -1 : MIN_VALIDITY_SECONDS);
        if (!keycloak.token) {
          throw new Error('No access token');
        }
        return keycloak.token;
      },
      logout: logoutFromKeycloak,
    };
  })();
  return initialization;
}

/** Ends the Keycloak session and comes back to the app's root (matches the client's redirect pattern). */
export function logoutFromKeycloak(): void {
  void keycloak.logout({ redirectUri: `${window.location.origin}/` });
}
