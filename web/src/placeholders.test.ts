import { describe, expect, it } from 'vitest';
import { fillPlaceholders } from './placeholders.ts';

describe('fillPlaceholders', () => {
  it('fills every known placeholder, also repeated ones, and leaves unknown ones', () => {
    const text = 'Merhaba {müşteri}, {talep} çözüldü. {müşteri} {bilinmeyen} — {ben}';

    expect(fillPlaceholders(text, { customer: 'Ayşe', ticketId: 42, me: 'Agent One' })).toBe(
      'Merhaba Ayşe, #42 çözüldü. Ayşe {bilinmeyen} — Agent One',
    );
  });
});
