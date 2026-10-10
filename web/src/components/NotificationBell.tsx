import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { useNotificationsApi } from '../api/ApiContext.tsx';
import type { AppNotification } from '../api/types.ts';
import { formatDateTime } from '../format.ts';
import { ErrorMessage } from './ErrorMessage.tsx';
import { notificationText } from './notificationText.ts';
import styles from './NotificationBell.module.css';

/** How often the unread count is refreshed (in-app notifications only, no push). */
export const POLL_INTERVAL_MS = 30_000;
const LIST_SIZE = 10;

export function NotificationBell() {
  const api = useNotificationsApi();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const container = useRef<HTMLDivElement>(null);

  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => api.unreadCount(),
    refetchInterval: POLL_INTERVAL_MS,
  });
  const list = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => api.list({ size: LIST_SIZE }),
    enabled: open,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({ mutationFn: (id: number) => api.markRead(id), onSuccess: refresh });
  const markAllRead = useMutation({ mutationFn: () => api.markAllRead(), onSuccess: refresh });

  // Close on outside click and Escape.
  useEffect(() => {
    if (!open) return;
    function onPointer(event: MouseEvent) {
      if (container.current && !container.current.contains(event.target as Node)) setOpen(false);
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  function openNotification(n: AppNotification) {
    if (!n.read) {
      markRead.mutate(n.id);
    }
    setOpen(false);
    void navigate(`/tickets/${n.ticketId}`);
  }

  const count = unread.data?.count ?? 0;

  return (
    <div className={styles.bell} ref={container}>
      <button
        type="button"
        className={`secondary ${styles.trigger}`}
        aria-expanded={open}
        aria-haspopup="true"
        aria-label={count > 0 ? `Bildirimler, ${count} okunmamış` : 'Bildirimler'}
        onClick={() => setOpen((o) => !o)}
      >
        <span aria-hidden="true">🔔</span>
        {count > 0 && <span className={styles.count}>{count > 99 ? '99+' : count}</span>}
      </button>

      {open && (
        <div className={styles.panel} role="dialog" aria-label="Bildirimler">
          <div className={styles.panelHeader}>
            <strong>Bildirimler</strong>
            {count > 0 && (
              <button
                type="button"
                className={`secondary ${styles.small}`}
                disabled={markAllRead.isPending}
                onClick={() => markAllRead.mutate()}
              >
                Tümünü okundu işaretle
              </button>
            )}
          </div>
          {list.isPending && <p className={styles.empty}>Yükleniyor…</p>}
          {list.isError && <ErrorMessage error={list.error} />}
          {list.data && list.data.items.length === 0 && <p className={styles.empty}>Bildirim yok.</p>}
          {list.data && list.data.items.length > 0 && (
            <ul className={styles.list}>
              {list.data.items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    className={`${styles.item} ${n.read ? '' : styles.unread}`}
                    onClick={() => openNotification(n)}
                  >
                    <span className={styles.title}>
                      #{n.ticketId} {n.ticketTitle ?? ''}
                    </span>
                    <span>{notificationText(n)}</span>
                    <span className={styles.time}>{formatDateTime(n.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
