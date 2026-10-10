import { useState, type FormEvent } from 'react';
import type { CommentVisibility, CreateCommentRequest } from '../api/types.ts';
import { VISIBILITY_LABELS } from '../labels.ts';
import styles from './Comments.module.css';

interface Props {
  /** Staff can choose INTERNAL comments; customers always write EXTERNAL ones. */
  staff: boolean;
  pending: boolean;
  onSubmit: (request: CreateCommentRequest) => Promise<unknown>;
}

export function CommentForm({ staff, pending, onSubmit }: Props) {
  const [body, setBody] = useState('');
  // Safe default for staff: a note meant for the team must not reach the customer by accident.
  const [visibility, setVisibility] = useState<CommentVisibility>(staff ? 'INTERNAL' : 'EXTERNAL');

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
