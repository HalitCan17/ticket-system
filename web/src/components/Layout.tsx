import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext.tsx';
import { ROLE_LABELS } from '../auth/roles.ts';
import styles from './Layout.module.css';

export function Layout() {
  const { user, logout } = useAuth();

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <NavLink to="/tickets" className={styles.brand}>
          ITSM
        </NavLink>
        <nav className={styles.nav}>
          <NavLink to="/tickets" end>
            Talepler
          </NavLink>
          <NavLink to="/tickets/new">Yeni talep</NavLink>
        </nav>
        <div className={styles.user}>
          <span>
            {user.displayName} <span className={styles.role}>· {ROLE_LABELS[user.role]}</span>
          </span>
          <button type="button" className="secondary" onClick={logout}>
            Çıkış
          </button>
        </div>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
