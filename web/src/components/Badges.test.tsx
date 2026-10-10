import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { anSla } from '../test/fixtures.ts';
import { SlaBadge, slaSummary } from './Badges.tsx';

const HOUR = 3600;

describe('slaSummary', () => {
  it('shows remaining time while running', () => {
    expect(slaSummary(anSla({ remainingSeconds: 7 * HOUR }))).toEqual({ tone: 'info', text: '7 sa kaldı' });
  });

  it('warns when less than a quarter of the target is left', () => {
    expect(slaSummary(anSla({ targetSeconds: 8 * HOUR, remainingSeconds: HOUR + 20 * 60 }))).toEqual({
      tone: 'warning',
      text: '1 sa 20 dk kaldı',
    });
  });

  it('shows the delay once breached', () => {
    expect(slaSummary(anSla({ remainingSeconds: -(2 * HOUR), breached: true }))).toEqual({
      tone: 'danger',
      text: '2 sa gecikme',
    });
  });

  it('marks a paused clock', () => {
    expect(slaSummary(anSla({ state: 'PAUSED', remainingSeconds: 5 * HOUR, dueAt: null }))).toEqual({
      tone: 'neutral',
      text: 'Duraklatıldı · 5 sa kaldı',
    });
  });

  it('reports the outcome once stopped', () => {
    expect(slaSummary(anSla({ state: 'STOPPED', dueAt: null }))).toEqual({ tone: 'success', text: 'SLA karşılandı' });
    expect(slaSummary(anSla({ state: 'STOPPED', remainingSeconds: -90 * 60, breached: true, dueAt: null }))).toEqual({
      tone: 'danger',
      text: 'SLA aşıldı (1 sa 30 dk)',
    });
  });
});

describe('SlaBadge', () => {
  it('shows the target as a tooltip', () => {
    render(<SlaBadge sla={anSla({ targetSeconds: 4 * HOUR, remainingSeconds: 3 * HOUR })} />);

    expect(screen.getByText('3 sa kaldı')).toHaveAttribute('title', 'Hedef: 4 sa');
  });
});
