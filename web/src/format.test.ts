import { describe, expect, it } from 'vitest';
import { formatDuration, userLabel } from './format.ts';

describe('formatDuration', () => {
  it('uses the two largest units', () => {
    expect(formatDuration(45)).toBe("1 dk'dan az");
    expect(formatDuration(45 * 60)).toBe('45 dk');
    expect(formatDuration(3 * 3600 + 20 * 60)).toBe('3 sa 20 dk');
    expect(formatDuration(2 * 86_400 + 4 * 3600 + 59 * 60)).toBe('2 g 4 sa');
    expect(formatDuration(3 * 86_400)).toBe('3 g');
  });

  it('ignores the sign', () => {
    expect(formatDuration(-2 * 3600)).toBe('2 sa');
  });
});

describe('userLabel', () => {
  it('prefers "Siz", then the name, then a short id', () => {
    expect(userLabel('me', 'Me Myself', 'me')).toBe('Siz');
    expect(userLabel('u-1', 'Agent One', 'me')).toBe('Agent One');
    expect(userLabel('57b1f513-7b21-4ea0-ba33-582a17a87665', null, 'me')).toBe('57b1f513');
    expect(userLabel(null, null, 'me')).toBe('—');
  });
});
