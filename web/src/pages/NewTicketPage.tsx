import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { useTicketsApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { CreateTicketRequest, TicketCategory, TicketPriority } from '../api/types.ts';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { CATEGORY_LABELS, CATEGORY_ORDER, PRIORITY_LABELS, PRIORITY_ORDER } from '../labels.ts';
import styles from './Form.module.css';

export function NewTicketPage() {
  const api = useTicketsApi();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<CreateTicketRequest>({
    title: '',
    description: '',
    priority: 'MEDIUM',
    category: 'OTHER',
  });

  const create = useMutation({
    mutationFn: (request: CreateTicketRequest) => api.create(request),
    onSuccess: async (ticket) => {
      await queryClient.invalidateQueries({ queryKey: ['tickets'] });
      void navigate(`/tickets/${ticket.id}`);
    },
  });

  const fieldErrors = create.error instanceof ApiError ? create.error.fieldErrors : [];
  const fieldError = (field: keyof CreateTicketRequest) => fieldErrors.find((e) => e.field === field)?.message;

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate({ ...form, title: form.title.trim(), description: form.description.trim() });
  }

  return (
    <section className={styles.form}>
      <h1>Yeni talep</h1>
      <form onSubmit={submit}>
        <div className={styles.field}>
          <label htmlFor="title">Başlık</label>
          <input
            id="title"
            required
            maxLength={200}
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          {fieldError('title') && <p className={styles.fieldError}>{fieldError('title')}</p>}
        </div>
        <div className={styles.field}>
          <label htmlFor="description">Açıklama</label>
          <textarea
            id="description"
            required
            rows={6}
            maxLength={10_000}
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          {fieldError('description') && <p className={styles.fieldError}>{fieldError('description')}</p>}
        </div>
        <div className={styles.field}>
          <label htmlFor="category">Kategori</label>
          <select
            id="category"
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value as TicketCategory })}
          >
            {CATEGORY_ORDER.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.field}>
          <label htmlFor="priority">Öncelik</label>
          <select
            id="priority"
            value={form.priority}
            onChange={(e) => setForm({ ...form, priority: e.target.value as TicketPriority })}
          >
            {PRIORITY_ORDER.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABELS[p]}
              </option>
            ))}
          </select>
        </div>
        {create.isError && fieldErrors.length === 0 && <ErrorMessage error={create.error} />}
        <div className={styles.actions}>
          <button type="submit" disabled={create.isPending}>
            {create.isPending ? 'Gönderiliyor…' : 'Talep aç'}
          </button>
          <button type="button" className="secondary" onClick={() => void navigate('/tickets')}>
            Vazgeç
          </button>
        </div>
      </form>
    </section>
  );
}
