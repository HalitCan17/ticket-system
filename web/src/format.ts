const dateTime = new Intl.DateTimeFormat('tr-TR', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** Compact Turkish duration, two largest units: "2 g 4 sa", "3 sa 20 dk", "45 dk", "1 dk'dan az". */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.abs(Math.trunc(totalSeconds));
  if (seconds < 60) {
    return "1 dk'dan az";
  }
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const parts: string[] = [];
  if (days > 0) parts.push(`${days} g`);
  if (hours > 0) parts.push(`${hours} sa`);
  if (minutes > 0 && days === 0) parts.push(`${minutes} dk`);
  return parts.slice(0, 2).join(' ');
}

/** "512 B", "34 KB", "2,4 MB" (Turkish decimal comma). */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString('tr-TR', { maximumFractionDigits: 1 })} MB`;
}

/**
 * How to show a user referenced by id: "Siz" for the signed-in user, otherwise the name from the
 * backend's user directory, falling back to a short id.
 */
export function userLabel(id: string | null, name: string | null, currentUserId: string): string {
  if (id === null) {
    return '—';
  }
  if (id === currentUserId) {
    return 'Siz';
  }
  return name ?? id.slice(0, 8);
}
