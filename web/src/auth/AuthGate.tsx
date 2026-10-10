import { useEffect, useState, type ReactNode } from 'react';
import { AuthContext, type Auth } from './AuthContext.tsx';
import { initAuth, logoutFromKeycloak, NoApplicationRoleError } from './keycloak.ts';
import styles from './AuthGate.module.css';

type State = { kind: 'loading' } | { kind: 'ready'; auth: Auth } | { kind: 'error'; error: unknown };

/** Renders children only for a signed-in user with an application role. */
export function AuthGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    initAuth().then(
      (auth) => active && setState({ kind: 'ready', auth }),
      (error: unknown) => active && setState({ kind: 'error', error }),
    );
    return () => {
      active = false;
    };
  }, []);

  if (state.kind === 'loading') {
    return <p className={styles.message}>Giriş yapılıyor…</p>;
  }
  if (state.kind === 'error') {
    const noRole = state.error instanceof NoApplicationRoleError ? state.error : null;
    return (
      <div className={styles.message} role="alert">
        <p>{noRole ? noRole.message : 'Kimlik sunucusuna ulaşılamadı. Keycloak çalışıyor mu?'}</p>
        {noRole ? (
          <button type="button" onClick={logoutFromKeycloak}>
            Çıkış yap
          </button>
        ) : (
          <button type="button" onClick={() => window.location.reload()}>
            Tekrar dene
          </button>
        )}
      </div>
    );
  }
  return <AuthContext.Provider value={state.auth}>{children}</AuthContext.Provider>;
}
