import type { CommentVisibility, TicketPriority, TicketStatus } from './api/types.ts';

export const STATUS_LABELS: Record<TicketStatus, string> = {
  NEW: 'Yeni',
  IN_PROGRESS: 'İşlemde',
  WAITING_FOR_CUSTOMER: 'Müşteri bekleniyor',
  RESOLVED: 'Çözüldü',
  CLOSED: 'Kapalı',
};

export const STATUS_ORDER: TicketStatus[] = ['NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: 'Düşük',
  MEDIUM: 'Orta',
  HIGH: 'Yüksek',
  CRITICAL: 'Kritik',
};

export const PRIORITY_ORDER: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export const VISIBILITY_LABELS: Record<CommentVisibility, string> = {
  INTERNAL: 'Dahili (yalnızca ekip)',
  EXTERNAL: 'Müşteriye açık',
};
