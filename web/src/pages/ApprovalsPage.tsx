import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useCatalogApi } from '../api/ApiContext.tsx';
import { useAuth } from '../auth/AuthContext.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { formatDateTime } from '../format.ts';
import { DecisionForm } from './ServiceRequestPage.tsx';
import styles from './Catalog.module.css';

/** Managers' queue of service requests waiting for a decision, oldest first. */
export function ApprovalsPage() {
  const { user } = useAuth();
  if (user.role !== 'MANAGER') {
    return (
      <section>
        <h1>Onaylar</h1>
        <p>Bu sayfa yalnızca yöneticiler içindir.</p>
      </section>
    );
  }
  return <Approvals />;
}

function Approvals() {
  const api = useCatalogApi();
  const pending = useQuery({
    queryKey: ['service-requests', 'pending'],
    queryFn: () => api.requests({ status: 'PENDING_APPROVAL', size: 100 }),
  });
  const items = [...(pending.data?.items ?? [])].reverse();

  return (
    <section className={styles.page}>
      <h1>Onay bekleyen hizmet talepleri</h1>
      {pending.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {pending.isError && <ErrorMessage error={pending.error} />}
      {pending.data && items.length === 0 && <p className={styles.muted}>Onay bekleyen talep yok.</p>}
      <ul className={styles.list} aria-label="Onay bekleyenler">
        {items.map((r) => (
          <li key={r.id} className={styles.card}>
            <h2>
              <Link to={`/requests/${r.id}`}>
                #{r.id} {r.itemName}
              </Link>
            </h2>
            <span className={styles.muted}>
              {r.requesterName ?? r.requesterId} · {formatDateTime(r.createdAt)}
            </span>
            <dl className={styles.answers}>
              {r.fields
                .filter((f) => r.answers[f.key] !== undefined)
                .map((f) => (
                  <div key={f.key} style={{ display: 'contents' }}>
                    <dt>{f.label}</dt>
                    <dd>{r.answers[f.key]}</dd>
                  </div>
                ))}
            </dl>
            {r.canDecide ? (
              <DecisionForm id={r.id} />
            ) : (
              <p className={styles.muted}>Kendi talebinize karar veremezsiniz.</p>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
