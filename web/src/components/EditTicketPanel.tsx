import { useState, type FormEvent } from 'react';
import type { Ticket, TicketCategory, TicketPriority, UpdateTicketRequest } from '../api/types.ts';
import { CATEGORY_LABELS, CATEGORY_ORDER, PRIORITY_LABELS, PRIORITY_ORDER } from '../labels.ts';
import styles from './SidePanel.module.css';

interface Props {
  ticket: Ticket;
  pending: boolean;
  onSave: (request: UpdateTicketRequest) => void;
}

/** Staff change priority (with a reason; the SLA target follows) and category. Shown when ticket.canEdit. */
export function EditTicketPanel({ ticket, pending, onSave }: Props) {
  const [open, setOpen] = useState(false);
  const [priority, setPriority] = useState<TicketPriority>(ticket.priority);
  const [category, setCategory] = useState<TicketCategory>(ticket.category);
  const [reason, setReason] = useState('');

  if (!ticket.canEdit) {
    return null;
  }

  const priorityChanged = priority !== ticket.priority;
  const categoryChanged = category !== ticket.category;
  const missingReason = priorityChanged && reason.trim() === '';

  function start() {
    setPriority(ticket.priority);
    setCategory(ticket.category);
    setReason('');
    setOpen(true);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (missingReason || (!priorityChanged && !categoryChanged)) return;
    onSave({
      priority: priorityChanged ? priority : undefined,
      category: categoryChanged ? category : undefined,
      reason: priorityChanged ? reason.trim() : undefined,
    });
    setOpen(false);
  }

  if (!open) {
    return (
      <button type="button" className="secondary" onClick={start}>
        Önceliği / kategoriyi değiştir
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
      {priorityChanged && (
        <>
          <label htmlFor="edit-reason">Öncelik değişikliğinin gerekçesi</label>
          <textarea id="edit-reason" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
          <p className={styles.hint}>SLA hedefi yeni önceliğe göre hesaplanır; geçen süre korunur.</p>
        </>
      )}
      <div className={styles.row}>
        <button type="submit" disabled={pending || missingReason || (!priorityChanged && !categoryChanged)}>
          Kaydet
        </button>
        <button type="button" className="secondary" onClick={() => setOpen(false)}>
          Vazgeç
        </button>
      </div>
    </form>
  );
}
