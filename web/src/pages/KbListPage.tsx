import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useKbApi } from '../api/ApiContext.tsx';
import type { KbVisibility, TicketCategory } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { isStaff } from '../auth/roles.ts';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { CATEGORY_LABELS, CATEGORY_ORDER, KB_VISIBILITY_LABELS, KB_VISIBILITY_ORDER } from '../labels.ts';
import styles from './KbPages.module.css';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

export function KbListPage() {
  const api = useKbApi();
  const { user } = useAuth();
  const staff = isStaff(user.role);
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get('q') ?? '';
  const category = CATEGORY_ORDER.find((c) => c === searchParams.get('category'));
  const visibility = staff ? KB_VISIBILITY_ORDER.find((v) => v === searchParams.get('visibility')) : undefined;
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0);
  const [text, setText] = useState(q);

  const articles = useQuery({
    queryKey: ['kb', 'list', { q, category, visibility, page }],
    queryFn: () => api.search({ q: q || undefined, category, visibility, page, size: PAGE_SIZE }),
  });

  function update(next: { q?: string; category?: TicketCategory | undefined; visibility?: KbVisibility | undefined; page?: number }) {
    const merged = { q, category, visibility, page: 0, ...next };
    const params = new URLSearchParams();
    if (merged.q) params.set('q', merged.q);
    if (merged.category) params.set('category', merged.category);
    if (merged.visibility) params.set('visibility', merged.visibility);
    if (merged.page) params.set('page', String(merged.page));
    setSearchParams(params);
  }

  useEffect(() => {
    if (text.trim() === q) return;
    const timer = setTimeout(() => update({ q: text.trim() }), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // Only typing triggers a search; the other filters update the URL directly.
  }, [text]);

  const filtered = q !== '' || category !== undefined || visibility !== undefined;

  return (
    <section className={styles.page}>
      <div className={styles.toolbar}>
        <h1>Bilgi bankası</h1>
        {staff && (
          <Link to="/kb/new" className={styles.newLink}>
            + Yeni makale
          </Link>
        )}
      </div>

      <div className={styles.filters}>
        <label>
          <span>Ara</span>
          <input
            type="search"
            placeholder="Başlık ya da içerik"
            value={text}
            maxLength={200}
            onChange={(event) => setText(event.target.value)}
          />
        </label>
        <label>
          <span>Kategori</span>
          <select
            value={category ?? ''}
            onChange={(event) => update({ category: CATEGORY_ORDER.find((c) => c === event.target.value) })}
          >
            <option value="">Tümü</option>
            {CATEGORY_ORDER.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </select>
        </label>
        {staff && (
          <label>
            <span>Görünürlük</span>
            <select
              value={visibility ?? ''}
              onChange={(event) => update({ visibility: KB_VISIBILITY_ORDER.find((v) => v === event.target.value) })}
            >
              <option value="">Tümü</option>
              {KB_VISIBILITY_ORDER.map((v) => (
                <option key={v} value={v}>
                  {KB_VISIBILITY_LABELS[v]}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {articles.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {articles.isError && <ErrorMessage error={articles.error} />}
      {articles.data && articles.data.items.length === 0 && (
        <p className={styles.muted}>{filtered ? 'Aramaya uyan makale yok.' : 'Henüz makale yok.'}</p>
      )}
      {articles.data && articles.data.items.length > 0 && (
        <>
          <ul className={styles.list} aria-label="Makaleler">
            {articles.data.items.map((a) => (
              <li key={a.id} className={styles.item}>
                <div className={styles.meta}>
                  <span>{CATEGORY_LABELS[a.category]}</span>
                  {a.visibility !== 'PUBLIC' && <span className={styles.badge}>{KB_VISIBILITY_LABELS[a.visibility]}</span>}
                </div>
                <h2>
                  <Link to={`/kb/${a.id}`}>{a.title}</Link>
                </h2>
                <p className={styles.muted}>{a.excerpt}</p>
              </li>
            ))}
          </ul>
          <nav className={styles.pager} aria-label="Sayfalama">
            <button
              type="button"
              className="secondary"
              disabled={page === 0}
              onClick={() => update({ page: page - 1 })}
            >
              Önceki
            </button>
            <span>
              Sayfa {articles.data.page + 1} / {Math.max(1, articles.data.totalPages)} · {articles.data.totalElements} makale
            </span>
            <button
              type="button"
              className="secondary"
              disabled={page + 1 >= articles.data.totalPages}
              onClick={() => update({ page: page + 1 })}
            >
              Sonraki
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
