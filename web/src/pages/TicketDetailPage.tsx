import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { useCannedResponsesApi, useGroupsApi, useTicketsApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { Activity, CreateCommentRequest, StatusHistoryEntry, Ticket, TicketStatus, UpdateTicketRequest } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { isStaff } from '../auth/roles.ts';
import { AssignmentPanel } from '../components/AssignmentPanel.tsx';
import { Attachments } from '../components/Attachments.tsx';
import { PriorityBadge, SlaBadge, StatusBadge } from '../components/Badges.tsx';
import { CommentForm } from '../components/CommentForm.tsx';
import { EditTicketPanel } from '../components/EditTicketPanel.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { Rating } from '../components/Rating.tsx';
import { TransitionActions } from '../components/TransitionActions.tsx';
import { formatDateTime, formatDuration, userLabel } from '../format.ts';
import { CATEGORY_LABELS, PRIORITY_LABELS, STATUS_LABELS } from '../labels.ts';
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
  const staffView = isStaff(user.role);
  const cannedApi = useCannedResponsesApi();
  const groupsApi = useGroupsApi();
  const groups = useQuery({
    queryKey: ['groups'],
    queryFn: () => groupsApi.list(),
    enabled: staffView,
    staleTime: 60_000,
  });
  const templates = useQuery({
    queryKey: ['canned-responses'],
    queryFn: () => cannedApi.list(),
    enabled: staffView,
    staleTime: 60_000,
  });
  const activities = useQuery({
    queryKey: ['activities', id],
    queryFn: () => api.activities(id),
    enabled: ticket.isSuccess && staffView,
  });
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

  const changed = (updated: Ticket) => {
    queryClient.setQueryData(['ticket', id], updated);
    void queryClient.invalidateQueries({ queryKey: ['activities', id] });
    void queryClient.invalidateQueries({ queryKey: ['tickets'] });
  };
  const assign = useMutation({
    mutationFn: (assigneeId: string | null) => api.assign(id, assigneeId),
    onSuccess: changed,
  });
  const update = useMutation({
    mutationFn: (request: UpdateTicketRequest) => api.update(id, request),
    onSuccess: changed,
  });

  const rate = useMutation({
    mutationFn: ({ score, comment }: { score: number; comment?: string }) => api.rate(id, { score, comment }),
    onSuccess: (updated) => queryClient.setQueryData(['ticket', id], updated),
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
  const staff = staffView;

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

          <Rating
            ticket={t}
            pending={rate.isPending}
            onRate={(score, comment) => rate.mutate({ score, comment })}
          />
          {rate.isError && <ErrorMessage error={rate.error} />}

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
                templates={templates.data}
                placeholders={{
                  customer: t.requesterName ?? 'Sayın müşterimiz',
                  ticketId: t.id,
                  me: user.displayName,
                }}
                pending={addComment.isPending}
                onSubmit={(request) => addComment.mutateAsync(request)}
              />
            )}
            {addComment.isError && <ErrorMessage error={addComment.error} />}
          </section>
        </div>

        <aside className={styles.side}>
          <dl className={styles.facts}>
            <dt>Kategori</dt>
            <dd>{CATEGORY_LABELS[t.category]}</dd>
            <dt>Destek grubu</dt>
            <dd>{t.groupName ?? '—'}</dd>
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

          <AssignmentPanel
            ticket={t}
            currentUserId={user.id}
            role={user.role}
            pending={assign.isPending}
            onAssign={(assigneeId) => assign.mutate(assigneeId)}
          />
          {assign.isError && <ErrorMessage error={assign.error} />}
          <EditTicketPanel
            ticket={t}
            groups={groups.data ?? []}
            pending={update.isPending}
            onSave={(request) => update.mutate(request)}
          />
          {update.isError && <ErrorMessage error={update.error} />}

          <h2>Geçmiş</h2>
          {history.isError && <ErrorMessage error={history.error} />}
          {activities.isError && <ErrorMessage error={activities.error} />}
          <ol className={styles.timeline}>
            {timeline(history.data ?? [], activities.data ?? []).map((item) => (
              <li key={item.key}>
                <div>
                  <strong>{item.title}</strong>
                  <span className={styles.muted}> · {userLabel(item.actorId, item.actorName, user.id)}</span>
                </div>
                <div className={styles.muted}>{formatDateTime(item.at)}</div>
                {item.note && <p className={styles.note}>{item.note}</p>}
              </li>
            ))}
          </ol>
        </aside>
      </div>
    </article>
  );
}

interface TimelineItem {
  key: string;
  title: string;
  actorId: string;
  actorName: string | null;
  note: string | null;
  at: string;
}

function activityTitle(activity: Activity): string {
  switch (activity.type) {
    case 'ASSIGNEE_CHANGED':
      return `Atama: ${activity.oldName ?? (activity.oldValue ? activity.oldValue : 'atanmamış')} → ${
        activity.newName ?? (activity.newValue ? activity.newValue : 'atanmamış')
      }`;
    case 'PRIORITY_CHANGED':
      return `Öncelik: ${label(PRIORITY_LABELS, activity.oldValue)} → ${label(PRIORITY_LABELS, activity.newValue)}`;
    case 'CATEGORY_CHANGED':
      return `Kategori: ${label(CATEGORY_LABELS, activity.oldValue)} → ${label(CATEGORY_LABELS, activity.newValue)}`;
    case 'GROUP_CHANGED':
      return `Grup: ${groupLabel(activity.oldValue, activity.oldName)} → ${groupLabel(activity.newValue, activity.newName)}`;
  }
}

/** No value: no group; a value without a name: the group was deleted since. */
function groupLabel(id: string | null, name: string | null): string {
  if (id === null) return 'grupsuz';
  return name ?? 'silinmiş grup';
}

function label(labels: Record<string, string>, value: string | null): string {
  return value ? (labels[value] ?? value) : '—';
}

/** Status history and (staff only) activities in one list, oldest first; ties keep status entries first. */
function timeline(history: StatusHistoryEntry[], activities: Activity[]): TimelineItem[] {
  const items: TimelineItem[] = [
    ...history.map((entry, index) => ({
      key: `s${index}`,
      title: STATUS_LABELS[entry.toStatus],
      actorId: entry.changedBy,
      actorName: entry.changedByName,
      note: entry.note,
      at: entry.changedAt,
    })),
    ...activities.map((activity) => ({
      key: `a${activity.id}`,
      title: activityTitle(activity),
      actorId: activity.actorId,
      actorName: activity.actorName,
      note: activity.note,
      at: activity.createdAt,
    })),
  ];
  // Stable sort: equal times keep the order above.
  return items.sort((a, b) => Date.parse(a.at) - Date.parse(b.at));
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
