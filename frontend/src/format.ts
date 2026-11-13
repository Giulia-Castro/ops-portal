export function percent(value: number | null | undefined, locale: string, digits = 1): string {
  if (value === null || value === undefined) return '–'
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value)
}

export function number(value: number, locale: string, digits = 0): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits: digits }).format(value)
}

export function usd(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: value > 0 && value < 1 ? 4 : 2,
  }).format(value)
}

export function seconds(ms: number, locale: string): string {
  return `${number(ms / 1000, locale, 1)} s`
}

export function dateTime(iso: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso))
}

/** Report days are calendar days (no time zone shift): format the yyyy-mm-dd parts directly. */
export function day(isoDate: string, locale: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', timeZone: 'UTC' }).format(
    new Date(Date.UTC(y, m - 1, d)),
  )
}
