/**
 * Yalnız bellek içi, kısa ömürlü önbellek. Amaç: hub sekmeleri arasında gezinirken dakikada 10'luk "generation" kotasını
 * (arkadaş/oyuncu/etkinlik önerileri) gereksiz harcamamak. Kişisel veri localStorage/sessionStorage/URL'ye YAZILMAZ;
 * sayfa yenilenince kaybolur. Anahtar her zaman hesap kimliğiyle başlar; hesap değişince ya da rıza değişince silinir.
 */
export const CACHE_TTL_MS = 2 * 60 * 1000;
const MAX_ENTRIES = 64;

type Entry = { accountId: string; value: unknown; storedAt: number };
const entries = new Map<string, Entry>();
let owner: string | null = null;

function scoped(accountId: string, key: string): string {
  return `${accountId}\u0000${key}`;
}

/** Başka hesaba geçildiyse önceki hesabın tüm kayıtlarını siler (paylaşılan tarayıcıda veri sızmasın). */
function claim(accountId: string): void {
  if (owner !== accountId) {
    entries.clear();
    owner = accountId;
  }
}

export const recommendationCache = {
  get<T>(accountId: string, key: string, now = Date.now()): T | undefined {
    claim(accountId);
    const entry = entries.get(scoped(accountId, key));
    if (!entry) return undefined;
    if (now - entry.storedAt > CACHE_TTL_MS) {
      entries.delete(scoped(accountId, key));
      return undefined;
    }
    return entry.value as T;
  },
  set<T>(accountId: string, key: string, value: T, now = Date.now()): void {
    claim(accountId);
    if (entries.size >= MAX_ENTRIES) {
      const oldest = entries.keys().next().value;
      if (oldest !== undefined) entries.delete(oldest);
    }
    entries.set(scoped(accountId, key), { accountId, value, storedAt: now });
  },
  /** Bir anahtar önekini (ör. `runs:`) geçersiz kılar: yeni öneri üretildiğinde geçmiş listesi tazelenir. */
  invalidate(accountId: string, prefix: string): void {
    const start = scoped(accountId, prefix);
    for (const name of [...entries.keys()]) if (name.startsWith(start)) entries.delete(name);
  },
  /** Rıza değişti veya `CONSENT_REQUIRED` alındı: backend geçmişi silmiş olabilir, hiçbir eski sonuç gösterilmez. */
  clear(): void {
    entries.clear();
  }
};
