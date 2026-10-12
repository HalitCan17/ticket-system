import { useState, type FormEvent } from 'react';
import type { SupportGroup, Ticket, TicketCategory, TicketPriority, UpdateTicketRequest } from '../api/types.ts';
import { CATEGORY_LABELS, CATEGORY_ORDER, PRIORITY_LABELS, PRIORITY_ORDER } from '../labels.ts';
import styles from './SidePanel.module.css';

interface Props {
  ticket: Ticket;
  /** Groups to move the ticket to; the select is hidden without them. */
  groups?: SupportGroup[];
  pending: boolean;
  onSave: (request: UpdateTicketRequest) => void;
}

/** Staff change priority (with a reason; the SLA target follows) and category. Shown when ticket.canEdit. */
export function EditTicketPanel({ ticket, groups = [], pending, onSave }: Props) {
  const [open, setOpen] = useState(false);
  const [priority, setPriority] = useState<TicketPriority>(ticket.priority);
  const [category, setCategory] = useState<TicketCategory>(ticket.category);
  const [reason, setReason] = useState('');
  /** '' = no group */
  const [group, setGroup] = useState<string>(ticket.groupId === null ? '' : String(ticket.groupId));

  if (!ticket.canEdit) {
    return null;
  }

  const priorityChanged = priority !== ticket.priority;
  const categoryChanged = category !== ticket.category;
  const groupChanged = group !== (ticket.groupId === null ? '' : String(ticket.groupId));
  const changed = priorityChanged || categoryChanged || groupChanged;
  const missingReason = priorityChanged && reason.trim() === '';

  function start() {
    setPriority(ticket.priority);
    setCategory(ticket.category);
    setReason('');
    setGroup(ticket.groupId === null ? '' : String(ticket.groupId));
    setOpen(true);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (missingReason || !changed) return;
    onSave({
      priority: priorityChanged ? priority : undefined,
      category: categoryChanged ? category : undefined,
      reason: priorityChanged ? reason.trim() : undefined,
      groupId: groupChanged && group !== '' ? Number(group) : undefined,
      removeGroup: groupChanged && group === '' ? true : undefined,
    });
    setOpen(false);
  }

  if (!open) {
    return (
      <button type="button" className="secondary" onClick={start}>
        Önceliği / kategoriyi / grubu değiştir
      </button>
    );
  }

  return (
    <form className={styles.panel} onSubmit={submit} aria-label="Talebi düzenle">
      <h2>Talebi düzenle</h2>
      <label htmlFor="edit-priority">Öncelik</label>
      <select
        id="edit-priority"
        value={priority}
        disabled={ticket.status === 'RESOLVED'}
        onChange={(e) => setPriority(e.target.value as TicketPriority)}
      >
        {PRIORITY_ORDER.map((p) => (
          <option key={p} value={p}>
            {PRIORITY_LABELS[p]}
          </option>
        ))}
      </select>
      {ticket.status === 'RESOLVED' && <p className={styles.hint}>Çözülmüş talebin önceliği değişmez.</p>}
      <label htmlFor="edit-category">Kategori</label>
      <select id="edit-category" value={category} onChange={(e) => setCategory(e.target.value as TicketCategory)}>
        {CATEGORY_ORDER.map((c) => (
          <option key={c} value={c}>
            {CATEGORY_LABELS[c]}
          </option>
        ))}
      </select>
      {groups.length > 0 && (
        <>
          <label htmlFor="edit-group">Destek grubu</label>
          <select id="edit-group" value={group} onChange={(e) => setGroup(e.target.value)}>
            <option value="">Grup yok</option>
            {groups.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name}
              </option>
            ))}
          </select>
          {categoryChanged && !groupChanged && (
            <p className={styles.hint}>Kategori değişince talep o kategorinin grubuna geçer.</p>
          )}
        </>
      )}
      {priorityChanged && (
        <>
          <label htmlFor="edit-reason">Öncelik değişikliğinin gerekçesi</label>
          <textarea id="edit-reason" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
          <p className={styles.hint}>SLA hedefi yeni önceliğe göre hesaplanır; geçen süre korunur.</p>
        </>
      )}
      <div className={styles.row}>
        <button type="submit" disabled={pending || missingReason || !changed}>
          Kaydet
        </button>
        <button type="button" className="secondary" onClick={() => setOpen(false)}>
          Vazgeç
        </button>
      </div>
    </form>
  );
}
