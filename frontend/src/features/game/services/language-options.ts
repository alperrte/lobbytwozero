import type { ComboOption } from "../components/game-combobox";

/**
 * Dil filtresi önerileri: yalnız Game veritabanında (game_languages) gerçekten bulunan kodlardır
 * (DEV ölçümü, 2026-11-08; sıklığa göre). Backend süzgeci bu kodlarla tam eşleşir (ör. "tr" değil "tr-tr").
 * Liste dışı kodlar serbest yazılabilir; yeni kod geldikçe liste doğrulanarak genişletilmelidir.
 */
export const verifiedLanguageCodes = [
  "en-us", "de-de", "fr-fr", "es-es", "it-it", "ru-ru", "pl-pl", "ja-jp", "zh-cn", "ko-kr", "cs-cz", "pt-br", "zh-tw", "pt-pt", "nl-nl",
  "hu-hu", "sv-se", "tr-tr", "da-dk", "fi-fi", "nb-no", "uk-ua", "en-gb", "es-mx", "th-th", "vi-vn", "ar", "he-il"
] as const;

function displayName(code: string, locale: string): string {
  try {
    const [language, region] = code.split("-");
    const names = new Intl.DisplayNames([locale], { type: "language" });
    return names.of(region ? `${language}-${region.toUpperCase()}` : language) ?? code;
  } catch {
    return code;
  }
}

export function languageOptions(locale: string): ComboOption[] {
  return verifiedLanguageCodes.map((code) => ({ value: code, label: displayName(code, locale) }));
}

/** `tr-tr` → `TR`; bölge yoksa (`ar`) veya geçersizse undefined (bayrak uydurulmaz). */
export function languageRegion(code: string): string | undefined {
  const region = code.split("-")[1];
  return region && /^[a-z]{2}$/i.test(region) ? region.toUpperCase() : undefined;
}

/** Dil adı (seçili arayüz dilinde, örn. "Almanca (Almanya)"); Intl desteklemezse kod döner. */
export function languageLabel(code: string, locale: string): string {
  return displayName(code, locale);
}
