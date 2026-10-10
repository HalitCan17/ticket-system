import type { Sla, TicketPriority, TicketStatus } from '../api/types.ts';
import { formatDuration } from '../format.ts';
import { PRIORITY_LABELS, STATUS_LABELS } from '../labels.ts';
import styles from './Badge.module.css';

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

function Badge({ tone, children, title }: { tone: Tone; children: string; title?: string }) {
  const toneClass = tone === 'neutral' ? '' : styles[tone];
  return (
    <span className={`${styles.badge} ${toneClass ?? ''}`.trim()} title={title}>
      {children}
    </span>
  );
}

const STATUS_TONES: Record<TicketStatus, Tone> = {
  NEW: 'info',
  IN_PROGRESS: 'info',
  WAITING_FOR_CUSTOMER: 'warning',
  RESOLVED: 'success',
  CLOSED: 'neutral',
};

export function StatusBadge({ status }: { status: TicketStatus }) {
  return <Badge tone={STATUS_TONES[status]}>{STATUS_LABELS[status]}</Badge>;
}

const PRIORITY_TONES: Record<TicketPriority, Tone> = {
  LOW: 'neutral',
  MEDIUM: 'info',
  HIGH: 'warning',
  CRITICAL: 'danger',
};

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  return <Badge tone={PRIORITY_TONES[priority]}>{PRIORITY_LABELS[priority]}</Badge>;
}

/** Less than this share of the target left → warning colour. */
const WARNING_SHARE = 0.25;

export function slaSummary(sla: Sla): { tone: Tone; text: string } {
  if (sla.state === 'STOPPED') {
    return sla.breached
      ? { tone: 'danger', text: `SLA aşıldı (${formatDuration(-sla.remainingSeconds)})` }
      : { tone: 'success', text: 'SLA karşılandı' };
  }
  if (sla.breached) {
    return { tone: 'danger', text: `${formatDuration(-sla.remainingSeconds)} gecikme` };
  }
  const remaining = `${formatDuration(sla.remainingSeconds)} kaldı`;
  if (sla.state === 'PAUSED') {
    return { tone: 'neutral', text: `Duraklatıldı · ${remaining}` };
  }
  const tone = sla.remainingSeconds < sla.targetSeconds * WARNING_SHARE ? 'warning' : 'info';
  return { tone, text: remaining };
}

export function SlaBadge({ sla }: { sla: Sla }) {
  const { tone, text } = slaSummary(sla);
  return (
    <Badge tone={tone} title={`Hedef: ${formatDuration(sla.targetSeconds)}`}>
      {text}
    </Badge>
  );
}
