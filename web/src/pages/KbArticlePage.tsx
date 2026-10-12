import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { useKbApi } from '../api/ApiContext.tsx';
import { useAuth } from '../auth/AuthContext.tsx';
import { isStaff } from '../auth/roles.ts';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { formatDateTime } from '../format.ts';
import { CATEGORY_LABELS, KB_VISIBILITY_LABELS } from '../labels.ts';
import styles from './KbPages.module.css';

export function KbArticlePage() {
  const api = useKbApi();
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const id = Number(useParams().id);
  const valid = Number.isInteger(id) && id > 0;
  const staff = isStaff(user.role);

  const article = useQuery({ queryKey: ['kb', 'article', id], queryFn: () => api.get(id), enabled: valid });

  const vote = useMutation({
    mutationFn: (helpful: boolean) => api.vote(id, helpful),
    onSuccess: (updated) => queryClient.setQueryData(['kb', 'article', id], updated),
  });

  const remove = useMutation({
    mutationFn: () => api.remove(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['kb'] });
      void navigate('/kb');
    },
  });

  if (!valid) return <p>Geçersiz makale numarası.</p>;
  if (article.isPending) return <p className={styles.muted}>Yükleniyor…</p>;
  if (article.isError) {
    return (
      <section className={styles.page}>
        <ErrorMessage error={article.error} />
        <Link to="/kb">Bilgi bankasına dön</Link>
      </section>
    );
  }
  const a = article.data;

  return (
    <section className={styles.page}>
      <p>
        <Link to="/kb">← Bilgi bankası</Link>
      </p>
      <h1>{a.title}</h1>
      <div className={styles.meta}>
        <span>{CATEGORY_LABELS[a.category]}</span>
        {a.visibility !== 'PUBLIC' && <span className={styles.badge}>{KB_VISIBILITY_LABELS[a.visibility]}</span>}
        <span>Güncellendi: {formatDateTime(a.updatedAt)}</span>
      </div>
      <article className={styles.article}>
        <p className={styles.body}>{a.body}</p>
      </article>

      <div className={styles.votes} role="group" aria-label="Bu makale işinize yaradı mı?">
        <span>Bu makale işinize yaradı mı?</span>
        <button
          type="button"
          className="secondary"
          aria-pressed={a.myVote === true}
          disabled={vote.isPending}
          onClick={() => vote.mutate(true)}
        >
          Evet ({a.helpfulYes})
        </button>
        <button
          type="button"
          className="secondary"
          aria-pressed={a.myVote === false}
          disabled={vote.isPending}
          onClick={() => vote.mutate(false)}
        >
          Hayır ({a.helpfulNo})
        </button>
      </div>
      {vote.isError && <ErrorMessage error={vote.error} />}

      {staff && (
        <div className={styles.actions}>
          <Link to={`/kb/${a.id}/edit`}>Düzenle</Link>
          {user.role === 'MANAGER' && (
            <button
              type="button"
              className="secondary"
              disabled={remove.isPending}
              onClick={() => {
                if (window.confirm('Bu makale silinsin mi?')) remove.mutate();
              }}
            >
              Sil
            </button>
          )}
        </div>
      )}
      {remove.isError && <ErrorMessage error={remove.error} />}
    </section>
  );
}
