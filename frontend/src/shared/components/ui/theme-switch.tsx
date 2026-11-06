"use client";

import { useTranslations } from "next-intl";
import {
  useResolvedTheme,
  useThemePreference
} from "@/shared/hooks/use-theme-preference";
import { Icon } from "./icons";
import { iconButtonClass } from "./styles";

/*
 * Tek düğmeli tema kontrolü. Görünen ikon aktif temayı gösterir (Güneş = açık, Ay = koyu) ve
 * <html data-theme> üzerinden CSS ile seçilir; böylece kayıtlı tema ilk boyamada doğru görünür
 * (hydration sonrası ikon sıçraması olmaz). Düğmenin adı, tıklayınca geçilecek temayı söyler.
 * "Sistem" tercihi Ayarlar sayfasındadır; burada görünen tema değiştirilir.
 */
export function ThemeSwitch() {
  const t = useTranslations("Theme");
  const [preference, setPreference] = useThemePreference();
  const resolved = useResolvedTheme();
  const current = preference === "system" ? resolved : preference;
  const next = current === "dark" ? "light" : "dark";

  return (
    <button
      type="button"
      aria-label={t(next)}
      title={t(next)}
      onClick={() => setPreference(next)}
      className={iconButtonClass}
    >
      <Icon name="moon" className="size-5 in-data-[theme=light]:hidden" />
      <Icon name="sun" className="hidden size-5 in-data-[theme=light]:block" />
    </button>
  );
}
