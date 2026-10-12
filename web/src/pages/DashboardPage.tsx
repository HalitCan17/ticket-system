import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { useReportsApi } from '../api/ApiContext.tsx';
import type { TicketCategory, TicketStatus } from '../api/types.ts';
import { useAuth } from '../auth/AuthContext.tsx';
import { BarList } from '../components/BarList.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';
import { formatDuration } from '../format.ts';
import { CATEGORY_LABELS, CATEGORY_ORDER, STATUS_LABELS, STATUS_ORDER } from '../labels.ts';
import { SCORE_LABELS } from '../components/Rating.tsx';
import styles from './DashboardPage.module.css';

const PERIODS = [7, 30, 90] as const;
type Period = (typeof PERIODS)[number];

function parsePeriod(value: string | null): Period {
  return PERIODS.find((p) => String(p) === value) ?? 30;
}

const number = new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 1, minimumFractionDigits: 1 });
const percent = new Intl.NumberFormat('tr-TR', { style: 'percent', maximumFractionDigits: 0 });

/** Manager overview (docs/adr/0013-manager-dashboard.md). */
export function DashboardPage() {
  const { user } = useAuth();
  if (user.role !== 'MANAGER') {
    return (
      <section>
        <h1>Panel</h1>
        <p>Bu sayfa yalnızca yöneticiler içindir.</p>
      </section>
    );
  }
  return <Dashboard />;
}

function Dashboard() {
  const api = useReportsApi();
  const [searchParams, setSearchParams] = useSearchParams();
  const days = parsePeriod(searchParams.get('days'));

  const summary = useQuery({ queryKey: ['reports', 'summary', days], queryFn: () => api.summary(days) });
  const team = useQuery({ queryKey: ['reports', 'team', days], queryFn: () => api.team(days) });
  const groups = useQuery({ queryKey: ['reports', 'groups'], queryFn: () => api.groups() });

  const s = summary.data;

  return (
    <section className={styles.page}>
      <div className={styles.toolbar}>
        <h1>Panel</h1>
        <div className={styles.periods} role="group" aria-label="Dönem">
          {PERIODS.map((p) => (
            <button
              key={p}
              type="button"
              aria-pressed={p === days}
              className={p === days ? styles.periodActive : styles.period}
              onClick={() => setSearchParams(p === 30 ? {} : { days: String(p) })}
            >
              Son {p} gün
            </button>
          ))}
        </div>
      </div>

      {summary.isPending && <p className={styles.muted}>Yükleniyor…</p>}
      {summary.isError && <ErrorMessage error={summary.error} />}
      {s && (
        <>
          <h2 className={styles.sectionTitle}>Şu an</h2>
          <div className={styles.tiles}>
            <Tile label="Açık talep" value={String(s.openTickets)} link="/tickets" />
            <Tile label="Atanmamış" value={String(s.unassignedOpen)} link="/tickets?assignee=unassigned&sort=sla" />
            <Tile
              label="SLA'sı aşılmış"
              value={String(s.breachedOpen)}
              tone={s.breachedOpen > 0 ? 'danger' : undefined}
              link="/tickets?sort=sla"
            />
          </div>

          <h2 className={styles.sectionTitle}>Son {days} gün</h2>
          <div className={styles.tiles}>
            <Tile label="Açılan" value={String(s.created)} />
            <Tile label="Çözülen" value={String(s.resolution.resolved)} />
            <Tile
              label="SLA uyumu"
              value={s.resolution.withinSlaRate === null ? '—' : percent.format(s.resolution.withinSlaRate)}
              hint={
                s.resolution.resolved === 0
                  ? 'Bu dönemde çözülen talep yok'
                  : `${s.resolution.withinSla} / ${s.resolution.resolved} talep hedefinde çözüldü`
              }
            />
            <Tile
              label="Ortalama çözüm süresi"
              value={s.resolution.avgResolveSeconds === null ? '—' : formatDuration(s.resolution.avgResolveSeconds)}
              hint="Müşteri beklemesi hariç"
            />
            <Tile
              label="Memnuniyet"
              value={s.satisfaction.average === null ? '—' : `${number.format(s.satisfaction.average)} / 5`}
              hint={s.satisfaction.count === 0 ? 'Bu dönemde puan verilmedi' : `${s.satisfaction.count} puan`}
            />
          </div>

          <div className={styles.charts}>
            <figure className={styles.card}>
              <figcaption>Statü dağılımı (şu an)</figcaption>
              <BarList
                title="Statü dağılımı"
                unit="talep"
                bars={STATUS_ORDER.map((status: TicketStatus) => ({
                  key: status,
                  label: STATUS_LABELS[status],
                  value: s.statusCounts[status] ?? 0,
                }))}
              />
            </figure>
            <figure className={styles.card}>
              <figcaption>Açılan taleplerin kategorisi</figcaption>
              <BarList
                title="Kategori dağılımı"
                unit="talep"
                bars={CATEGORY_ORDER.map((category: TicketCategory) => ({
                  key: category,
                  label: CATEGORY_LABELS[category],
                  value: s.categories[category] ?? 0,
                }))}
              />
            </figure>
            <figure className={styles.card}>
              <figcaption>Memnuniyet puanları</figcaption>
              <BarList
                title="Memnuniyet dağılımı"
                unit="puan"
                bars={[5, 4, 3, 2, 1].map((score) => ({
                  key: String(score),
                  label: `${score} – ${SCORE_LABELS[score]}`,
                  value: s.satisfaction.distribution[score - 1] ?? 0,
                }))}
              />
            </figure>
          </div>
        </>
      )}

      <h2 className={styles.sectionTitle}>Gruplar (şu an)</h2>
      {groups.isError && <ErrorMessage error={groups.error} />}
      {groups.data && groups.data.length > 0 && (
        <div className={styles.tableWrap}>
          <table className={styles.table} aria-label="Grup iş yükü">
            <thead>
              <tr>
                <th>Grup</th>
                <th className={styles.num}>Üye</th>
                <th className={styles.num}>Açık iş</th>
                <th className={styles.num}>Atanmamış</th>
                <th className={styles.num}>SLA'sı aşılmış</th>
              </tr>
            </thead>
            <tbody>
              {groups.data.map((group) => (
                <tr key={group.id ?? 'none'}>
                  <td>
                    {group.id === null ? (
                      <span className={styles.muted}>Grupsuz talepler</span>
                    ) : (
                      <Link to={`/tickets?group=${group.id}&sort=sla`}>{group.name}</Link>
                    )}
                  </td>
                  <td className={`${styles.num} ${group.id !== null && group.members === 0 ? styles.danger : ''}`}>
                    {group.id === null ? '—' : group.members}
                  </td>
                  <td className={styles.num}>{group.openWork}</td>
                  <td className={styles.num}>{group.unassignedOpen}</td>
                  <td className={`${styles.num} ${group.breachedOpen > 0 ? styles.danger : ''}`}>
                    {group.breachedOpen}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className={styles.sectionTitle}>Ekip</h2>
      {team.isError && <ErrorMessage error={team.error} />}
      {team.data && team.data.length === 0 && <p className={styles.muted}>Henüz giriş yapmış personel yok.</p>}
      {team.data && team.data.length > 0 && (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Kişi</th>
                <th className={styles.num}>Açık iş</th>
                <th className={styles.num}>SLA'sı aşılmış</th>
                <th className={styles.num}>Çözdüğü ({days} gün)</th>
                <th className={styles.num}>Memnuniyet</th>
              </tr>
            </thead>
            <tbody>
              {team.data.map((member) => (
                <tr key={member.id}>
                  <td>
                    <Link to={`/tickets?assignee=${encodeURIComponent(member.id)}&sort=sla`}>{member.name}</Link>
                    {member.role === 'MANAGER' && <span className={styles.muted}> · yönetici</span>}
                  </td>
                  <td className={styles.num}>{member.openWork}</td>
                  <td className={`${styles.num} ${member.breachedOpen > 0 ? styles.danger : ''}`}>
                    {member.breachedOpen}
                  </td>
                  <td className={styles.num}>{member.resolved}</td>
                  <td className={styles.num}>
                    {member.averageRating === null
                      ? '—'
                      : `${number.format(member.averageRating)} (${member.ratings})`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className={styles.muted}>
        Ekipte en az bir kez giriş yapmış personel ve açık işi olan herkes listelenir. Çözülen talepler ve puanlar,
        talebin atandığı kişiye yazılır.
      </p>
    </section>
  );
}

interface TileProps {
  label: string;
  value: string;
  hint?: string;
  tone?: 'danger';
  link?: string;
}

function Tile({ label, value, hint, tone, link }: TileProps) {
  const body = (
    <>
      <span className={styles.tileLabel}>{label}</span>
      <span className={`${styles.tileValue} ${tone === 'danger' ? styles.danger : ''}`}>{value}</span>
      {hint && <span className={styles.tileHint}>{hint}</span>}
    </>
  );
  return link ? (
    <Link to={link} className={`${styles.tile} ${styles.tileLink}`} aria-label={`${label}: ${value}`}>
      {body}
    </Link>
  ) : (
    <div className={styles.tile} role="group" aria-label={`${label}: ${value}`}>
      {body}
    </div>
  );
}
