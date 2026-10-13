import type { AppNotification } from '../api/types.ts';
import { STATUS_LABELS } from '../labels.ts';

/** The sentence for a notification; the backend only sends its type and facts. */
export function notificationText(n: AppNotification): string {
  const actor = n.actorName ?? 'Bir kullanıcı';
  switch (n.type) {
    case 'STATUS_CHANGED':
      return n.ticketStatus
        ? `${actor} durumu "${STATUS_LABELS[n.ticketStatus]}" olarak değiştirdi`
        : `${actor} durumu değiştirdi`;
    case 'AUTO_CLOSED':
      return 'Yanıt verilmediği için talep otomatik kapatıldı';
    case 'COMMENT_ADDED':
      return `${actor} yorum yazdı`;
    case 'INTERNAL_COMMENT_ADDED':
      return `${actor} dahili not ekledi`;
    case 'SLA_BREACHED':
      return 'SLA süresi aşıldı';
    case 'ATTACHMENT_ADDED':
      return `${actor} dosya ekledi`;
    case 'TICKET_RATED':
      return `${actor} çözümü puanladı`;
    case 'TICKET_ASSIGNED':
      return `${actor} talebi size atadı`;
    case 'TICKET_UNASSIGNED':
      return `${actor} talebi sizden aldı`;
    case 'PRIORITY_CHANGED':
      return `${actor} önceliği değiştirdi`;
    case 'SLA_WARNING':
      return "SLA süresinin %80'i doldu";
    case 'FIRST_RESPONSE_BREACHED':
      return 'İlk yanıt süresi aşıldı';
    case 'APPROVAL_REQUESTED':
      return `${actor} onayınızı bekleyen bir hizmet talebi açtı`;
    case 'REQUEST_APPROVED':
      return `${actor} hizmet talebinizi onayladı`;
    case 'REQUEST_REJECTED':
      return `${actor} hizmet talebinizi reddetti`;
  }
}
