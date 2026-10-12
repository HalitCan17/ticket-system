import type { CommentVisibility, TicketCategory, TicketPriority, TicketStatus } from './api/types.ts';

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

export const CATEGORY_LABELS: Record<TicketCategory, string> = {
  HARDWARE: 'Donanım',
  SOFTWARE: 'Yazılım',
  NETWORK: 'Ağ',
  ACCESS: 'Hesap / erişim',
  OTHER: 'Diğer',
};

export const CATEGORY_ORDER: TicketCategory[] = ['HARDWARE', 'SOFTWARE', 'NETWORK', 'ACCESS', 'OTHER'];

export const VISIBILITY_LABELS: Record<CommentVisibility, string> = {
  INTERNAL: 'Dahili (yalnızca ekip)',
  EXTERNAL: 'Müşteriye açık',
};
