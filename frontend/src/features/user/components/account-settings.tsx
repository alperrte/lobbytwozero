"use client";

import { useTranslations } from "next-intl";
import { AccountAgeSection, AccountDeletionSection, AccountSecuritySection, useRequireSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { Link } from "@/shared/lib/i18n/navigation";
import { useRemote } from "../hooks/use-remote";
import { getConsents, getLinkedSteam, getPreferences, getPrivacy } from "../services/settings-api";
import { ConsentsSection } from "./consents-section";
import { PreferencesSection } from "./preferences-section";
import { PrivacySection } from "./privacy-section";
import { ProfileSection, RemoteBody } from "./remote-section";
import { SteamSection } from "./steam-section";

/** Sol menüdeki gruplar ve bölümleri; sıra sayfadaki bölüm sırasıyla aynıdır. Bölüm kimlikleri sayfadaki `id`lerdir. */
const SETTINGS_GROUPS = [
  { id: "profile", sections: ["privacy", "consents"] },
  { id: "account", sections: ["preferences", "steam"] },
  { id: "protection", sections: ["security", "age"] },
  { id: "danger", sections: ["deletion"] }
] as const;

/**
 * Hesap ayarları: gizlilik, hesap tercihleri, bağlı hesap (Steam) izni ve rızalar. Her bölüm kendi ucundan bağımsız okunur; birinin hatası diğerlerini
 * bozmaz. Oturum doğrulanmadan hiçbir istek atılmaz (misafir girişe yönlendirilir).
 */
export function AccountSettings() {
  const session = useRequireSession();
  const enabled = session.status === "authenticated";
  const t = useTranslations("AccountSettings");
  const privacy = useRemote(enabled ? "privacy" : null, getPrivacy);
  const preferences = useRemote(enabled ? "preferences" : null, getPreferences);
  const consents = useRemote(enabled ? "consents" : null, getConsents);
  const steam = useRemote(enabled ? "steam" : null, getLinkedSteam);

  if (!enabled) {
    return (
      <PageContainer>
        <p role="status" className="text-text-secondary">
          {t("loading")}
        </p>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <div>
          <Link href="/profile" className="text-sm text-brand-text hover:underline">
            {t("back")}
          </Link>
          <h1 className="mt-2 font-display text-2xl font-semibold sm:text-3xl">{t("title")}</h1>
          <p className="mt-1 max-w-2xl text-text-secondary">{t("description")}</p>
        </div>

        <div className="grid gap-6 lg:grid-cols-[13rem_minmax(0,1fr)]">
          <nav aria-label={t("navLabel")} className="lg:sticky lg:top-24 lg:self-start">
            <ul className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:gap-4 lg:overflow-visible lg:pb-0">
              {SETTINGS_GROUPS.map((group) => (
                <li key={group.id} className="shrink-0 lg:shrink">
                  <p className="hidden px-3 pb-1 font-mono text-[11px] uppercase tracking-[0.14em] text-text-secondary lg:block">
                    {t(`groups.${group.id}`)}
                  </p>
                  <ul className="flex gap-1 lg:flex-col">
                    {group.sections.map((section) => (
                      <li key={section} className="shrink-0">
                        <a
                          href={`#${section}`}
                          className={`inline-flex min-h-10 w-full items-center whitespace-nowrap rounded-control border-l-2 px-3 text-sm transition-colors hover:bg-surface-elevated hover:text-text-primary ${
                            group.id === "danger" ? "border-primary/50 text-brand-text" : "border-transparent text-text-secondary hover:border-primary"
                          }`}
                        >
                          {t(`${section}.title`)}
                        </a>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          </nav>

          <div className="flex min-w-0 flex-col gap-6">
            <ProfileSection id="privacy" title={t("privacy.title")}>
              <RemoteBody remote={privacy}>
                {(data) => <PrivacySection initial={data} onRefresh={privacy.retry} />}
              </RemoteBody>
            </ProfileSection>

            <ProfileSection id="consents" title={t("consents.title")}>
              <RemoteBody remote={consents}>
                {(data) => <ConsentsSection initial={data} onRefresh={consents.retry} />}
              </RemoteBody>
            </ProfileSection>

            <ProfileSection id="preferences" title={t("preferences.title")}>
              <RemoteBody remote={preferences}>
                {(data) => <PreferencesSection initial={data} onRefresh={preferences.retry} />}
              </RemoteBody>
            </ProfileSection>

            <ProfileSection id="steam" title={t("steam.title")}>
              <RemoteBody remote={steam}>
                {(data) => <SteamSection initial={data} onRefresh={steam.retry} />}
              </RemoteBody>
            </ProfileSection>

            <ProfileSection id="security" title={t("security.title")}>
              <AccountSecuritySection />
            </ProfileSection>

            <ProfileSection id="age" title={t("age.title")}>
              <AccountAgeSection />
            </ProfileSection>

            <ProfileSection id="deletion" tone="danger" title={t("deletion.title")}>
              <AccountDeletionSection />
            </ProfileSection>
          </div>
        </div>
      </div>
    </PageContainer>
  );
}
