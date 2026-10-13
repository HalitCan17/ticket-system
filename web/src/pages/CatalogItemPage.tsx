import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { useCatalogApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { CatalogField, CatalogItem } from '../api/types.ts';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import styles from './Form.module.css';
import catalogStyles from './Catalog.module.css';

/** The request form of one catalog item, built from its field definitions. */
export function CatalogItemPage() {
  const api = useCatalogApi();
  const id = Number(useParams().id);
  const valid = Number.isInteger(id) && id > 0;
  const item = useQuery({ queryKey: ['catalog', 'item', id], queryFn: () => api.item(id), enabled: valid });

  if (!valid) return <p>Geçersiz katalog öğesi.</p>;
  if (item.isPending) return <p>Yükleniyor…</p>;
  if (item.isError) {
    return (
      <section>
        <ErrorMessage error={item.error} />
        <Link to="/catalog">Kataloğa dön</Link>
      </section>
    );
  }
  return <RequestForm item={item.data} />;
}

function RequestForm({ item }: { item: CatalogItem }) {
  const api = useCatalogApi();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [answers, setAnswers] = useState<Record<string, string>>({});

  const create = useMutation({
    mutationFn: () => api.create(item.id, answers),
    onSuccess: async (request) => {
      await queryClient.invalidateQueries({ queryKey: ['service-requests'] });
      void navigate(`/requests/${request.id}`);
    },
  });

  const fieldErrors = create.error instanceof ApiError ? create.error.fieldErrors : [];
  const fieldError = (key: string) => fieldErrors.find((e) => e.field === `answers.${key}`)?.message;

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <section className={styles.form}>
      <p>
        <Link to="/catalog">← Hizmet kataloğu</Link>
      </p>
      <h1>{item.name}</h1>
      <p className={catalogStyles.muted}>{item.description}</p>
      {item.requiresApproval && (
        <p className={catalogStyles.muted}>Bu istek bir yöneticinin onayından sonra destek ekibine iletilir.</p>
      )}
      <form onSubmit={submit} aria-label="Hizmet talebi">
        {item.fields.map((field) => (
          <div key={field.key} className={styles.field}>
            <label htmlFor={`answer-${field.key}`}>
              {field.label}
              {field.required ? ' *' : ''}
            </label>
            <FieldInput
              field={field}
              value={answers[field.key] ?? ''}
              onChange={(value) => setAnswers({ ...answers, [field.key]: value })}
            />
            {fieldError(field.key) && <p className={styles.fieldError}>{fieldError(field.key)}</p>}
          </div>
        ))}
        {create.isError && fieldErrors.length === 0 && <ErrorMessage error={create.error} />}
        <div className={styles.actions}>
          <button type="submit" disabled={create.isPending || !item.active}>
            {create.isPending ? 'Gönderiliyor…' : 'İste'}
          </button>
          <button type="button" className="secondary" onClick={() => void navigate('/catalog')}>
            Vazgeç
          </button>
        </div>
      </form>
    </section>
  );
}

function FieldInput({ field, value, onChange }: { field: CatalogField; value: string; onChange: (v: string) => void }) {
  const id = `answer-${field.key}`;
  switch (field.type) {
    case 'TEXTAREA':
      return (
        <textarea id={id} rows={4} maxLength={2000} required={field.required} value={value}
          onChange={(e) => onChange(e.target.value)} />
      );
    case 'NUMBER':
      return (
        <input id={id} type="number" required={field.required} value={value} onChange={(e) => onChange(e.target.value)} />
      );
    case 'SELECT':
      return (
        <select id={id} required={field.required} value={value} onChange={(e) => onChange(e.target.value)}>
          <option value="">Seçin…</option>
          {field.options.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      );
    default:
      return (
        <input id={id} maxLength={200} required={field.required} value={value} onChange={(e) => onChange(e.target.value)} />
      );
  }
}
