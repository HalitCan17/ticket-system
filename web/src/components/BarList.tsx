import styles from './BarList.module.css';

export interface Bar {
  key: string;
  label: string;
  value: number;
}

interface Props {
  /** Accessible name of the list, e.g. "Statü dağılımı". */
  title: string;
  bars: Bar[];
  /** Unit after the number in the tooltip, e.g. "talep". */
  unit: string;
}

/**
 * One series of magnitudes as horizontal bars: a single hue, the exact number always written next to the bar
 * (so nothing depends on reading the bar length or the colour), a tooltip with the share of the total.
 */
export function BarList({ title, bars, unit }: Props) {
  const max = Math.max(1, ...bars.map((b) => b.value));
  const total = bars.reduce((sum, b) => sum + b.value, 0);

  return (
    <ul className={styles.list} aria-label={title}>
      {bars.map((bar) => {
        const share = total === 0 ? 0 : Math.round((bar.value / total) * 100);
        return (
          <li key={bar.key} className={styles.row} title={`${bar.label}: ${bar.value} ${unit} (%${share})`}>
            <span className={styles.label}>{bar.label}</span>
            <span className={styles.track} aria-hidden="true">
              {bar.value > 0 && <span className={styles.bar} style={{ width: `${(bar.value / max) * 100}%` }} />}
            </span>
            <span className={styles.value}>{bar.value}</span>
          </li>
        );
      })}
    </ul>
  );
}
