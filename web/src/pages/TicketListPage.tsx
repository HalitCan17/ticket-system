import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useTicketsApi } from '../api/ApiContext.tsx';
import type { TicketCategory, TicketScope, TicketStatus } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { isStaff } from '../auth/roles.ts';
import { PriorityBadge, SlaBadge, StatusBadge } from '../components/Badges.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { formatDateTime, userLabel } from '../format.ts';
import { CATEGORY_LABELS, CATEGORY_ORDER, STATUS_LABELS, STATUS_ORDER } from '../labels.ts';
import styles from './TicketListPage.module.css';

const PAGE_SIZE = 20;
/** Typing pauses this long before the search hits the API. */
export const SEARCH_DEBOUNCE_MS = 300;

const TABS: { scope: TicketScope; label: string }[] = [
  { scope: 'OPEN', label: 'Açık' },
  { scope: 'CLOSED', label: 'Kapanmış' },
  { scope: 'ALL', label: 'Tümü' },
];

function parseStatus(value: string | null): TicketStatus | undefined {
  return STATUS_ORDER.find((status) => status === value);
}

function parseCategory(value: string | null): TicketCategory | undefined {
  return CATEGORY_ORDER.find((category) => category === value);
}

/** 'me', 'unassigned' or a user id (from the dashboard's team table). */
type Queue = string;

/** Work queue presets: a person filter (assignee) or a group filter, never both. */
const QUEUES: { queue?: Queue; group?: string; label: string }[] = [
  { label: 'Herkesin' },
  { queue: 'me', label: 'Bana atananlar' },
  { queue: 'unassigned', label: 'Atanmamışlar' },
  { group: 'mine', label: 'Grubumun talepleri' },
];

function parseQueue(value: string | null): Queue | undefined {
  return value?.trim() ? value.trim() : undefined;
}

/** The URL omits the default tab (OPEN). */
function parseScope(value: string | null): TicketScope {
  return TABS.find((tab) => tab.scope === value?.toUpperCase())?.scope ?? 'OPEN';
}

interface Filters {
  q: string;
  scope: TicketScope;
  category?: TicketCategory;
  status?: TicketStatus;
  /** Staff only. */
  queue?: Queue;
  /** Staff only: 'mine' or a group id. */
  group?: string;
  sortBySla: boolean;
  page: number;
}

export function TicketListPage() {
  const api = useTicketsApi();
  const { user } = useAuth();
  const staff = isStaff(user.role);
  const [searchParams, setSearchParams] = useSearchParams();
  const filters: Filters = {
    q: searchParams.get('q') ?? '',
    scope: parseScope(searchParams.get('scope')),
    category: parseCategory(searchParams.get('category')),
    status: parseStatus(searchParams.get('status')),
    queue: staff ? parseQueue(searchParams.get('assignee')) : undefined,
    group: staff ? parseQueue(searchParams.get('group')) : undefined,
    sortBySla: staff && searchParams.get('sort') === 'sla',
    page: Math.max(0, Number(searchParams.get('page') ?? 0) || 0),
  };
  const [text, setText] = useState(filters.q);

  const tickets = useQuery({
    queryKey: ['tickets', filters],
    queryFn: () =>
      api.list({
        q: filters.q || undefined,
        scope: filters.scope,
        category: filters.category,
        status: filters.status,
        assignee: filters.queue,
        group: filters.group,
        sort: filters.sortBySla ? 'SLA' : undefined,
        page: filters.page,
        size: PAGE_SIZE,
      }),
  });

  function update(next: Partial<Filters>) {
    // Any filter change goes back to the first page unless a page is given.
    const merged = { ...filters, page: 0, ...next };
    const params = new URLSearchParams();
    if (merged.q) params.set('q', merged.q);
    if (merged.scope !== 'OPEN') params.set('scope', merged.scope.toLowerCase());
    if (merged.category) params.set('category', merged.category);
    if (merged.status) params.set('status', merged.status);
    if (merged.queue) params.set('assignee', merged.queue);
    if (merged.group) params.set('group', merged.group);
    if (merged.sortBySla) params.set('sort', 'sla');
    if (merged.page) params.set('page', String(merged.page));
    setSearchParams(params);
  }

  useEffect(() => {
    if (text.trim() === filters.q) return;
    const timer = setTimeout(() => update({ q: text.trim() }), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // Only typing triggers a search; the other filters update the URL directly.
  }, [text]);

  const filtered =
    filters.q !== '' ||
    filters.category !== undefined ||
    filters.status !== undefined ||
    filters.queue !== undefined ||
    filters.group !== undefined;

  return (
    <section>
      <div className={styles.toolbar}>
        <h1>Talepler</h1>
        <Link to="/tickets/new" className={styles.newLink}>
          + Yeni talep
        </Link>
      </div>

      <div className={styles.tabs} role="tablist" aria-label="Talep durumu">
        {TABS.map((tab) => (
          <button
            key={tab.scope}
            type="button"
            role="tab"
            aria-selected={filters.scope === tab.scope}
            className={filters.scope === tab.scope ? styles.activeTab : styles.tab}
            onClick={() => update({ scope: tab.scope })}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {staff && (
        <div className={styles.filters}>
          {filters.queue && filters.queue !== 'me' && filters.queue !== 'unassigned' && (
            <span className={styles.personQueue}>
              Kuyruk: {tickets.data?.items[0]?.assigneeName ?? 'seçili kişi'}
              <button
                type="button"
                className="secondary"
                aria-label="Kişi filtresini kaldır"
                onClick={() => update({ queue: undefined })}
              >
                ×
              </button>
            </span>
          )}
          {filters.group && filters.group !== 'mine' && (
            <span className={styles.personQueue}>
              Grup: {tickets.data?.items[0]?.groupName ?? 'seçili grup'}
              <button
                type="button"
                className="secondary"
                aria-label="Grup filtresini kaldır"
                onClick={() => update({ group: undefined })}
              >
                ×
              </button>
            </span>
          )}
          <div className={styles.segmented} role="group" aria-label="İş kuyruğu">
            {QUEUES.map((q) => {
              const active = filters.queue === q.queue && filters.group === q.group;
              return (
                <button
                  key={q.label}
                  type="button"
                  aria-pressed={active}
                  className={active ? styles.segmentActive : styles.segment}
                  onClick={() => update({ queue: q.queue, group: q.group })}
                >
                  {q.label}
                </button>
              );
            })}
          </div>
          <label className={styles.filter}>
            <span>Sıralama</span>
            <select
              value={filters.sortBySla ? 'sla' : 'newest'}
              onChange={(event) => update({ sortBySla: event.target.value === 'sla' })}
            >
              <option value="newest">En yeni</option>
              <option value="sla">SLA'sı en yakın</option>
            </select>
          </label>
        </div>
      )}

      <div className={styles.filters}>
        <label className={styles.search}>
          <span>Ara</span>
          <input
            type="search"
            placeholder="Başlık ya da #numara"
            value={text}
            maxLength={200}
            onChange={(event) => setText(event.target.value)}
          />
        </label>
        <label className={styles.filter}>
          <span>Kategori</span>
          <select
            value={filters.category ?? ''}
            onChange={(event) => update({ category: parseCategory(event.target.value) })}
          >
            <option value="">Tümü</option>
            {CATEGORY_ORDER.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.filter}>
          <span>Durum</span>
          <select
            value={filters.status ?? ''}
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
      </div>

      {tickets.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {tickets.isError && <ErrorMessage error={tickets.error} />}
      {tickets.data && tickets.data.items.length === 0 && (
        <p className={styles.muted}>{filtered ? 'Aramaya uyan talep yok.' : 'Burada talep yok.'}</p>
      )}

      {tickets.data && tickets.data.items.length > 0 && (
        <>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Başlık</th>
                  <th>Kategori</th>
                  {staff && <th>Grup</th>}
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
                    <td className={styles.muted}>{CATEGORY_LABELS[ticket.category]}</td>
                    {staff && <td className={styles.muted}>{ticket.groupName ?? '—'}</td>}
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
              disabled={filters.page === 0}
              onClick={() => update({ page: filters.page - 1 })}
            >
              Önceki
            </button>
            <span>
              Sayfa {tickets.data.page + 1} / {Math.max(1, tickets.data.totalPages)} · {tickets.data.totalElements} talep
            </span>
            <button
              type="button"
              className="secondary"
              disabled={filters.page + 1 >= tickets.data.totalPages}
              onClick={() => update({ page: filters.page + 1 })}
            >
              Sonraki
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
