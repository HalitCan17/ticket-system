import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCannedResponsesApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { CannedResponse, CannedResponseRequest, CommentVisibility } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { VISIBILITY_LABELS } from '../labels.ts';
import { PLACEHOLDERS } from '../placeholders.ts';
import styles from './CannedResponsesPage.module.css';

const EMPTY: CannedResponseRequest = { title: '', body: '', visibility: 'EXTERNAL' };

/** Managers maintain the team's reply templates; see docs/adr/0012-canned-responses.md. */
export function CannedResponsesPage() {
  const { user } = useAuth();
  if (user.role !== 'MANAGER') {
    return (
      <section>
        <h1>Hazır yanıtlar</h1>
        <p>Bu sayfa yalnızca yöneticiler içindir.</p>
      </section>
    );
  }
  return <CannedResponsesEditor />;
}

function CannedResponsesEditor() {
  const api = useCannedResponsesApi();
  const queryClient = useQueryClient();
  /** null: closed; 'new' or an id: the form is open for that template */
  const [editing, setEditing] = useState<'new' | number | null>(null);
  const [form, setForm] = useState<CannedResponseRequest>(EMPTY);

  const list = useQuery({ queryKey: ['canned-responses'], queryFn: () => api.list() });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['canned-responses'] });

  const save = useMutation({
    mutationFn: (request: CannedResponseRequest) =>
      editing === 'new' || editing === null ? api.create(request) : api.update(editing, request),
    onSuccess: () => {
      setEditing(null);
      void refresh();
    },
  });
  const remove = useMutation({ mutationFn: (id: number) => api.remove(id), onSuccess: () => void refresh() });

  function open(target: 'new' | CannedResponse) {
    save.reset();
    if (target === 'new') {
      setEditing('new');
      setForm(EMPTY);
    } else {
      setEditing(target.id);
      setForm({ title: target.title, body: target.body, visibility: target.visibility });
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate({ ...form, title: form.title.trim(), body: form.body.trim() });
  }

  function confirmDelete(template: CannedResponse) {
    if (window.confirm(`"${template.title}" silinsin mi?`)) {
      remove.mutate(template.id);
    }
  }

  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors : [];
  const fieldError = (field: string) => fieldErrors.find((e) => e.field === field)?.message;

  return (
    <section className={styles.page}>
      <div className={styles.toolbar}>
        <h1>Hazır yanıtlar</h1>
        {editing === null && (
          <button type="button" onClick={() => open('new')}>
            + Yeni hazır yanıt
          </button>
        )}
      </div>
      <p className={styles.muted}>
        Destek ekibi yorum yazarken bu metinleri seçip ekler. Kullanılabilecek yer tutucular: {PLACEHOLDERS.join(', ')} (talep eden, talep numarası, yorumu
        yazan).
      </p>

      {editing !== null && (
        <form className={styles.form} onSubmit={submit} aria-label={editing === 'new' ? 'Yeni hazır yanıt' : 'Hazır yanıtı düzenle'}>
          <label htmlFor="canned-title">Başlık</label>
          <input
            id="canned-title"
            required
            maxLength={100}
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          {fieldError('title') && <p className={styles.fieldError}>{fieldError('title')}</p>}
          <label htmlFor="canned-body">Metin</label>
          <textarea
            id="canned-body"
            required
            rows={5}
            maxLength={5000}
            value={form.body}
            onChange={(e) => setForm({ ...form, body: e.target.value })}
          />
          {fieldError('body') && <p className={styles.fieldError}>{fieldError('body')}</p>}
          <fieldset className={styles.visibility}>
            <legend>Önerilen görünürlük</legend>
            {(['EXTERNAL', 'INTERNAL'] as CommentVisibility[]).map((v) => (
              <label key={v}>
                <input
                  type="radio"
                  name="canned-visibility"
                  value={v}
                  checked={form.visibility === v}
                  onChange={() => setForm({ ...form, visibility: v })}
                />
                {VISIBILITY_LABELS[v]}
              </label>
            ))}
          </fieldset>
          {save.isError && fieldErrors.length === 0 && <ErrorMessage error={save.error} />}
          <div className={styles.actions}>
            <button type="submit" disabled={save.isPending}>
              Kaydet
            </button>
            <button type="button" className="secondary" onClick={() => setEditing(null)}>
              Vazgeç
            </button>
          </div>
        </form>
      )}

      {list.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {list.isError && <ErrorMessage error={list.error} />}
      {remove.isError && <ErrorMessage error={remove.error} />}
      {list.data && list.data.length === 0 && <p className={styles.muted}>Henüz hazır yanıt yok.</p>}
      {list.data && list.data.length > 0 && (
        <ul className={styles.list}>
          {list.data.map((template) => (
            <li key={template.id} className={styles.item}>
              <div className={styles.head}>
                <strong>{template.title}</strong>
                {template.visibility === 'INTERNAL' && <span className={styles.badge}>Dahili</span>}
                <div className={styles.itemActions}>
                  <button type="button" className="secondary" onClick={() => open(template)}>
                    Düzenle
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    aria-label={`${template.title} sil`}
                    disabled={remove.isPending}
                    onClick={() => confirmDelete(template)}
                  >
                    Sil
                  </button>
                </div>
              </div>
              <p className={styles.body}>{template.body}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
