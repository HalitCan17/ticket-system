import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useCatalogApi } from '../api/ApiContext.tsx';
import type { ServiceRequest } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { isStaff } from '../auth/roles.ts';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { formatDateTime } from '../format.ts';
import { REQUEST_STATUS_LABELS } from '../labels.ts';
import styles from './Catalog.module.css';

export function RequestStatusBadge({ request }: { request: ServiceRequest }) {
  const tone =
    request.status === 'PENDING_APPROVAL' ? styles.pending : request.status === 'REJECTED' ? styles.rejected : '';
  return <span className={`${styles.badge} ${tone}`}>{REQUEST_STATUS_LABELS[request.status]}</span>;
}

/** The caller's service requests; staff can switch to everybody's. */
export function ServiceRequestsPage() {
  const api = useCatalogApi();
  const { user } = useAuth();
  const staff = isStaff(user.role);
  const [params, setParams] = useSearchParams();
  const all = staff && params.get('all') === '1';
  const requests = useQuery({
    queryKey: ['service-requests', 'list', all],
    queryFn: () => api.requests({ mine: !all, size: 50 }),
  });

  return (
    <section className={styles.page}>
      <div className={styles.toolbar}>
        <h1>{all ? 'Hizmet talepleri' : 'Hizmet taleplerim'}</h1>
        <Link to="/catalog" className={styles.toolbarLink}>
          + Yeni istek
        </Link>
      </div>
      {staff && (
        <div className={styles.tabs} role="group" aria-label="Kimin talepleri">
          <button type="button" className={all ? 'secondary' : ''} aria-pressed={!all} onClick={() => setParams({})}>
            Benim
          </button>
          <button type="button" className={all ? '' : 'secondary'} aria-pressed={all} onClick={() => setParams({ all: '1' })}>
            Herkesin
          </button>
        </div>
      )}
      {requests.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {requests.isError && <ErrorMessage error={requests.error} />}
      {requests.data && requests.data.items.length === 0 && <p className={styles.muted}>Hizmet talebi yok.</p>}
      {requests.data && requests.data.items.length > 0 && (
        <ul className={styles.list} aria-label="Hizmet talepleri">
          {requests.data.items.map((r) => (
            <li key={r.id} className={styles.row}>
              <Link to={`/requests/${r.id}`}>
                #{r.id} {r.itemName}
              </Link>
              {all && <span className={styles.muted}>{r.requesterName ?? r.requesterId}</span>}
              <span className={styles.muted}>{formatDateTime(r.createdAt)}</span>
              <RequestStatusBadge request={r} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
