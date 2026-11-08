/**
 * Arkadaşlık davet kodu kuralları (SOC-14). Sunucu kuralıyla aynıdır (`InviteCodeGenerator`):
 * 10 karakter, Crockford benzeri alfabe (`0-9`, `I L O U` hariç büyük harfler). Nihai doğrulama sunucudadır.
 */
export const INVITE_CODE_LENGTH = 10;

const WELL_FORMED = /^[0-9A-HJKMNP-TV-Z]{10}$/;

/** Kullanıcının yazdığı/yapıştırdığı kodu sunucu biçimine çevirir: boşluk ve tireler atılır, harfler büyütülür. */
export function normalizeInviteCode(input: string): string {
  return input.replace(/[\s-]+/g, "").toUpperCase();
}

export type InviteCodeIssue = "empty" | "length" | "characters";

/** Biçim hatası yoksa `null`; varsa alanın altında gösterilecek sorun. */
export function validateInviteCode(input: string): InviteCodeIssue | null {
  const code = normalizeInviteCode(input);
  if (code.length === 0) {
    return "empty";
  }
  if (code.length !== INVITE_CODE_LENGTH) {
    return /^[0-9A-Z]*$/.test(code) ? "length" : "characters";
  }
  return WELL_FORMED.test(code) ? null : "characters";
}

/** Okunurluk için 5-5 gruplanmış gösterim (`ABCDE-FGHJK`). Kopyalanan değer gruplanmamış koddur. */
export function formatInviteCode(code: string): string {
  return code.length === INVITE_CODE_LENGTH ? `${code.slice(0, 5)}-${code.slice(5)}` : code;
}
