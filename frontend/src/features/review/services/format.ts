/** Ortalama puan (1–10, en çok iki ondalık). `null` = puan yok; çağıran "0/10" yerine boş durum gösterir. */
export function formatAverage(value: number | null, locale: string): string | null {
  if (value === null || !Number.isFinite(value)) return null;
  return new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 2 }).format(value);
}

/** Yüzde (0–100 ölçeği). `null` = veri yok; sahte %0 üretilmez. */
export function formatPercentage(value: number | null, locale: string): string | null {
  if (value === null || !Number.isFinite(value)) return null;
  return `${new Intl.NumberFormat(locale, { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(value)}%`;
}

export function formatCount(value: number, locale: string): string {
  return new Intl.NumberFormat(locale).format(value);
}

/** Geçersiz tarih metni için `null` (arayüz "bilinmiyor" yazar); saat dilimi kullanıcıya göredir (backend UTC verir). */
export function formatDateTime(value: string, locale: string): string | null {
  const time = Date.parse(value);
  return Number.isNaN(time) ? null : new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(time));
}

/** Düzenlenmiş mi: `updatedAt` oluşturulma zamanından sonraysa. Yalnız ham zaman karşılaştırması. */
export function wasEdited(createdAt: string, updatedAt: string): boolean {
  const created = Date.parse(createdAt);
  const updated = Date.parse(updatedAt);
  return !Number.isNaN(created) && !Number.isNaN(updated) && updated > created;
}

/** Oyun kapağı: kayıtlı https kapak; yoksa Steam başlık görseli (yalnız rakamlı AppID). Yoksa `null` (uydurma görsel yok). */
export function safeCoverUrl(card: { coverUrl?: string | null; externalReferences?: { provider: string; externalId: string }[] }): string | null {
  if (card.coverUrl) {
    try {
      const url = new URL(card.coverUrl);
      if (url.protocol === "https:" && !url.username && !url.password) return url.href;
    } catch {
      /* geçersiz adres: Steam yedeğine düşer */
    }
  }
  const steam = card.externalReferences?.find((reference) => reference.provider === "STEAM" && /^\d{1,10}$/.test(reference.externalId));
  return steam ? `https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${steam.externalId}/header.jpg` : null;
}
