import { useState, type FormEvent } from 'react';
import type {
  CannedResponse,
  CommentVisibility,
  CreateCommentRequest,
  KbArticleSummary,
} from '../api/types.ts';
import { fillPlaceholders, type PlaceholderValues } from '../placeholders.ts';
import { VISIBILITY_LABELS } from '../labels.ts';
import styles from './Comments.module.css';

interface Props {
  /** Staff can choose INTERNAL comments; customers always write EXTERNAL ones. */
  staff: boolean;
  pending: boolean;
  onSubmit: (request: CreateCommentRequest) => Promise<unknown>;
  /** Staff only: reply templates offered above the text box. */
  templates?: CannedResponse[];
  placeholders?: PlaceholderValues;
  /** Staff only: knowledge base articles that can be linked from the comment. */
  articles?: KbArticleSummary[];
}

export function CommentForm({ staff, pending, onSubmit, templates = [], placeholders, articles = [] }: Props) {
  const [body, setBody] = useState('');
  // Safe default for staff: a note meant for the team must not reach the customer by accident.
  const [visibility, setVisibility] = useState<CommentVisibility>(staff ? 'INTERNAL' : 'EXTERNAL');

  /** Adds the template below what is already written; the agent edits and sends it. */
  function insert(templateId: string) {
    const template = templates.find((t) => String(t.id) === templateId);
    if (!template) return;
    const text = placeholders ? fillPlaceholders(template.body, placeholders) : template.body;
    setBody((current) => (current.trim() ? `${current.trimEnd()}\n\n${text}` : text));
    setVisibility(template.visibility);
  }

  /** Adds the article title and its link; an internal-only article is not offered to the customer. */
  function insertArticle(articleId: string) {
    const article = articles.find((a) => String(a.id) === articleId);
    if (!article) return;
    const text = `${article.title}: ${window.location.origin}/kb/${article.id}`;
    setBody((current) => (current.trim() ? `${current.trimEnd()}\n\n${text}` : text));
  }

  // Customers cannot open INTERNAL articles, so only public ones are offered on a customer-visible comment.
  const linkable = articles.filter(
    (a) => a.visibility !== 'DRAFT' && (a.visibility === 'PUBLIC' || visibility === 'INTERNAL'),
  );

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!body.trim()) return;
    try {
      await onSubmit({ body: body.trim(), visibility: staff ? visibility : 'EXTERNAL' });
      setBody('');
    } catch {
      // The parent shows the error; keep the text so it can be sent again.
    }
  }

  return (
    <form className={styles.form} onSubmit={(e) => void submit(e)}>
      {staff && templates.length > 0 && (
        <div className={styles.template}>
          <label htmlFor="comment-template">Hazır yanıt</label>
          <select
            id="comment-template"
            value=""
            onChange={(e) => {
              insert(e.target.value);
            }}
          >
            <option value="">Seçin…</option>
            {templates.map((t) => (
              <option key={t.id} value={t.id}>
                {t.title}
                {t.visibility === 'INTERNAL' ? ' (dahili)' : ''}
              </option>
            ))}
          </select>
        </div>
      )}
      {staff && linkable.length > 0 && (
        <div className={styles.template}>
          <label htmlFor="comment-article">Bilgi bankası makalesi</label>
          <select
            id="comment-article"
            value=""
            onChange={(e) => {
              insertArticle(e.target.value);
            }}
          >
            <option value="">Seçin…</option>
            {linkable.map((a) => (
              <option key={a.id} value={a.id}>
                {a.title}
                {a.visibility === 'INTERNAL' ? ' (dahili)' : ''}
              </option>
            ))}
          </select>
        </div>
      )}
      <label htmlFor="comment-body">Yorum</label>
      <textarea id="comment-body" rows={3} value={body} onChange={(e) => setBody(e.target.value)} />
      <div className={styles.formFooter}>
        {staff && (
          <fieldset className={styles.visibility}>
            <legend>Görünürlük</legend>
            {(['INTERNAL', 'EXTERNAL'] as const).map((v) => (
              <label key={v}>
                <input
                  type="radio"
                  name="visibility"
                  value={v}
                  checked={visibility === v}
                  onChange={() => setVisibility(v)}
                />
                {VISIBILITY_LABELS[v]}
              </label>
            ))}
          </fieldset>
        )}
        <button type="submit" disabled={pending || body.trim() === ''}>
          Yorum ekle
        </button>
      </div>
    </form>
  );
}
