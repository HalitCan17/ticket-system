import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router';
import { useKbApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { KbArticle, KbArticleRequest, KbVisibility, TicketCategory } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { isStaff } from '../auth/roles.ts';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { CATEGORY_LABELS, CATEGORY_ORDER, KB_VISIBILITY_LABELS, KB_VISIBILITY_ORDER } from '../labels.ts';
import styles from './Form.module.css';

/** New article (/kb/new) or edit (/kb/:id/edit); staff only. */
export function KbEditPage() {
  const { user } = useAuth();
  const api = useKbApi();
  const idParam = useParams().id;
  const id = idParam === undefined ? undefined : Number(idParam);

  const existing = useQuery({
    queryKey: ['kb', 'article', id],
    queryFn: () => api.get(id as number),
    enabled: id !== undefined && isStaff(user.role),
  });

  if (!isStaff(user.role)) {
    return (
      <section>
        <h1>Makale yaz</h1>
        <p>Bu sayfa yalnızca destek ekibi içindir.</p>
      </section>
    );
  }
  if (id !== undefined) {
    if (existing.isPending) return <p>Yükleniyor…</p>;
    if (existing.isError) return <ErrorMessage error={existing.error} />;
    return <ArticleForm article={existing.data} />;
  }
  return <ArticleForm />;
}

function ArticleForm({ article }: { article?: KbArticle }) {
  const api = useKbApi();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<KbArticleRequest>({
    title: article?.title ?? '',
    body: article?.body ?? '',
    category: article?.category ?? 'OTHER',
    visibility: article?.visibility ?? 'DRAFT',
  });

  const save = useMutation({
    mutationFn: (request: KbArticleRequest) => (article ? api.update(article.id, request) : api.create(request)),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['kb'] });
      void navigate(`/kb/${saved.id}`);
    },
  });

  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors : [];
  const fieldError = (field: keyof KbArticleRequest) => fieldErrors.find((e) => e.field === field)?.message;

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate({ ...form, title: form.title.trim(), body: form.body.trim() });
  }

  return (
    <section className={styles.form}>
      <h1>{article ? 'Makaleyi düzenle' : 'Yeni makale'}</h1>
      <form onSubmit={submit} aria-label="Makale">
        <div className={styles.field}>
          <label htmlFor="kb-title">Başlık</label>
          <input
            id="kb-title"
            required
            maxLength={200}
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          {fieldError('title') && <p className={styles.fieldError}>{fieldError('title')}</p>}
        </div>
        <div className={styles.field}>
          <label htmlFor="kb-body">İçerik</label>
          <textarea
            id="kb-body"
            required
            rows={12}
            maxLength={20_000}
            value={form.body}
            onChange={(e) => setForm({ ...form, body: e.target.value })}
          />
          {fieldError('body') && <p className={styles.fieldError}>{fieldError('body')}</p>}
        </div>
        <div className={styles.field}>
          <label htmlFor="kb-category">Kategori</label>
          <select
            id="kb-category"
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value as TicketCategory })}
          >
            {CATEGORY_ORDER.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.field}>
          <label htmlFor="kb-visibility">Görünürlük</label>
          <select
            id="kb-visibility"
            value={form.visibility}
            onChange={(e) => setForm({ ...form, visibility: e.target.value as KbVisibility })}
          >
            {KB_VISIBILITY_ORDER.map((v) => (
              <option key={v} value={v}>
                {KB_VISIBILITY_LABELS[v]}
              </option>
            ))}
          </select>
        </div>
        {save.isError && fieldErrors.length === 0 && <ErrorMessage error={save.error} />}
        <div className={styles.actions}>
          <button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
          <button
            type="button"
            className="secondary"
            onClick={() => void navigate(article ? `/kb/${article.id}` : '/kb')}
          >
            Vazgeç
          </button>
        </div>
      </form>
    </section>
  );
}
