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

export function marketForCurrency(currency: string | null | undefined): { currency: string; country: string } {
  return priceMarkets.find((market) => market.currency === currency) ?? priceMarkets[0];
}
