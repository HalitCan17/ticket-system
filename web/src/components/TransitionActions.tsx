import { useState, type FormEvent } from 'react';
import type { Role, TicketStatus } from '../api/types.ts';
import styles from './TransitionActions.module.css';

interface ActionSpec {
  label: string;
  /** Label of the required note field, when the backend requires one. */
  noteLabel?: string;
  secondary?: boolean;
}

/** Button texts per transition; which ones are shown comes from the backend's allowedTransitions. */
function actionFor(from: TicketStatus, to: TicketStatus, role: Role): ActionSpec {
  if (to === 'IN_PROGRESS' && from === 'NEW') return { label: 'İşleme al' };
  if (to === 'WAITING_FOR_CUSTOMER') return { label: 'Müşteriden bilgi bekle', secondary: true };
  if (to === 'IN_PROGRESS' && from === 'WAITING_FOR_CUSTOMER') return { label: 'İşleme devam et' };
  if (to === 'RESOLVED') return { label: 'Çözüldü olarak işaretle', noteLabel: 'Çözüm notu' };
  if (to === 'IN_PROGRESS' && from === 'RESOLVED') {
    return role === 'CUSTOMER'
      ? { label: 'Çözüme itiraz et', noteLabel: 'İtiraz gerekçesi', secondary: true }
      : { label: 'Yeniden aç', noteLabel: 'Yeniden açma gerekçesi', secondary: true };
  }
  if (to === 'CLOSED') return { label: role === 'CUSTOMER' ? 'Onayla ve kapat' : 'Kapat' };
  return { label: to };
}

interface Props {
  status: TicketStatus;
  allowed: TicketStatus[];
  role: Role;
  pending: boolean;
  onTransition: (target: TicketStatus, note?: string) => void;
}

export function TransitionActions({ status, allowed, role, pending, onTransition }: Props) {
  const [noteFor, setNoteFor] = useState<TicketStatus | null>(null);
  const [note, setNote] = useState('');

  if (allowed.length === 0) {
    return null;
  }

  const open = noteFor ? actionFor(status, noteFor, role) : null;

  function submitNote(event: FormEvent) {
    event.preventDefault();
    if (noteFor && note.trim()) {
      onTransition(noteFor, note.trim());
    }
  }

  return (
    <div className={styles.actions}>
      <div className={styles.buttons}>
        {allowed.map((target) => {
          const spec = actionFor(status, target, role);
          return (
            <button
              key={target}
              type="button"
              className={spec.secondary ? 'secondary' : undefined}
              disabled={pending}
              onClick={() => {
                if (spec.noteLabel) {
                  setNoteFor(target);
                  setNote('');
                } else {
                  onTransition(target);
                }
              }}
            >
              {spec.label}
            </button>
          );
        })}
      </div>

      {noteFor && open?.noteLabel && (
        <form className={styles.noteForm} onSubmit={submitNote}>
          <label htmlFor="transition-note">{open.noteLabel}</label>
          <textarea id="transition-note" rows={3} value={note} onChange={(e) => setNote(e.target.value)} autoFocus />
          <div className={styles.buttons}>
            <button type="submit" disabled={pending || note.trim() === ''}>
              {open.label}
            </button>
            <button type="button" className="secondary" onClick={() => setNoteFor(null)}>
              Vazgeç
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
