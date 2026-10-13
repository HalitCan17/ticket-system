import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCatalogApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { CatalogField, CatalogFieldType, CatalogItem, CatalogItemRequest, TicketCategory, TicketPriority } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { CATEGORY_LABELS, CATEGORY_ORDER, FIELD_TYPE_LABELS, PRIORITY_LABELS, PRIORITY_ORDER } from '../labels.ts';
import formStyles from './Form.module.css';
import styles from './Catalog.module.css';

const FIELD_TYPES: CatalogFieldType[] = ['TEXT', 'TEXTAREA', 'NUMBER', 'SELECT'];

const EMPTY: CatalogItemRequest = {
  name: '',
  description: '',
  category: 'OTHER',
  priority: 'MEDIUM',
  requiresApproval: false,
  active: true,
  fields: [],
};

/** A camelCase key from a label: "Kullanıcı sayısı" -> "kullaniciSayisi". */
export function keyFromLabel(label: string, taken: string[]): string {
  const ascii = label
    .toLocaleLowerCase('tr-TR')
    .replace(/ı/g, 'i')
    .normalize('NFD')
    .replace(/[^a-z0-9 ]/g, '')
    .trim();
  const words = ascii.split(/\s+/).filter(Boolean);
  let key = words.map((w, i) => (i === 0 ? w : w.charAt(0).toUpperCase() + w.slice(1))).join('') || 'alan';
  if (!/^[a-z]/.test(key)) key = `alan${key}`;
  key = key.slice(0, 36);
  let candidate = key;
  for (let n = 2; taken.includes(candidate); n++) candidate = `${key}${n}`;
  return candidate;
}

export function CatalogManagePage() {
  const { user } = useAuth();
  if (user.role !== 'MANAGER') {
    return (
      <section>
        <h1>Kataloğu yönet</h1>
        <p>Bu sayfa yalnızca yöneticiler içindir.</p>
      </section>
    );
  }
  return <Manage />;
}

function Manage() {
  const api = useCatalogApi();
  const items = useQuery({ queryKey: ['catalog', 'items'], queryFn: () => api.items() });
  const [editing, setEditing] = useState<CatalogItem | 'new' | null>(null);

  return (
    <section className={styles.page}>
      <div className={styles.toolbar}>
        <h1>Kataloğu yönet</h1>
        {editing === null && (
          <button type="button" className={styles.toolbarLink} onClick={() => setEditing('new')}>
            + Yeni öğe
          </button>
        )}
      </div>
      {editing !== null && (
        <ItemEditor
          key={editing === 'new' ? 'new' : editing.id}
          item={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
        />
      )}
      {items.isError && <ErrorMessage error={items.error} />}
      <ul className={styles.list} aria-label="Katalog öğeleri">
        {items.data?.map((item) => (
          <li key={item.id} className={styles.row}>
            <strong>{item.name}</strong>
            <span className={styles.muted}>
              {CATEGORY_LABELS[item.category]} · {item.fields.length} alan
              {item.requiresApproval ? ' · onaylı' : ''}
            </span>
            {!item.active && <span className={styles.badge}>Pasif</span>}
            <button type="button" className="secondary" aria-label={`${item.name} düzenle`} onClick={() => setEditing(item)}>
              Düzenle
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ItemEditor({ item, onClose }: { item?: CatalogItem; onClose: () => void }) {
  const api = useCatalogApi();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<CatalogItemRequest>(
    item
      ? {
          name: item.name,
          description: item.description,
          category: item.category,
          priority: item.priority,
          requiresApproval: item.requiresApproval,
          active: item.active,
          fields: item.fields,
        }
      : EMPTY,
  );

  const save = useMutation({
    mutationFn: (request: CatalogItemRequest) => (item ? api.updateItem(item.id, request) : api.createItem(request)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['catalog'] });
      onClose();
    },
  });
  const errors = save.error instanceof ApiError ? save.error.fieldErrors : [];

  function setField(index: number, next: Partial<CatalogField>) {
    setForm({ ...form, fields: form.fields.map((f, i) => (i === index ? { ...f, ...next } : f)) });
  }

  function addField() {
    const key = keyFromLabel('Yeni alan', form.fields.map((f) => f.key));
    setForm({ ...form, fields: [...form.fields, { key, label: '', type: 'TEXT', options: [], required: false }] });
  }

  function move(index: number, by: number) {
    const fields = [...form.fields];
    const [moved] = fields.splice(index, 1);
    if (moved) fields.splice(index + by, 0, moved);
    setForm({ ...form, fields });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const taken: string[] = [];
    const fields = form.fields.map((f) => {
      // New fields get their key from the label; existing keys stay so stored answers keep their meaning.
      const key = f.key.startsWith('yeniAlan') ? keyFromLabel(f.label, taken) : f.key;
      taken.push(key);
      // Empty lines stay while typing (so Enter works) and are dropped here.
      const options = f.type === 'SELECT' ? f.options.map((o) => o.trim()).filter(Boolean) : [];
      return { ...f, key, label: f.label.trim(), options };
    });
    save.mutate({ ...form, name: form.name.trim(), description: form.description.trim(), fields });
  }

  return (
    <form className={styles.panel} onSubmit={submit} aria-label={item ? 'Öğeyi düzenle' : 'Yeni öğe'}>
      <h2>{item ? item.name : 'Yeni öğe'}</h2>
      <div className={formStyles.field}>
        <label htmlFor="item-name">Ad</label>
        <input id="item-name" required maxLength={120} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </div>
      <div className={formStyles.field}>
        <label htmlFor="item-description">Açıklama</label>
        <textarea id="item-description" required rows={3} maxLength={2000} value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </div>
      <div className={styles.actions}>
        <label>
          Kategori{' '}
          <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value as TicketCategory })}>
            {CATEGORY_ORDER.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Öncelik{' '}
          <select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value as TicketPriority })}>
            {PRIORITY_ORDER.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABELS[p]}
              </option>
            ))}
          </select>
        </label>
        <label>
          <input type="checkbox" checked={form.requiresApproval}
            onChange={(e) => setForm({ ...form, requiresApproval: e.target.checked })} />{' '}
          Yönetici onayı gerekir
        </label>
        <label>
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Aktif
        </label>
      </div>

      <h2>Form alanları</h2>
      {form.fields.length === 0 && <p className={styles.muted}>Alan yok; istek tek tıkla gönderilir.</p>}
      {form.fields.map((f, i) => (
        <fieldset key={i} className={styles.fieldRow} aria-label={`Alan ${i + 1}`}>
          <label>
            Etiket
            <input required maxLength={100} value={f.label} onChange={(e) => setField(i, { label: e.target.value })} />
          </label>
          <label>
            Tür
            <select value={f.type} onChange={(e) => setField(i, { type: e.target.value as CatalogFieldType })}>
              {FIELD_TYPES.map((t) => (
                <option key={t} value={t}>
                  {FIELD_TYPE_LABELS[t]}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>
              <input type="checkbox" checked={f.required} onChange={(e) => setField(i, { required: e.target.checked })} />{' '}
              Zorunlu
            </span>
          </label>
          <span className={styles.actions}>
            <button type="button" className="secondary" aria-label={`Alan ${i + 1} yukarı`} disabled={i === 0}
              onClick={() => move(i, -1)}>
              ↑
            </button>
            <button type="button" className="secondary" aria-label={`Alan ${i + 1} aşağı`}
              disabled={i === form.fields.length - 1} onClick={() => move(i, 1)}>
              ↓
            </button>
          </span>
          <button type="button" className="secondary" aria-label={`Alan ${i + 1} sil`}
            onClick={() => setForm({ ...form, fields: form.fields.filter((_, j) => j !== i) })}>
            Sil
          </button>
          {f.type === 'SELECT' && (
            <label className={styles.fieldOptions}>
              Seçenekler (her satıra bir tane)
              <textarea rows={3} value={f.options.join('\n')}
                onChange={(e) => setField(i, { options: e.target.value.split('\n') })} />
            </label>
          )}
        </fieldset>
      ))}
      <div className={styles.actions}>
        <button type="button" className="secondary" onClick={addField}>
          + Alan ekle
        </button>
      </div>

      {errors.map((e) => (
        <p key={e.field} className={formStyles.fieldError}>
          {e.field}: {e.message}
        </p>
      ))}
      {save.isError && errors.length === 0 && <ErrorMessage error={save.error} />}
      <div className={styles.actions}>
        <button type="submit" disabled={save.isPending}>
          Kaydet
        </button>
        <button type="button" className="secondary" onClick={onClose}>
          Vazgeç
        </button>
      </div>
    </form>
  );
}
