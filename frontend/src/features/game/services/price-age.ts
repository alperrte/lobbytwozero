/**
 * Bayat fiyat notu için göreli süre ("3 gün önce"). Dakika, saat veya gün seçilir; gelecekteki bir zaman (saat farkı) "1 dakika önce"ye yuvarlanır,
 * yani yaş hiçbir zaman olumsuz veya "az sonra" görünmez.
 */
export function relativeAge(fetchedAtMs: number, nowMs: number, locale: string): string {
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
  const seconds = Math.max(0, Math.round((nowMs - fetchedAtMs) / 1000));
  if (seconds < 3600) return formatter.format(-Math.max(1, Math.floor(seconds / 60)), "minute");
  if (seconds < 86400) return formatter.format(-Math.floor(seconds / 3600), "hour");
  return formatter.format(-Math.floor(seconds / 86400), "day");
}
