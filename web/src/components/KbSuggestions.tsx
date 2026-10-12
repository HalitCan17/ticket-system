import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useKbApi } from '../api/ApiContext.tsx';
import styles from '../pages/KbPages.module.css';

/** Typing pauses this long before the suggestions are fetched. */
export const SUGGESTION_DEBOUNCE_MS = 400;
const MIN_LENGTH = 3;

/**
 * Public articles that match the title being typed on the new-ticket form, so a customer may find the answer before
 * opening a ticket. Links open in a new tab and never block the form.
 */
export function KbSuggestions({ title }: { title: string }) {
  const api = useKbApi();
  const [settled, setSettled] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setSettled(title.trim()), SUGGESTION_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [title]);

  const enabled = settled.length >= MIN_LENGTH;
  const suggestions = useQuery({
    queryKey: ['kb', 'suggestions', settled],
    queryFn: () => api.search({ q: settled, visibility: 'PUBLIC', size: 3 }),
    enabled,
    staleTime: 60_000,
  });

  const items = enabled ? (suggestions.data?.items ?? []) : [];
  if (items.length === 0) return null;
  return (
    <aside className={styles.suggestions} aria-label="Önerilen makaleler">
      <h2>Bunlar işinize yarayabilir</h2>
      <ul>
        {items.map((a) => (
          <li key={a.id}>
            <a href={`/kb/${a.id}`} target="_blank" rel="noopener noreferrer">
              {a.title}
            </a>
          </li>
        ))}
      </ul>
    </aside>
  );
}
