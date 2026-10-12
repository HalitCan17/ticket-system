import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useGroupsApi, useTicketsApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { AssignmentMode, GroupRequest, SupportGroup, TicketCategory } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { CATEGORY_LABELS, CATEGORY_ORDER } from '../labels.ts';
import styles from './GroupsPage.module.css';

export const MODE_LABELS: Record<AssignmentMode, string> = {
  NONE: 'Otomatik atama yok (grup kuyruğunda bekler)',
  ROUND_ROBIN: 'Üyelere sırayla ata',
  LEAST_LOADED: 'En az açık işi olana ata',
};

const MODES: AssignmentMode[] = ['NONE', 'ROUND_ROBIN', 'LEAST_LOADED'];

/** Managers set up support groups, their members and which category goes where (docs/adr/0014). */
export function GroupsPage() {
  const { user } = useAuth();
  if (user.role !== 'MANAGER') {
    return (
      <section>
        <h1>Destek grupları</h1>
        <p>Bu sayfa yalnızca yöneticiler içindir.</p>
      </section>
    );
  }
  return <GroupsEditor />;
}

function GroupsEditor() {
  const api = useGroupsApi();
  const queryClient = useQueryClient();
  const groups = useQuery({ queryKey: ['groups'], queryFn: () => api.list() });
  const [creating, setCreating] = useState(false);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['groups'] });

  const create = useMutation({
    mutationFn: (request: GroupRequest) => api.create(request),
    onSuccess: () => {
      setCreating(false);
      void refresh();
    },
  });

  return (
    <section className={styles.page}>
      <div className={styles.toolbar}>
        <h1>Destek grupları</h1>
        {!creating && (
          <button type="button" onClick={() => setCreating(true)}>
            + Yeni grup
          </button>
        )}
      </div>
      <p className={styles.muted}>
        Talep açılınca kategorisine bağlı gruba düşer. Grubun atama ayarı açıksa talep üyelerden birine otomatik atanır;
        üyesi olmayan gruptaki talepler kuyrukta bekler.
      </p>

      {creating && (
        <GroupForm
          label="Yeni grup"
          initial={{ name: '', description: '', assignmentMode: 'NONE' }}
          pending={create.isPending}
          error={create.error}
          onCancel={() => setCreating(false)}
          onSave={(request) => create.mutate(request)}
        />
      )}

      {groups.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {groups.isError && <ErrorMessage error={groups.error} />}
      {groups.data && (
        <>
          <RoutingTable groups={groups.data} onSaved={refresh} />
          <h2>Gruplar</h2>
          {groups.data.length === 0 && <p className={styles.muted}>Henüz grup yok.</p>}
          <ul className={styles.list}>
            {groups.data.map((group) => (
              <GroupCard key={group.id} group={group} onChanged={refresh} />
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

function RoutingTable({ groups, onSaved }: { groups: SupportGroup[]; onSaved: () => void }) {
  const api = useGroupsApi();
  const current: Partial<Record<TicketCategory, number>> = {};
  groups.forEach((g) => g.categories.forEach((c) => (current[c] = g.id)));
  const [routes, setRoutes] = useState<Partial<Record<TicketCategory, number | null>> | null>(null);
  const shown = routes ?? current;
  const save = useMutation({
    mutationFn: () => {
      const body: Partial<Record<TicketCategory, number | null>> = {};
      CATEGORY_ORDER.forEach((c) => (body[c] = shown[c] ?? null));
      return api.setRoutes(body);
    },
    onSuccess: () => {
      setRoutes(null);
      onSaved();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate();
  }

  return (
    <form className={styles.card} onSubmit={submit} aria-label="Yönlendirme">
      <h2>Yönlendirme</h2>
      <p className={styles.muted}>Her kategori en fazla bir gruba gider. Değişiklik yalnızca yeni ve kategorisi değişen talepleri etkiler.</p>
      <div className={styles.routes}>
        {CATEGORY_ORDER.map((category) => (
          <label key={category} className={styles.route}>
            <span>{CATEGORY_LABELS[category]}</span>
            <select
              aria-label={`${CATEGORY_LABELS[category]} grubu`}
              value={shown[category] ?? ''}
              onChange={(e) =>
                setRoutes({ ...shown, [category]: e.target.value === '' ? null : Number(e.target.value) })
              }
            >
              <option value="">Grup yok</option>
              {groups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </select>
          </label>
        ))}
      </div>
      {save.isError && <ErrorMessage error={save.error} />}
      <div className={styles.actions}>
        <button type="submit" disabled={routes === null || save.isPending}>
          Yönlendirmeyi kaydet
        </button>
      </div>
    </form>
  );
}

function GroupCard({ group, onChanged }: { group: SupportGroup; onChanged: () => void }) {
  const api = useGroupsApi();
  const [mode, setMode] = useState<'view' | 'edit' | 'members'>('view');

  const update = useMutation({
    mutationFn: (request: GroupRequest) => api.update(group.id, request),
    onSuccess: () => {
      setMode('view');
      onChanged();
    },
  });
  const remove = useMutation({ mutationFn: () => api.remove(group.id), onSuccess: onChanged });

  function confirmDelete() {
    if (window.confirm(`"${group.name}" silinsin mi? Talepleri grupsuz kalır, atamaları değişmez.`)) {
      remove.mutate();
    }
  }

  return (
    <li className={styles.card} aria-label={group.name}>
      <div className={styles.head}>
        <h3>{group.name}</h3>
        <div className={styles.headActions}>
          <button type="button" className="secondary" onClick={() => setMode('edit')}>
            Düzenle
          </button>
          <button type="button" className="secondary" onClick={() => setMode('members')}>
            Üyeler
          </button>
          <button
            type="button"
            className="secondary"
            aria-label={`${group.name} sil`}
            disabled={remove.isPending}
            onClick={confirmDelete}
          >
            Sil
          </button>
        </div>
      </div>
      {group.description && <p className={styles.muted}>{group.description}</p>}
      <dl className={styles.facts}>
        <dt>Atama</dt>
        <dd>{MODE_LABELS[group.assignmentMode]}</dd>
        <dt>Kategoriler</dt>
        <dd>{group.categories.length === 0 ? '—' : group.categories.map((c) => CATEGORY_LABELS[c]).join(', ')}</dd>
        <dt>Üyeler</dt>
        <dd>{group.members.length === 0 ? 'Üye yok' : group.members.map((m) => m.name).join(', ')}</dd>
      </dl>
      {remove.isError && <ErrorMessage error={remove.error} />}

      {mode === 'edit' && (
        <GroupForm
          label={`${group.name} düzenle`}
          initial={{ name: group.name, description: group.description ?? '', assignmentMode: group.assignmentMode }}
          pending={update.isPending}
          error={update.error}
          onCancel={() => setMode('view')}
          onSave={(request) => update.mutate(request)}
        />
      )}
      {mode === 'members' && (
        <MembersForm
          group={group}
          onDone={() => {
            setMode('view');
            onChanged();
          }}
          onCancel={() => setMode('view')}
        />
      )}
    </li>
  );
}

interface GroupFormProps {
  label: string;
  initial: GroupRequest;
  pending: boolean;
  error: unknown;
  onSave: (request: GroupRequest) => void;
  onCancel: () => void;
}

function GroupForm({ label, initial, pending, error, onSave, onCancel }: GroupFormProps) {
  const [form, setForm] = useState<GroupRequest>(initial);
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : [];

  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({ ...form, name: form.name.trim(), description: form.description?.trim() || undefined });
  }

  return (
    <form className={styles.form} onSubmit={submit} aria-label={label}>
      <label htmlFor={`${label}-name`}>Ad</label>
      <input
        id={`${label}-name`}
        required
        maxLength={80}
        value={form.name}
        onChange={(e) => setForm({ ...form, name: e.target.value })}
      />
      <label htmlFor={`${label}-description`}>Açıklama</label>
      <input
        id={`${label}-description`}
        maxLength={1000}
        value={form.description ?? ''}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
      />
      <fieldset className={styles.modes}>
        <legend>Atama</legend>
        {MODES.map((m) => (
          <label key={m}>
            <input
              type="radio"
              name={`${label}-mode`}
              value={m}
              checked={form.assignmentMode === m}
              onChange={() => setForm({ ...form, assignmentMode: m })}
            />
            {MODE_LABELS[m]}
          </label>
        ))}
      </fieldset>
      {error !== null && fieldErrors.length === 0 && <ErrorMessage error={error} />}
      {fieldErrors.map((e) => (
        <p key={e.field} className={styles.fieldError}>
          {e.message}
        </p>
      ))}
      <div className={styles.actions}>
        <button type="submit" disabled={pending}>
          Kaydet
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Vazgeç
        </button>
      </div>
    </form>
  );
}

function MembersForm({ group, onDone, onCancel }: { group: SupportGroup; onDone: () => void; onCancel: () => void }) {
  const groupsApi = useGroupsApi();
  const ticketsApi = useTicketsApi();
  const staff = useQuery({ queryKey: ['staff'], queryFn: () => ticketsApi.staff() });
  const [selected, setSelected] = useState<Set<string>>(new Set(group.members.map((m) => m.id)));
  const save = useMutation({ mutationFn: () => groupsApi.setMembers(group.id, [...selected]), onSuccess: onDone });

  function toggle(id: string) {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelected(next);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate();
  }

  return (
    <form className={styles.form} onSubmit={submit} aria-label={`${group.name} üyeleri`}>
      {staff.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {staff.isError && <ErrorMessage error={staff.error} />}
      {staff.data && (
        <fieldset className={styles.members}>
          <legend>Üyeler</legend>
          {staff.data.map((member) => (
            <label key={member.id}>
              <input type="checkbox" checked={selected.has(member.id)} onChange={() => toggle(member.id)} />
              {member.name}
              {member.role === 'MANAGER' ? ' · yönetici' : ''}
            </label>
          ))}
        </fieldset>
      )}
      <p className={styles.muted}>Listede yalnızca en az bir kez giriş yapmış personel görünür.</p>
      {save.isError && <ErrorMessage error={save.error} />}
      <div className={styles.actions}>
        <button type="submit" disabled={save.isPending || !staff.data}>
          Üyeleri kaydet
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Vazgeç
        </button>
      </div>
    </form>
  );
}
