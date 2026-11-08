/**
 * Fiyat pazarları: ITAD ülke başına tek para birimi döndürür, bu yüzden ülke ve para birimi birlikte seçilir
 * (USD → ABD, EUR → Almanya, GBP → Birleşik Krallık). TRY sunulmaz: Steam Türkiye için TL ile satış yapmaz.
 * Backend (ltz.game.pricing.markets) aynı üç pazarı yeniler.
 */
export const priceMarkets = [
  { currency: "USD", country: "US" },
  { currency: "EUR", country: "DE" },
  { currency: "GBP", country: "GB" }
] as const;

export const defaultCurrency = "USD";

/** Seçenek metni: ülke adı (arayüz dilinde) ve o ülkeyle birlikte gelen para birimi, örn. "Amerika Birleşik Devletleri (USD)". */
export function marketLabel(market: { currency: string; country: string }, locale: string): string {
  let region = market.country;
  try { region = new Intl.DisplayNames([locale], { type: "region" }).of(market.country) ?? market.country; } catch { /* geçersiz kod: ham kod gösterilir */ }
  return `${region} (${market.currency})`;
}

export function marketForCurrency(currency: string | null | undefined): { currency: string; country: string } {
  return priceMarkets.find((market) => market.currency === currency) ?? priceMarkets[0];
}
