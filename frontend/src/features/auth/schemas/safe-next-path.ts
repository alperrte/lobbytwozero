/**
 * Giriş sonrası yönlendirme hedefi. Yalnız dahili yol kabul edilir: `/` ile başlar, `//` ile başlamaz,
 * `\` , boşluk ve denetim karakteri içermez, şema taşımaz. Kodu çözülmüş hâli de aynı kurallardan geçmelidir
 * (`/%5Cevil`, `/%2F%2Fevil`). Aksi halde `null` (çağıran ana sayfaya gider). Dış adrese yönlendirme yoktur.
 */
const MAX_LENGTH = 512;

function isPlainPath(value: string): boolean {
  if (!value.startsWith("/") || value.startsWith("//")) {
    return false;
  }
  for (const char of value) {
    const code = char.codePointAt(0) ?? 0;
    const control = code <= 0x1f || code === 0x7f;
    if (control || char === "\\" || /\s/.test(char)) {
      return false;
    }
  }
  return true;
}

export function safeNextPath(value: string | null | undefined): string | null {
  if (typeof value !== "string" || value === "" || value.length > MAX_LENGTH) {
    return null;
  }
  if (!isPlainPath(value)) {
    return null;
  }
  try {
    return isPlainPath(decodeURIComponent(value)) ? value : null;
  } catch {
    return null;
  }
}
