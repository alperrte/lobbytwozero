"use client";

import { useId, useTransition } from "react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Flag } from "@/shared/components/ui/flags";
import { Icon } from "@/shared/components/ui/icons";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import { OptionGroup } from "@/shared/components/ui/option-group";
import { iconButtonClass } from "@/shared/components/ui/styles";
import { useNavbarMode } from "@/shared/hooks/use-navbar-mode";
import { useSidebarCollapsed } from "@/shared/hooks/use-sidebar-collapsed";
import { useThemePreference } from "@/shared/hooks/use-theme-preference";
import { setLocale } from "@/shared/lib/i18n/actions";
import { locales, type Locale } from "@/shared/lib/i18n/config";

type SettingsDialogProps = {
  open: boolean;
  onClose: () => void;
};

/** Ayarlar çekmecesi. Hesap/güvenlik ayarı yoktur; yalnız görünüm ve gezinme tercihleri. */
export function SettingsDialog({ open, onClose }: SettingsDialogProps) {
  const titleId = useId();

  return (
    <ModalDialog
      open={open}
      onClose={onClose}
      labelledBy={titleId}
      className="ms-auto me-0 mt-0 h-dvh max-h-none w-[26rem] max-w-[92vw] border-s border-border bg-surface shadow-drawer open:animate-drawer-in"
    >
      <SettingsPanel titleId={titleId} onClose={onClose} />
    </ModalDialog>
  );
}

const nativeNames: Record<Locale, string> = { tr: "Türkçe", en: "English" };

function SettingsPanel({
  titleId,
  onClose
}: {
  titleId: string;
  onClose: () => void;
}) {
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
    <div className="flex h-dvh flex-col">
      <div className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
        <div className="min-w-0">
          <h2 id={titleId} className="font-display text-lg font-semibold">
            {t("title")}
          </h2>
          <p className="mt-0.5 text-sm text-text-secondary">{t("description")}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label={t("close")}
          className={iconButtonClass}
        >
          <Icon name="close" />
        </button>
      </div>

      <div className="flex-1 space-y-6 overflow-y-auto overscroll-contain px-5 py-5">
        <OptionGroup
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

        <OptionGroup
          legend={t("navbar.title")}
          name="settings-navbar"
          value={navbar}
          onChange={setNavbar}
          options={[
            { value: "auto", label: t("navbar.auto"), hint: t("navbar.autoHint") },
            { value: "fixed", label: t("navbar.fixed"), hint: t("navbar.fixedHint") }
          ]}
        />

        <OptionGroup
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

        <OptionGroup
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
      </div>
    </div>
  );
}
