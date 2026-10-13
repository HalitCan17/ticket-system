import { useQuery } from '@tanstack/react-query';
import { NavLink } from 'react-router';
import { useCatalogApi } from '../api/ApiContext.tsx';

/** Managers' menu entry with the number of requests waiting for a decision. */
export function ApprovalsLink() {
  const api = useCatalogApi();
  const pending = useQuery({
    queryKey: ['service-requests', 'pending-count'],
    queryFn: () => api.pendingCount(),
    refetchInterval: 30_000,
  });
  const count = pending.data?.count ?? 0;
  return (
    <NavLink to="/approvals" aria-label={count > 0 ? `Onaylar, ${count} bekleyen` : 'Onaylar'}>
      Onaylar{count > 0 ? ` (${count})` : ''}
    </NavLink>
  );
}
