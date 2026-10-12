import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSlaCalendarApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import type { Holiday, SlaCalendar, SlaCalendarUpdate, WeekDay } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { PRIORITY_LABELS, PRIORITY_ORDER } from '../labels.ts';
import styles from './SlaSettingsPage.module.css';

const DAYS: { value: WeekDay; label: string }[] = [
  { value: 'MONDAY', label: 'Pazartesi' },
  { value: 'TUESDAY', label: 'Salı' },
  { value: 'WEDNESDAY', label: 'Çarşamba' },
  { value: 'THURSDAY', label: 'Perşembe' },
  { value: 'FRIDAY', label: 'Cuma' },
  { value: 'SATURDAY', label: 'Cumartesi' },
  { value: 'SUNDAY', label: 'Pazar' },
];

const dateFormat = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', month: 'long', year: 'numeric', weekday: 'long' });

/** "30 dk", "4 saat", "1 saat 30 dk": SLA targets in working time, so "gün" would be ambiguous. */
export function targetText(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} dk`;
  return rest === 0 ? `${hours} saat` : `${hours} saat ${rest} dk`;
}

function holidayLabel(holiday: Holiday): string {
  return dateFormat.format(new Date(`${holiday.date}T12:00:00`));
}

/** Managers set the business hours the SLA clocks run in (docs/adr/0015-sla-extensions.md). */
export function SlaSettingsPage() {
  const { user } = useAuth();
  if (user.role !== 'MANAGER') {
    return (
      <section>
        <h1>SLA ayarları</h1>
        <p>Bu sayfa yalnızca yöneticiler içindir.</p>
      </section>
    );
  }
  return <SlaSettings />;
}

function SlaSettings() {
  const api = useSlaCalendarApi();
  const calendar = useQuery({ queryKey: ['sla-calendar'], queryFn: () => api.get() });
  // Kept here: saving refreshes the settings, which rebuilds the editor below and would drop the message.
  const [saved, setSaved] = useState<SlaCalendarUpdate | null>(null);

  return (
    <section className={styles.page}>
      <h1>SLA ayarları</h1>
      <p className={styles.muted}>
        Mesai saatleri açıkken ilk yanıt ve çözüm süreleri yalnızca çalışma günlerinin çalışma saatlerinde ilerler;
        hafta sonu ve tatiller sayılmaz. Kapalıyken süreler günün her saatinde işler.
      </p>
      {calendar.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {calendar.isError && <ErrorMessage error={calendar.error} />}
      {saved && (
        <p className={styles.notice} role="status">
          Kaydedildi.{' '}
          {saved.recalculatedTickets > 0
            ? `${saved.recalculatedTickets} açık talebin süreleri yeniden hesaplandı.`
            : 'Yeniden hesaplanacak açık talep yoktu.'}
        </p>
      )}
      {calendar.data && (
        <CalendarEditor key={JSON.stringify(calendar.data)} calendar={calendar.data} onSaved={setSaved} />
      )}
    </section>
  );
}

function CalendarEditor({ calendar, onSaved }: { calendar: SlaCalendar; onSaved: (result: SlaCalendarUpdate) => void }) {
  const api = useSlaCalendarApi();
  const queryClient = useQueryClient();
  const [enabled, setEnabled] = useState(calendar.enabled);
  const [days, setDays] = useState<Set<WeekDay>>(new Set(calendar.workDays));
  const [start, setStart] = useState(calendar.start);
  const [end, setEnd] = useState(calendar.end);
  const [holidays, setHolidays] = useState<Holiday[]>(calendar.holidays);
  const [newDate, setNewDate] = useState('');
  const [newName, setNewName] = useState('');
  const [holidayProblem, setHolidayProblem] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () =>
      api.save({
        enabled,
        zoneId: calendar.zoneId,
        workDays: DAYS.map((d) => d.value).filter((d) => days.has(d)),
        start,
        end,
        holidays,
      }),
    onSuccess: (result) => {
      onSaved(result);
      void queryClient.invalidateQueries({ queryKey: ['sla-calendar'] });
    },
  });

  const hoursProblem =
    days.size === 0 ? 'En az bir çalışma günü seçin.' : start >= end ? 'Mesai bitişi başlangıçtan sonra olmalı.' : null;

  function toggleDay(day: WeekDay) {
    const next = new Set(days);
    if (next.has(day)) next.delete(day);
    else next.add(day);
    setDays(next);
  }

  function addHoliday() {
    if (!newDate || !newName.trim()) return;
    if (holidays.some((h) => h.date === newDate)) {
      setHolidayProblem('Bu tarih zaten listede.');
      return;
    }
    setHolidayProblem(null);
    setHolidays([...holidays, { date: newDate, name: newName.trim() }].sort((a, b) => a.date.localeCompare(b.date)));
    setNewDate('');
    setNewName('');
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!hoursProblem) save.mutate();
  }

  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors : [];

  return (
    <>
      <form className={styles.card} onSubmit={submit} aria-label="Mesai saatleri">
        <label className={styles.toggle}>
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          Mesai saatlerini kullan
        </label>

        <fieldset className={styles.fieldset} disabled={!enabled}>
          <legend>Çalışma günleri</legend>
          <div className={styles.days}>
            {DAYS.map((d) => (
              <label key={d.value}>
                <input type="checkbox" checked={days.has(d.value)} onChange={() => toggleDay(d.value)} />
                {d.label}
              </label>
            ))}
          </div>
        </fieldset>

        <div className={styles.times}>
          <label>
            Başlangıç
            <input type="time" value={start} disabled={!enabled} onChange={(e) => setStart(e.target.value)} />
          </label>
          <label>
            Bitiş
            <input type="time" value={end} disabled={!enabled} onChange={(e) => setEnd(e.target.value)} />
          </label>
          <span className={styles.muted}>Saat dilimi: {calendar.zoneId}</span>
        </div>
        {enabled && hoursProblem && <p className={styles.problem}>{hoursProblem}</p>}

        <h2>Resmî tatiller</h2>
        <p className={styles.muted}>
          Sabit tarihli resmî tatiller hazır gelir. Her yıl değişen dini bayramları kendiniz eklemelisiniz.
        </p>
        <ul className={styles.holidays} aria-label="Tatiller">
          {holidays.length === 0 && <li className={styles.muted}>Tatil yok.</li>}
          {holidays.map((h) => (
            <li key={h.date}>
              <span>
                <strong>{h.name}</strong> · {holidayLabel(h)}
              </span>
              <button
                type="button"
                className="secondary"
                aria-label={`${h.name} tatilini kaldır`}
                onClick={() => setHolidays(holidays.filter((x) => x.date !== h.date))}
              >
                Kaldır
              </button>
            </li>
          ))}
        </ul>
        <div className={styles.addHoliday}>
          <label>
            Tarih
            <input type="date" value={newDate} onChange={(e) => setNewDate(e.target.value)} />
          </label>
          <label>
            Ad
            <input
              value={newName}
              maxLength={100}
              placeholder="Örn. Ramazan Bayramı 1. gün"
              onChange={(e) => setNewName(e.target.value)}
            />
          </label>
          <button type="button" className="secondary" disabled={!newDate || !newName.trim()} onClick={addHoliday}>
            Tatil ekle
          </button>
        </div>
        {holidayProblem && <p className={styles.problem}>{holidayProblem}</p>}

        {save.isError && fieldErrors.length === 0 && <ErrorMessage error={save.error} />}
        {fieldErrors.map((e) => (
          <p key={e.field} className={styles.problem}>
            {e.message}
          </p>
        ))}
        <div className={styles.actions}>
          <button type="submit" disabled={save.isPending || (enabled && hoursProblem !== null)}>
            Kaydet
          </button>
        </div>
      </form>

      <section className={styles.card} aria-label="SLA hedefleri">
        <h2>SLA hedefleri</h2>
        <p className={styles.muted}>
          Hedefler sunucu ayarlarından gelir ve önceliğe göre belirlenir. {enabled ? 'Süreler mesai saatleriyle sayılır.' : ''}{' '}
          Süre aşılınca öncelik otomatik yükseltme: {calendar.raisePriorityOnBreach ? 'açık' : 'kapalı'}.
        </p>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Öncelik</th>
              <th>İlk yanıt</th>
              <th>Çözüm</th>
            </tr>
          </thead>
          <tbody>
            {[...PRIORITY_ORDER].reverse().map((p) => (
              <tr key={p}>
                <td>{PRIORITY_LABELS[p]}</td>
                <td>{targetText(calendar.firstResponseSeconds[p])}</td>
                <td>{targetText(calendar.resolutionSeconds[p])}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  );
}
