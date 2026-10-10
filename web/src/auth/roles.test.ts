import { describe, expect, it } from 'vitest';
import { roleFromRealmRoles } from './roles.ts';

describe('roleFromRealmRoles', () => {
  it('picks the most privileged application role', () => {
    expect(roleFromRealmRoles(['CUSTOMER', 'default-roles-itsm'])).toBe('CUSTOMER');
    expect(roleFromRealmRoles(['CUSTOMER', 'MANAGER', 'AGENT'])).toBe('MANAGER');
    expect(roleFromRealmRoles(['AGENT', 'CUSTOMER'])).toBe('AGENT');
  });

  it('returns null without an application role', () => {
    expect(roleFromRealmRoles(['default-roles-itsm'])).toBeNull();
    expect(roleFromRealmRoles(undefined)).toBeNull();
  });
});
