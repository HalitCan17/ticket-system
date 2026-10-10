import type { Role } from '../api/types.ts';

/** Same precedence as the backend's JwtCurrentUserProvider: most privileged first. */
const PRECEDENCE: Role[] = ['MANAGER', 'AGENT', 'CUSTOMER'];

export function roleFromRealmRoles(roles: readonly string[] | undefined): Role | null {
  return PRECEDENCE.find((role) => roles?.includes(role)) ?? null;
}

export function isStaff(role: Role): boolean {
  return role === 'AGENT' || role === 'MANAGER';
}

export const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Müşteri',
  AGENT: 'Destek uzmanı',
  MANAGER: 'Yönetici',
};
