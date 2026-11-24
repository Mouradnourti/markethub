/** Business days between order and delivery promised on product pages (sellers ship within 1–2 days). */
export const DELIVERY_MIN_DAYS = 2;
export const DELIVERY_MAX_DAYS = 5;

/** Adds working days (Monday to Friday) to a date. */
export function addBusinessDays(from: Date, days: number): Date {
  const date = new Date(from);
  let added = 0;
  while (added < days) {
    date.setDate(date.getDate() + 1);
    const day = date.getDay();
    if (day !== 0 && day !== 6) {
      added++;
    }
  }
  return date;
}

/** Estimated delivery window for an order placed now. */
export function deliveryWindow(now: Date = new Date()): { from: Date; to: Date } {
  return { from: addBusinessDays(now, DELIVERY_MIN_DAYS), to: addBusinessDays(now, DELIVERY_MAX_DAYS) };
}
