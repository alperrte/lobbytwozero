/**
 * Mesaj gövdesi istemci doğrulaması (sunucu kuralının aynası, `docs/api.md` → "Mesaj gönder"). Nihai karar sunucudadır.
 * - Uzunluk UTF-16 birimiyle sayılır (`string.length`; çoğu emoji 2 birim), en çok 4000.
 * - Boşluktan ibaret olamaz; satır sonu/sekme dışında kontrol karakteri ve eşlenmemiş vekil (bozuk emoji) içeremez.
 */
export const MAX_BODY_LENGTH = 4000;
/** Sayaç bu eşikten sonra görünür (alan dolarken). */
export const BODY_COUNTER_FROM = 3500;

export type BodyIssue = "empty" | "tooLong" | "invalidCharacters";

const CONTROL = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/;
const LONE_SURROGATE = /[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/;

export function validateBody(body: string, options: { optional?: boolean } = {}): BodyIssue | null {
  if (body.trim() === "") {
    // Görsel/GIF açıklaması isteğe bağlıdır: hiç yazılmamışsa sorun yok, yalnız boşluk yazılmışsa var.
    return options.optional && body.length === 0 ? null : "empty";
  }
  if (body.length > MAX_BODY_LENGTH) {
    return "tooLong";
  }
  if (CONTROL.test(body) || LONE_SURROGATE.test(body)) {
    return "invalidCharacters";
  }
  return null;
}

const CLIENT_ID = /^[A-Za-z0-9_-]{1,64}$/;

/** İstemcinin ürettiği tekil kimlik (`clientMessageId`): yeniden denemede aynı kalır, sunucu çift mesaj üretmez. */
export function newClientMessageId(): string {
  const value = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
  return CLIENT_ID.test(value) ? value : value.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64);
}

/** Grup başlığı: 1–100 karakter (baş/son boşluk kırpılır), kontrol karakteri yok. */
export const MAX_GROUP_TITLE = 100;

export function validateGroupTitle(title: string): "empty" | "tooLong" | "invalidCharacters" | null {
  const trimmed = title.trim();
  if (trimmed === "") return "empty";
  if ([...trimmed].length > MAX_GROUP_TITLE) return "tooLong";
  if (CONTROL.test(trimmed) || /[\n\r\t]/.test(trimmed)) return "invalidCharacters";
  return null;
}
