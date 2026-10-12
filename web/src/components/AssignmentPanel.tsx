import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTicketsApi } from '../api/ApiContext.tsx';
import type { Role, Ticket } from '../api/types.ts';
import styles from './SidePanel.module.css';

interface Props {
  ticket: Ticket;
  currentUserId: string;
  role: Role;
  pending: boolean;
  onAssign: (assigneeId: string | null) => void;
}

/**
 * Claim / hand over / assign. Which controls appear comes from the backend (canClaim, canReassign);
 * the rules themselves are not repeated here.
 */
export function AssignmentPanel({ ticket, currentUserId, role, pending, onAssign }: Props) {
  const api = useTicketsApi();
  const [target, setTarget] = useState('');
  const staff = useQuery({ queryKey: ['staff'], queryFn: () => api.staff(), enabled: ticket.canReassign });

  if (!ticket.canClaim && !ticket.canReassign) {
    return null;
  }

  const manager = role === 'MANAGER';
  const choices = (staff.data ?? []).filter((member) => member.id !== ticket.assigneeId);

  return (
    <section className={styles.panel} aria-label="Atama">
      <h2>Atama</h2>
      {ticket.canClaim && (
        <button type="button" disabled={pending} onClick={() => onAssign(currentUserId)}>
          Üstlen
        </button>
      )}
      {ticket.canReassign && (
        <>
          <label htmlFor="assign-to">{manager ? 'Atanacak kişi' : 'Devredilecek kişi'}</label>
          <select id="assign-to" value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="">Seçin…</option>
            {choices.map((member) => (
              <option key={member.id} value={member.id}>
                {member.id === currentUserId ? `${member.name} (siz)` : member.name}
                {member.role === 'MANAGER' ? ' · yönetici' : ''}
              </option>
            ))}
          </select>
          <div className={styles.row}>
            <button
              type="button"
              disabled={pending || target === ''}
              onClick={() => {
                onAssign(target);
                setTarget('');
              }}
            >
              {manager ? 'Ata' : 'Devret'}
            </button>
            {ticket.assigneeId && (
              <button type="button" className="secondary" disabled={pending} onClick={() => onAssign(null)}>
                {manager ? 'Atamayı kaldır' : 'Bırak'}
              </button>
            )}
          </div>
          <p className={styles.hint}>Listede yalnızca en az bir kez giriş yapmış personel görünür.</p>
        </>
      )}
    </section>
  );
}
