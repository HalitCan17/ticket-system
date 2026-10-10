import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useTicketsApi } from '../api/ApiContext.tsx';
import type { TicketStatus } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { PriorityBadge, SlaBadge, StatusBadge } from '../components/Badges.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { formatDateTime, userLabel } from '../format.ts';
import { STATUS_LABELS, STATUS_ORDER } from '../labels.ts';
import styles from './TicketListPage.module.css';

const PAGE_SIZE = 20;

function parseStatus(value: string | null): TicketStatus | undefined {
  return STATUS_ORDER.find((status) => status === value);
}

export function TicketListPage() {
  const api = useTicketsApi();
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const status = parseStatus(searchParams.get('status'));
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0);

  const tickets = useQuery({
    queryKey: ['tickets', { status, page }],
    queryFn: () => api.list({ status, page, size: PAGE_SIZE }),
  });

  function update(next: { status?: TicketStatus; page?: number }) {
    const params = new URLSearchParams();
    if (next.status) params.set('status', next.status);
    if (next.page) params.set('page', String(next.page));
    setSearchParams(params);
  }

  return (
    <section>
      <div className={styles.toolbar}>
        <h1>Talepler</h1>
        <label className={styles.filter}>
          <span>Durum</span>
          <select
            value={status ?? ''}
            onChange={(event) => update({ status: parseStatus(event.target.value) })}
          >
            <option value="">Tümü</option>
            {STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </label>
        <Link to="/tickets/new" className={styles.newLink}>
          + Yeni talep
        </Link>
      </div>

      {tickets.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {tickets.isError && <ErrorMessage error={tickets.error} />}
      {tickets.data && tickets.data.items.length === 0 && (
        <p className={styles.muted}>{status ? 'Bu durumda talep yok.' : 'Henüz talep yok.'}</p>
      )}

      {tickets.data && tickets.data.items.length > 0 && (
        <>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Başlık</th>
                  <th>Öncelik</th>
                  <th>Durum</th>
                  <th>SLA</th>
                  <th>Talep eden</th>
                  <th>Atanan</th>
                  <th>Açılış</th>
                </tr>
              </thead>
              <tbody>
                {tickets.data.items.map((ticket) => (
                  <tr key={ticket.id}>
                    <td className={styles.muted}>{ticket.id}</td>
                    <td>
                      <Link to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
                    </td>
                    <td>
                      <PriorityBadge priority={ticket.priority} />
                    </td>
                    <td>
                      <StatusBadge status={ticket.status} />
                    </td>
                    <td>
                      <SlaBadge sla={ticket.sla} />
                    </td>
                    <td>{userLabel(ticket.requesterId, ticket.requesterName, user.id)}</td>
                    <td>{userLabel(ticket.assigneeId, ticket.assigneeName, user.id)}</td>
                    <td className={styles.muted}>{formatDateTime(ticket.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <nav className={styles.pager} aria-label="Sayfalama">
            <button
              type="button"
              className="secondary"
              disabled={page === 0}
              onClick={() => update({ status, page: page - 1 })}
            >
              Önceki
            </button>
            <span>
              Sayfa {tickets.data.page + 1} / {Math.max(1, tickets.data.totalPages)} · {tickets.data.totalElements} talep
            </span>
            <button
              type="button"
              className="secondary"
              disabled={page + 1 >= tickets.data.totalPages}
              onClick={() => update({ status, page: page + 1 })}
            >
              Sonraki
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
