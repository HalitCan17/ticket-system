import { createContext, useContext } from 'react';
import type { Role } from '../api/types.ts';
import type { TokenSource } from '../api/client.ts';

export interface AuthUser {
  /** Keycloak subject; matches requesterId/assigneeId/authorId in API responses. */
  id: string;
  username: string;
  displayName: string;
  role: Role;
}

export interface Auth {
  user: AuthUser;
  getToken: TokenSource;
  logout: () => void;
}

export const AuthContext = createContext<Auth | null>(null);

export function useAuth(): Auth {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error('useAuth must be used inside an AuthContext provider');
  }
  return auth;
}
