"use client";

import { useId, useTransition, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Flag } from "@/shared/components/ui/flags";
import { Icon } from "@/shared/components/ui/icons";
import { InfoTip } from "@/shared/components/ui/info-tip";
import { OptionGroup } from "@/shared/components/ui/option-group";
import { useNavbarMode } from "@/shared/hooks/use-navbar-mode";
import { useSidebarCollapsed } from "@/shared/hooks/use-sidebar-collapsed";
import { useThemePreference } from "@/shared/hooks/use-theme-preference";
import { setLocale } from "@/shared/lib/i18n/actions";
import { locales, type Locale } from "@/shared/lib/i18n/config";

const nativeNames: Record<Locale, string> = { tr: "Türkçe", en: "English" };

type SettingCardProps = {
  title: string;
  info: string;
  children: (titleId: string) => ReactNode;
};

/** Tek bir ayar grubu: başlık, yanında "i" açıklaması ve seçenekler. */
function SettingCard({ title, info, children }: SettingCardProps) {
  const titleId = useId();

  return (
    <section
      aria-labelledby={titleId}
      className="rounded-panel border border-border bg-surface p-5"
    >
      <div className="mb-4 flex items-center gap-2">
        <h2 id={titleId} className="font-display text-base font-semibold">
          {title}
        </h2>
        <InfoTip subject={title}>{info}</InfoTip>
      </div>
      {children(titleId)}
    </section>
  );
}

/**
 * Ayarlar sayfasının içeriği: görünüm, üst çubuk, kenar çubuğu ve dil tercihleri.
 * Seçimler anında uygulanır ve bu cihazda saklanır; "Kaydet" adımı yoktur.
 * Hesap/güvenlik ayarı yoktur. Her grubun yanındaki "i" ne işe yaradığını anlatır.
 */
export function SettingsForm() {
  const t = useTranslations("Settings");
  const router = useRouter();
  const activeLocale = useLocale() as Locale;
  const [theme, setTheme] = useThemePreference();
  const [navbar, setNavbar] = useNavbarMode();
  const [collapsed, setCollapsed] = useSidebarCollapsed();
  const [pending, startTransition] = useTransition();

  function changeLocale(locale: Locale) {
    if (locale === activeLocale) {
      return;
    }
    startTransition(async () => {
      await setLocale(locale);
      router.refresh();
    });
  }

  return (
    <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-4">
      <SettingCard title={t("appearance.title")} info={t("appearance.info")}>
        {() => (
          <OptionGroup
            hideLegend
            legend={t("appearance.title")}
            name="settings-theme"
            value={theme}
            onChange={setTheme}
            options={[
              {
                value: "system",
                label: t("appearance.system"),
                hint: t("appearance.systemHint"),
                leading: <Icon name="contrast" className="size-[18px] text-text-secondary" />
              },
              {
                value: "light",
                label: t("appearance.light"),
                hint: t("appearance.lightHint"),
                leading: <Icon name="sun" className="size-[18px] text-text-secondary" />
              },
              {
                value: "dark",
                label: t("appearance.dark"),
                hint: t("appearance.darkHint"),
                leading: <Icon name="moon" className="size-[18px] text-text-secondary" />
              }
            ]}
          />
        )}
      </SettingCard>

      <SettingCard title={t("navbar.title")} info={t("navbar.info")}>
        {() => (
          <OptionGroup
            hideLegend
            legend={t("navbar.title")}
            name="settings-navbar"
            value={navbar}
            onChange={setNavbar}
            options={[
              { value: "auto", label: t("navbar.auto"), hint: t("navbar.autoHint") },
              { value: "fixed", label: t("navbar.fixed"), hint: t("navbar.fixedHint") }
            ]}
          />
        )}
      </SettingCard>

      <SettingCard title={t("sidebar.title")} info={t("sidebar.info")}>
        {() => (
          <OptionGroup
            hideLegend
            legend={t("sidebar.title")}
            name="settings-sidebar"
            value={collapsed ? "collapsed" : "wide"}
            onChange={(value) => setCollapsed(value === "collapsed")}
            note={t("sidebar.note")}
            options={[
              { value: "wide", label: t("sidebar.wide"), hint: t("sidebar.wideHint") },
              {
                value: "collapsed",
                label: t("sidebar.collapsed"),
                hint: t("sidebar.collapsedHint")
              }
            ]}
          />
        )}
      </SettingCard>

      <SettingCard title={t("language.title")} info={t("language.info")}>
        {() => (
          <OptionGroup
            hideLegend
            legend={t("language.title")}
            name="settings-language"
            value={activeLocale}
            onChange={changeLocale}
            disabled={pending}
            options={locales.map((locale) => ({
              value: locale,
              label: nativeNames[locale],
              leading: <Flag locale={locale} />
            }))}
          />
        )}
      </SettingCard>
    </div>
  );
}
