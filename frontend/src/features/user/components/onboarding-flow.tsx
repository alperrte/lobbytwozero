"use client";

import { useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { ApiHttpError } from "@/shared/lib/api/client";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { MAX_FAVORITES } from "../catalog/games";
import { useCatalogLabels } from "../hooks/use-catalog-labels";
import { useMyProfile } from "../hooks/use-my-profile";
import { useRemote } from "../hooks/use-remote";
import { completeOnboarding, getOnboarding, type OnboardingState } from "../services/onboarding-api";
import { emitProfileChanged } from "../services/profile-events";
import type { MyProfile, PlayerType } from "../types/profile.types";
import { OptionPicker } from "./option-picker";
import { PlayerTypePicker } from "./player-type-picker";
import { RemoteBody, sectionLinkClass } from "./remote-section";

/** Hata kodunu kullanıcı mesajı anahtarına çevirir; teknik ayrıntı gösterilmez. */
function errorKey(error: unknown): string {
  if (!(error instanceof ApiHttpError)) return "generic";
  if (error.status === 0) return "network";
  if (error.status === 409) return "conflict";
  if (error.status === 429) return "rateLimited";
  if (error.status === 503) return "busy";
  if (error.code === "ONBOARDING_INCOMPLETE") return "incomplete";
  if (error.status === 400) return "invalid";
  return "generic";
}

/**
 * İlk kurulum (USER-09): en az bir favori tür ve bir platform seçtirir, oyuncu tipi isteğe bağlıdır. Kurulum tamamlandıysa
 * form yerine bilgi gösterilir; seçimleri sonradan değiştirmek profil düzenlemenin işidir. Mevcut profil seçimleri forma
 * önceden doldurulur, böylece yalnız eksik olan adım için gelen kullanıcı doğru yazılmış alanlarını kaybetmez.
 */
export function OnboardingFlow() {
  const session = useRequireSession();
  const enabled = session.status === "authenticated";
  const t = useTranslations("Onboarding");
  const onboarding = useRemote(enabled ? "onboarding" : null, getOnboarding);
  const { state: profileState } = useMyProfile(enabled);

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="font-display text-2xl font-semibold sm:text-3xl">{t("title")}</h1>
          <p className="mt-1 max-w-2xl text-text-secondary">{t("description")}</p>
        </div>
        {!enabled || profileState.status === "loading" ? (
          <p role="status" className="text-text-secondary">
            {t("loading")}
          </p>
        ) : profileState.status === "error" ? (
          <p role="alert" className="text-sm text-text-secondary">
            {t("errors.generic")}
          </p>
        ) : (
          <RemoteBody remote={onboarding}>
            {(status) => (status.completed ? <Done /> : <OnboardingForm status={status} profile={profileState.profile} />)}
          </RemoteBody>
        )}
      </div>
    </PageContainer>
  );
}

function Done() {
  const t = useTranslations("Onboarding");
  return (
    <section className="max-w-prose rounded-panel border border-border bg-surface p-5 sm:p-6">
      <h2 className="font-display text-lg font-semibold">{t("doneTitle")}</h2>
      <p className="mt-2 text-text-secondary">{t("doneBody")}</p>
      <div className="mt-4 flex flex-wrap gap-3">
        <Link href="/profile" className={sectionLinkClass}>
          {t("toProfile")}
        </Link>
        <Link href="/profile/edit" className={sectionLinkClass}>
          {t("editProfile")}
        </Link>
      </div>
    </section>
  );
}

function OnboardingForm({ status, profile }: { status: OnboardingState; profile: MyProfile }) {
  const t = useTranslations("Onboarding");
  const te = useTranslations("Onboarding.errors");
  const tp = useTranslations("Profile");
  const labels = useCatalogLabels();
  const router = useRouter();
  const [types, setTypes] = useState<PlayerType[]>(profile.playerTypes);
  const [genres, setGenres] = useState(profile.favoriteGenres);
  const [platforms, setPlatforms] = useState(profile.favoritePlatforms);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tried, setTried] = useState(false);

  const missingGenres = genres.length === 0;
  const missingPlatforms = platforms.length === 0;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setTried(true);
    if (missingGenres || missingPlatforms) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await completeOnboarding({
        version: status.version,
        playerTypes: types,
        favoriteGenres: genres,
        favoritePlatforms: platforms
      });
      emitProfileChanged();
      // Sunucu yanıtı kesinleşmeden profile geçilmez.
      router.push("/profile");
    } catch (failure) {
      setError(te(errorKey(failure)));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
      <section className="flex flex-col gap-5 rounded-panel border border-border bg-surface p-5 sm:p-6">
        <PlayerTypePicker value={types} onChange={setTypes} />
        <OptionPicker
          label={tp("genres")}
          hint={tp("genresHint")}
          options={labels.genreOptions}
          value={genres}
          onChange={setGenres}
          max={MAX_FAVORITES}
        />
        {tried && missingGenres ? (
          <p role="alert" className="-mt-3 text-sm text-brand-text">
            {t("needGenre")}
          </p>
        ) : null}
        <OptionPicker
          label={tp("platforms")}
          hint={tp("platformsHint")}
          options={labels.platformOptions}
          groups={labels.platformGroups}
          value={platforms}
          onChange={setPlatforms}
          max={MAX_FAVORITES}
        />
        {tried && missingPlatforms ? (
          <p role="alert" className="-mt-3 text-sm text-brand-text">
            {t("needPlatform")}
          </p>
        ) : null}
      </section>

      <div aria-live="polite" className="min-h-6">
        {error ? (
          <p role="alert" className="text-sm text-brand-text">
            {error}
          </p>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-3">
        <button
          type="submit"
          aria-disabled={busy}
          className="inline-flex min-h-10 items-center justify-center rounded-control bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary aria-disabled:opacity-60"
        >
          {busy ? t("saving") : t("finish")}
        </button>
        <Link href="/profile" className={sectionLinkClass}>
          {t("later")}
        </Link>
      </div>
    </form>
  );
}
