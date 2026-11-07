/** Sistem gereksinimi boyutu: veritabanı MB tutar; 1024 ve üstü GB olarak gösterilir. Eksik değer hiç uydurulmaz. */
export function formatMegabytes(value: number | null | undefined, locale: string): string | null {
  if (value == null || !Number.isFinite(value) || value < 0) return null;
  const number = (amount: number, digits: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: digits }).format(amount);
  return value >= 1024 ? `${number(value / 1024, 1)} GB` : `${number(value, 0)} MB`;
}
