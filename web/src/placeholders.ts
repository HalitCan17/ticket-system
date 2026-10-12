/** Values for the placeholders a canned response may contain. */
export interface PlaceholderValues {
  /** {müşteri}: the requester's name */
  customer: string;
  /** {talep}: the ticket number, shown as #id */
  ticketId: number;
  /** {ben}: the staff member writing the comment */
  me: string;
}

export const PLACEHOLDERS = ['{müşteri}', '{talep}', '{ben}'] as const;

/** Fills known placeholders; anything else in braces is left as written. */
export function fillPlaceholders(text: string, values: PlaceholderValues): string {
  return text
    .replaceAll('{müşteri}', values.customer)
    .replaceAll('{talep}', `#${values.ticketId}`)
    .replaceAll('{ben}', values.me);
}
