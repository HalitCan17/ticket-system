import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useCatalogApi } from '../api/ApiContext.tsx';
import { useAuth } from '../auth/AuthContext.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { CATEGORY_LABELS } from '../labels.ts';
import styles from './Catalog.module.css';

/** What can be requested; staff also see inactive items (marked). */
export function CatalogPage() {
  const api = useCatalogApi();
  const { user } = useAuth();
  const items = useQuery({ queryKey: ['catalog', 'items'], queryFn: () => api.items() });

  return (
    <section className={styles.page}>
      <div className={styles.toolbar}>
        <h1>Hizmet kataloğu</h1>
        {user.role === 'MANAGER' && (
          <Link to="/catalog/manage" className={styles.toolbarLink}>
            Kataloğu yönet
          </Link>
        )}
      </div>
      <p className={styles.muted}>
        Bir arızayı bildirmek için <Link to="/tickets/new">talep açın</Link>; yeni bir şeye ihtiyacınız varsa buradan
        isteyin.
      </p>
      {items.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {items.isError && <ErrorMessage error={items.error} />}
      {items.data && items.data.length === 0 && <p className={styles.muted}>Katalogda öğe yok.</p>}
      {items.data && items.data.length > 0 && (
        <ul className={styles.grid} aria-label="Katalog öğeleri">
          {items.data.map((item) => (
            <li key={item.id} className={styles.card}>
              <h2>
                <Link to={`/catalog/${item.id}`}>{item.name}</Link>
              </h2>
              <p className={styles.muted}>{item.description}</p>
              <div className={styles.meta}>
                <span>{CATEGORY_LABELS[item.category]}</span>
                {item.requiresApproval && <span className={styles.badge}>Onay gerekir</span>}
                {!item.active && <span className={styles.badge}>Pasif</span>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
