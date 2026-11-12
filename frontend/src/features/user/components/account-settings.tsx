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

        <ProfileSection title={t("privacy.title")}>
          <RemoteBody remote={privacy}>
            {(data) => <PrivacySection initial={data} onRefresh={privacy.retry} />}
          </RemoteBody>
        </ProfileSection>

        <ProfileSection title={t("preferences.title")}>
          <RemoteBody remote={preferences}>
            {(data) => <PreferencesSection initial={data} onRefresh={preferences.retry} />}
          </RemoteBody>
        </ProfileSection>

        <ProfileSection title={t("steam.title")}>
          <RemoteBody remote={steam}>
            {(data) => <SteamSection initial={data} onRefresh={steam.retry} />}
          </RemoteBody>
        </ProfileSection>

        <ProfileSection title={t("consents.title")}>
          <RemoteBody remote={consents}>
            {(data) => <ConsentsSection initial={data} onRefresh={consents.retry} />}
          </RemoteBody>
        </ProfileSection>

        <ProfileSection title={t("security.title")}>
          <AccountSecuritySection />
        </ProfileSection>

        <ProfileSection title={t("age.title")}>
          <AccountAgeSection />
        </ProfileSection>

        <ProfileSection title={t("deletion.title")}>
          <AccountDeletionSection />
        </ProfileSection>
      </div>
    </PageContainer>
  );
}
