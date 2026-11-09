/**
 * Fiyat pazarları: ITAD ülke başına tek para birimi döndürür, bu yüzden ülke ve para birimi birlikte seçilir; kullanıcı ülkeyi seçer,
 * para birimi otomatik gelir. Her para birimi tek ülkeye karşılık gelir (URL parametresi `currency` bu yüzden benzersizdir).
 * TRY sunulmaz: Steam Türkiye için TL ile satış yapmaz. Backend (ltz.game.pricing.markets) aynı pazarları yeniler; çift,
 * canlı ITAD ile doğrulanmış olmalıdır (ITAD yerel para birimi döndürmeyen ülkede fiyat boş kalır).
 */
export const priceMarkets = [
  { currency: "USD", country: "US" },
  { currency: "EUR", country: "DE" },
  { currency: "GBP", country: "GB" },
  { currency: "CAD", country: "CA" },
  { currency: "AUD", country: "AU" },
  { currency: "NZD", country: "NZ" },
  { currency: "PLN", country: "PL" },
  { currency: "BRL", country: "BR" },
  { currency: "JPY", country: "JP" },
  { currency: "KRW", country: "KR" },
  { currency: "INR", country: "IN" },
  { currency: "CNY", country: "CN" }
] as const;

export const defaultCurrency = "USD";

/** Seçenek metni: ülke adı (arayüz dilinde) ve o ülkeyle birlikte gelen para birimi, örn. "Amerika Birleşik Devletleri (USD)". */
export function marketLabel(market: { currency: string; country: string }, locale: string): string {
  let region = market.country;
  try { region = new Intl.DisplayNames([locale], { type: "region" }).of(market.country) ?? market.country; } catch { /* geçersiz kod: ham kod gösterilir */ }
  return `${region} (${market.currency})`;
}

/** Seçim listesi sırası: varsayılan pazar (ABD) başta, kalanlar arayüz dilindeki ülke adına göre alfabetik. */
export function marketOptions(locale: string): { currency: string; country: string }[] {
  const [first, ...rest] = priceMarkets;
  return [first, ...[...rest].sort((a, b) => marketLabel(a, locale).localeCompare(marketLabel(b, locale), locale))];
}

export function marketForCurrency(currency: string | null | undefined): { currency: string; country: string } {
  return priceMarkets.find((market) => market.currency === currency) ?? priceMarkets[0];
}
