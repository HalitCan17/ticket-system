import { useState, type FormEvent } from 'react';
import type { Ticket } from '../api/types.ts';
import { formatDateTime } from '../format.ts';
import styles from './Rating.module.css';

const SCORES = [1, 2, 3, 4, 5] as const;
export const SCORE_LABELS: Record<number, string> = {
  1: 'Çok kötü',
  2: 'Kötü',
  3: 'Orta',
  4: 'İyi',
  5: 'Çok iyi',
};

function Stars({ score }: { score: number }) {
  return (
    <span className={styles.stars} aria-hidden="true">
      {'★'.repeat(score)}
      <span className={styles.empty}>{'★'.repeat(5 - score)}</span>
    </span>
  );
}

interface Props {
  ticket: Ticket;
  pending: boolean;
  onRate: (score: number, comment?: string) => void;
}

/** Shows the requester's rating, or the form while they may rate (ticket.canRate from the backend). */
export function Rating({ ticket, pending, onRate }: Props) {
  const [score, setScore] = useState<number | null>(null);
  const [comment, setComment] = useState('');

  if (ticket.rating) {
    const { rating } = ticket;
    return (
      <section className={styles.card} aria-label="Memnuniyet puanı">
        <h2>Memnuniyet</h2>
        <p className={styles.result}>
          <Stars score={rating.score} />
          <span>
            {rating.score}/5 · {SCORE_LABELS[rating.score]}
          </span>
          <span className={styles.muted}>{formatDateTime(rating.ratedAt)}</span>
        </p>
        {rating.comment && <p className={styles.comment}>{rating.comment}</p>}
      </section>
    );
  }

  if (!ticket.canRate) {
    return null;
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (score !== null) onRate(score, comment.trim() || undefined);
  }

  return (
    <section className={styles.card}>
      <h2>Çözümü değerlendirin</h2>
      <form onSubmit={submit}>
        <fieldset className={styles.scores}>
          <legend>Puanınız</legend>
          {SCORES.map((value) => (
            <label key={value} className={score !== null && value <= score ? styles.selected : styles.option}>
              <input
                type="radio"
                name="rating-score"
                value={value}
                checked={score === value}
                onChange={() => setScore(value)}
                aria-label={`${value} – ${SCORE_LABELS[value]}`}
              />
              <span aria-hidden="true">★</span>
            </label>
          ))}
          {score !== null && <span className={styles.muted}>{SCORE_LABELS[score]}</span>}
        </fieldset>
        <label htmlFor="rating-comment">Yorum (isteğe bağlı)</label>
        <textarea
          id="rating-comment"
          rows={2}
          maxLength={1000}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
        <button type="submit" disabled={pending || score === null}>
          Puanı gönder
        </button>
      </form>
    </section>
  );
}
