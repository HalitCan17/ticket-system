import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { useTicketsApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { CreateCommentRequest, Ticket, TicketStatus } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { isStaff } from '../auth/roles.ts';
import { Attachments } from '../components/Attachments.tsx';
import { PriorityBadge, SlaBadge, StatusBadge } from '../components/Badges.tsx';
import { CommentForm } from '../components/CommentForm.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { TransitionActions } from '../components/TransitionActions.tsx';
import { formatDateTime, formatDuration, userLabel } from '../format.ts';
import { STATUS_LABELS } from '../labels.ts';
import comments from '../components/Comments.module.css';
import styles from './TicketDetailPage.module.css';

export function TicketDetailPage() {
  const api = useTicketsApi();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const id = Number(useParams().id);
  const validId = Number.isInteger(id) && id > 0;

  const ticket = useQuery({ queryKey: ['ticket', id], queryFn: () => api.get(id), enabled: validId });
  const history = useQuery({ queryKey: ['history', id], queryFn: () => api.history(id), enabled: ticket.isSuccess });
  const commentList = useQuery({
    queryKey: ['comments', id],
    queryFn: () => api.comments(id),
    enabled: ticket.isSuccess,
  });

  const transition = useMutation({
    mutationFn: ({ target, note }: { target: TicketStatus; note?: string }) =>
      api.transition(id, { targetStatus: target, note }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['ticket', id], updated);
      void queryClient.invalidateQueries({ queryKey: ['history', id] });
      void queryClient.invalidateQueries({ queryKey: ['tickets'] });
    },
  });

  const addComment = useMutation({
    mutationFn: (request: CreateCommentRequest) => api.addComment(id, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['comments', id] }),
  });

  if (!validId || (ticket.error instanceof ApiError && ticket.error.status === 404)) {
    return (
      <section>
        <h1>Talep bulunamadı</h1>
        <Link to="/tickets">Taleplere dön</Link>
      </section>
    );
  }
  if (ticket.isPending) {
    return <p>Yükleniyor…</p>;
  }
  if (ticket.isError) {
    return <ErrorMessage error={ticket.error} />;
  }

  const t = ticket.data;
  const staff = isStaff(user.role);

  return (
    <article className={styles.page}>
      <p className={styles.back}>
        <Link to="/tickets">← Talepler</Link>
      </p>
      <header className={styles.header}>
        <h1>
          <span className={styles.id}>#{t.id}</span> {t.title}
        </h1>
        <div className={styles.badges}>
          <StatusBadge status={t.status} />
          <PriorityBadge priority={t.priority} />
          <SlaBadge sla={t.sla} />
        </div>
      </header>

      <div className={styles.columns}>
        <div className={styles.main}>
          <section className={styles.card}>
            <p className={styles.description}>{t.description}</p>
          </section>

          {t.resolutionNote && (
            <section className={`${styles.card} ${styles.resolution}`}>
              <h2>Çözüm</h2>
              <p className={styles.description}>{t.resolutionNote}</p>
            </section>
          )}

          <AutoCloseNotice ticket={t} customerView={!staff} />

          <TransitionActions
            key={t.status /* reset the note form after a status change */}
            status={t.status}
            allowed={t.allowedTransitions}
            role={user.role}
            pending={transition.isPending}
            onTransition={(target, note) => transition.mutate({ target, note })}
          />
          {transition.isError && <ErrorMessage error={transition.error} />}

          <Attachments ticketId={t.id} currentUserId={user.id} staff={staff} closed={t.status === 'CLOSED'} />

          <section>
            <h2>Yorumlar</h2>
            {commentList.isError && <ErrorMessage error={commentList.error} />}
            {commentList.data && commentList.data.length === 0 && <p className={styles.muted}>Henüz yorum yok.</p>}
            {commentList.data && commentList.data.length > 0 && (
              <ul className={comments.list}>
                {commentList.data.map((c) => (
                  <li
                    key={c.id}
                    className={`${comments.comment} ${c.visibility === 'INTERNAL' ? comments.internal : ''}`}
                  >
                    <div className={comments.meta}>
                      <span className={comments.author}>{userLabel(c.authorId, c.authorName, user.id)}</span>
                      <span>{formatDateTime(c.createdAt)}</span>
                      {c.visibility === 'INTERNAL' && <strong>Dahili</strong>}
                    </div>
                    <p className={comments.body}>{c.body}</p>
                  </li>
                ))}
              </ul>
            )}
            {t.status === 'CLOSED' ? (
              <p className={styles.muted}>Kapalı taleplere yorum eklenemez.</p>
            ) : (
              <CommentForm
                staff={staff}
                pending={addComment.isPending}
                onSubmit={(request) => addComment.mutateAsync(request)}
              />
            )}
            {addComment.isError && <ErrorMessage error={addComment.error} />}
          </section>
        </div>

        <aside className={styles.side}>
          <dl className={styles.facts}>
            <dt>Talep eden</dt>
            <dd>{userLabel(t.requesterId, t.requesterName, user.id)}</dd>
            <dt>Atanan</dt>
            <dd>{userLabel(t.assigneeId, t.assigneeName, user.id)}</dd>
            <dt>Açılış</dt>
            <dd>{formatDateTime(t.createdAt)}</dd>
            <dt>SLA hedefi</dt>
            <dd>{formatDuration(t.sla.targetSeconds)}</dd>
            <dt>Harcanan süre</dt>
            <dd>{formatDuration(t.sla.elapsedSeconds)}</dd>
            {t.sla.dueAt && (
              <>
                <dt>SLA bitişi</dt>
                <dd>{formatDateTime(t.sla.dueAt)}</dd>
              </>
            )}
          </dl>

          <h2>Geçmiş</h2>
          {history.isError && <ErrorMessage error={history.error} />}
          <ol className={styles.timeline}>
            {history.data?.map((entry, index) => (
              <li key={index}>
                <div>
                  <strong>{STATUS_LABELS[entry.toStatus]}</strong>
                  <span className={styles.muted}> · {userLabel(entry.changedBy, entry.changedByName, user.id)}</span>
                </div>
                <div className={styles.muted}>{formatDateTime(entry.changedAt)}</div>
                {entry.note && <p className={styles.note}>{entry.note}</p>}
              </li>
            ))}
          </ol>
        </aside>
      </div>
    </article>
  );
}

function AutoCloseNotice({ ticket, customerView }: { ticket: Ticket; customerView: boolean }) {
  if (!ticket.autoCloseAt) {
    return null;
  }
  const when = formatDateTime(ticket.autoCloseAt);
  return (
    <p className={styles.notice} role="status">
      {customerView
        ? `Çözümü onaylayabilir ya da itiraz edebilirsiniz. Yanıt vermezseniz talep ${when} tarihinde otomatik kapanacak.`
        : `Müşteri yanıt vermezse talep ${when} tarihinde otomatik kapanacak.`}
    </p>
  );
}
