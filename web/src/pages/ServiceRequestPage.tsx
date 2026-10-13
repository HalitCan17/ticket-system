import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { useCatalogApi } from '../api/ApiContext.tsx';
import type { ServiceRequest } from '../api/types.ts';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { formatDateTime } from '../format.ts';
import { RequestStatusBadge } from './ServiceRequestsPage.tsx';
import styles from './Catalog.module.css';

export function ServiceRequestPage() {
  const api = useCatalogApi();
  const id = Number(useParams().id);
  const valid = Number.isInteger(id) && id > 0;
  const request = useQuery({ queryKey: ['service-requests', 'one', id], queryFn: () => api.request(id), enabled: valid });

  if (!valid) return <p>Geçersiz talep numarası.</p>;
  if (request.isPending) return <p className={styles.muted}>Yükleniyor…</p>;
  if (request.isError) {
    return (
      <section className={styles.page}>
        <ErrorMessage error={request.error} />
        <Link to="/requests">Hizmet taleplerine dön</Link>
      </section>
    );
  }
  return <RequestDetail request={request.data} />;
}

export function RequestDetail({ request: r }: { request: ServiceRequest }) {
  const answered = r.fields.filter((f) => r.answers[f.key] !== undefined);
  return (
    <section className={styles.page}>
      <p>
        <Link to="/requests">← Hizmet talepleri</Link>
      </p>
      <h1>
        Hizmet talebi #{r.id}: {r.itemName}
      </h1>
      <div className={styles.meta}>
        <RequestStatusBadge request={r} />
        <span>
          {r.requesterName ?? r.requesterId} · {formatDateTime(r.createdAt)}
        </span>
      </div>

      {answered.length > 0 && (
        <dl className={styles.answers}>
          {answered.map((f) => (
            <div key={f.key} style={{ display: 'contents' }}>
              <dt>{f.label}</dt>
              <dd>{r.answers[f.key]}</dd>
            </div>
          ))}
        </dl>
      )}

      {r.status === 'PENDING_APPROVAL' && <p className={styles.muted}>Bir yöneticinin onayını bekliyor.</p>}
      {r.decidedAt && (
        <div className={styles.panel} aria-label="Karar">
          <h2>{r.status === 'REJECTED' ? 'Reddedildi' : 'Onaylandı'}</h2>
          <span className={styles.muted}>
            {r.approverName ?? r.approverId} · {formatDateTime(r.decidedAt)}
          </span>
          {r.decisionComment && <p>{r.decisionComment}</p>}
        </div>
      )}
      {r.ticketId != null && (
        <p>
          Destek ekibi bu isteği <Link to={`/tickets/${r.ticketId}`}>#{r.ticketId} numaralı talepte</Link> karşılıyor.
        </p>
      )}

      {r.canCancel && <CancelButton id={r.id} />}
      {r.canDecide && <DecisionForm id={r.id} />}
    </section>
  );
}

function CancelButton({ id }: { id: number }) {
  const api = useCatalogApi();
  const queryClient = useQueryClient();
  const cancel = useMutation({
    mutationFn: () => api.cancel(id),
    onSuccess: (updated) => {
      queryClient.setQueryData(['service-requests', 'one', id], updated);
      void queryClient.invalidateQueries({ queryKey: ['service-requests', 'list'] });
    },
  });
  return (
    <div className={styles.actions}>
      <button
        type="button"
        className="secondary"
        disabled={cancel.isPending}
        onClick={() => {
          if (window.confirm('Bu istek geri çekilsin mi?')) cancel.mutate();
        }}
      >
        Geri çek
      </button>
      {cancel.isError && <ErrorMessage error={cancel.error} />}
    </div>
  );
}

/** Managers approve, or reject with a reason. */
export function DecisionForm({ id, onDone }: { id: number; onDone?: () => void }) {
  const api = useCatalogApi();
  const queryClient = useQueryClient();
  const [comment, setComment] = useState('');
  const decide = useMutation({
    mutationFn: (approved: boolean) => api.decide(id, approved, comment.trim() || undefined),
    onSuccess: (updated) => {
      queryClient.setQueryData(['service-requests', 'one', id], updated);
      void queryClient.invalidateQueries({ queryKey: ['service-requests'] });
      onDone?.();
    },
  });
  return (
    <form className={styles.panel} aria-label={`Karar #${id}`} onSubmit={(e) => e.preventDefault()}>
      <h2>Karar</h2>
      <label htmlFor={`decision-comment-${id}`}>Not (reddederken zorunlu)</label>
      <textarea
        id={`decision-comment-${id}`}
        rows={2}
        maxLength={1000}
        value={comment}
        onChange={(e) => setComment(e.target.value)}
      />
      <div className={styles.actions}>
        <button type="button" disabled={decide.isPending} onClick={() => decide.mutate(true)}>
          Onayla
        </button>
        <button
          type="button"
          className="secondary"
          disabled={decide.isPending || comment.trim() === ''}
          onClick={() => decide.mutate(false)}
        >
          Reddet
        </button>
      </div>
      {decide.isError && <ErrorMessage error={decide.error} />}
    </form>
  );
}
