"use client";

import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { ApiHttpError } from "@/shared/lib/api/client";
import { CardSnake } from "@/shared/components/ui/card-snake";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { MAX_FAVORITES } from "../catalog/games";
import { useCatalogLabels } from "../hooks/use-catalog-labels";
import { useMyProfile } from "../hooks/use-my-profile";
import { removeMyImage, updateMyProfile, uploadMyImage } from "../services/profile-api";
import { emitProfileChanged } from "../services/profile-events";
import type { ImageKind, MyProfile, PlayerType } from "../types/profile.types";
import { CountrySelect } from "./country-select";
import { EDIT_SECTIONS, type EditSection } from "./edit-sections";
import { OptionPicker } from "./option-picker";
import { PlayerTypePicker } from "./player-type-picker";
import { ProfileAvatar } from "./profile-avatar";
import { ProfileHeader } from "./profile-header";
import { ProfileStateNotice } from "./profile-state-notice";

const MAX_BYTES: Record<ImageKind, number> = { avatar: 2 * 1024 * 1024, cover: 4 * 1024 * 1024 };
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif"];

const inputClass =
  "min-h-11 w-full rounded-control border border-control-border bg-surface px-3 text-text-primary placeholder:text-text-secondary";
const buttonClass =
  "inline-flex min-h-10 items-center justify-center rounded-control border border-control-border px-4 text-sm font-semibold transition-colors duration-150 hover:bg-surface-elevated aria-disabled:opacity-60";

/** Hata kodunu kullanıcı mesajı anahtarına çevirir; teknik ayrıntı gösterilmez. */
function errorKey(error: unknown): string {
  if (!(error instanceof ApiHttpError)) {
    return "generic";
  }
  if (error.status === 0) return "network";
  if (error.code === "CONCURRENT_MODIFICATION") return "conflict";
  if (error.status === 413) return "tooLarge";
  if (error.status === 415) return "unsupported";
  if (error.status === 429) return "rateLimited";
  if (error.status === 503) return "busy";
  if (error.code === "INVALID_IMAGE" || error.code === "INVALID_IMAGE_DIMENSIONS") return "invalidImage";
  if (error.status === 400) return "invalid";
  return "generic";
}

/** Profil düzenleme: görseller, ad, biyografi, ülke, oyuncu tipi ve favori tür/platform etiketleri. */
export function ProfileEditForm() {
  const session = useRequireSession();
  const { state, retry } = useMyProfile(session.status === "authenticated");
  const t = useTranslations("Profile");

  if (session.status !== "authenticated" || state.status === "loading") {
    return (
      <PageContainer>
        <p role="status" className="text-text-secondary">
          {t("loading")}
        </p>
      </PageContainer>
    );
  }
  if (state.status === "error") {
    return (
      <PageContainer>
        <ProfileStateNotice title={t("errorTitle")} body={t("errorBody")} actionLabel={t("retry")} onAction={retry} />
      </PageContainer>
    );
  }
  return (
    <PageContainer>
      <EditFields initial={state.profile} />
    </PageContainer>
  );
}

type Draft = {
  displayName: string;
  bio: string;
  country: string;
  types: PlayerType[];
  genres: string[];
  platforms: string[];
};

type ChangedField = "displayName" | "bio" | "country" | "playerTypes" | "genres" | "platforms";

function draftOf(profile: MyProfile): Draft {
  return {
    displayName: profile.displayName ?? "",
    bio: profile.bio ?? "",
    country: profile.country ?? "",
    types: profile.playerTypes,
    genres: profile.favoriteGenres,
    platforms: profile.favoritePlatforms
  };
}

const sameSet = (a: readonly string[], b: readonly string[]) => a.length === b.length && a.every((item) => b.includes(item));

/** Kayıtlı profille taslak arasındaki farklı alanlar (boşluklar ve ülke büyük/küçük harfi sunucu gibi normalleştirilir). */
export function changedFields(saved: Draft, draft: Draft): ChangedField[] {
  const changed: ChangedField[] = [];
  if (draft.displayName.trim() !== saved.displayName) changed.push("displayName");
  if (draft.bio.trim() !== saved.bio) changed.push("bio");
  if (draft.country.trim().toUpperCase() !== saved.country.toUpperCase()) changed.push("country");
  if (!sameSet(draft.types, saved.types)) changed.push("playerTypes");
  if (!sameSet(draft.genres, saved.genres)) changed.push("genres");
  if (!sameSet(draft.platforms, saved.platforms)) changed.push("platforms");
  return changed;
}

type Dialog = "save" | "discard" | null;

function EditFields({ initial }: { initial: MyProfile }) {
  const t = useTranslations("Profile");
  const te = useTranslations("Profile.errors");
  const labels = useCatalogLabels();
  const router = useRouter();
  const ids = { name: useId(), bio: useId(), country: useId(), hint: useId() };
  const [profile, setProfile] = useState(initial);
  const [displayName, setDisplayName] = useState(initial.displayName ?? "");
  const [bio, setBio] = useState(initial.bio ?? "");
  const [country, setCountry] = useState(initial.country ?? "");
  const [types, setTypes] = useState<PlayerType[]>(initial.playerTypes);
  const [genres, setGenres] = useState(initial.favoriteGenres);
  const [platforms, setPlatforms] = useState(initial.favoritePlatforms);
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [message, setMessage] = useState<{ kind: "ok" | "error"; text: string } | null>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);

  const changed = changedFields(draftOf(profile), { displayName, bio, country, types, genres, platforms });
  const dirty = changed.length > 0;

  // Kaydedilmemiş değişiklikle sekme kapatılırken/yenilenirken tarayıcı uyarısı.
  useEffect(() => {
    if (!dirty) {
      return;
    }
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  /** Biçim hatası varsa bildirir ve onay penceresini hiç açmaz; yoksa onay penceresini açar. */
  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy || !dirty) return;
    const name = displayName.trim();
    if (name !== "" && (Array.from(name).length < 2 || Array.from(name).length > 32)) {
      setFieldError(t("errors.displayName"));
      return;
    }
    if (country.trim() !== "" && !/^[A-Za-z]{2}$/.test(country.trim())) {
      setFieldError(t("errors.country"));
      return;
    }
    setFieldError(null);
    setDialog("save");
  }

  async function save() {
    if (busy) return;
    setBusy(true);
    setMessage(null);
    try {
      const saved = await updateMyProfile({
        version: profile.version,
        displayName: displayName.trim(),
        bio: bio.trim(),
        country: country.trim().toUpperCase(),
        playerTypes: types,
        favoriteGenres: genres,
        favoritePlatforms: platforms
      });
      setProfile(saved);
      emitProfileChanged();
      // Sunucu yanıtı kesinleşmeden "kaydedildi" denmez; alanlar sunucunun normalleştirdiği değere çekilir.
      setDisplayName(saved.displayName ?? "");
      setBio(saved.bio ?? "");
      setCountry(saved.country ?? "");
      setTypes(saved.playerTypes);
      setGenres(saved.favoriteGenres);
      setPlatforms(saved.favoritePlatforms);
      setMessage({ kind: "ok", text: t("saved") });
    } catch (error) {
      setMessage({ kind: "error", text: te(errorKey(error)) });
    } finally {
      setBusy(false);
      setDialog(null);
    }
  }

  /** Geri/Vazgeç: değişiklik yoksa doğrudan çıkar, varsa önce onay ister. */
  function leave() {
    if (dirty) {
      setDialog("discard");
    } else {
      router.push("/profile");
    }
  }

  const previewName = displayName.trim() === "" ? null : displayName.trim();

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
      <div>
        <button type="button" onClick={leave} className="text-sm text-brand-text hover:underline">
          {t("back")}
        </button>
        <h1 className="mt-2 font-display text-2xl font-semibold sm:text-3xl">{t("editTitle")}</h1>
        <p className="mt-1 max-w-2xl text-text-secondary">{t("editDescription")}</p>
      </div>

      <section aria-label={t("preview")} className="flex flex-col gap-2">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="font-display text-base font-semibold">{t("preview")}</h2>
          <p className="text-xs text-text-secondary">{t("previewHint")}</p>
        </div>
        {/* Önizleme yalnız görseldir: başlığı ve bağlantıları yardımcı teknolojiye ikinci kez okutulmaz. */}
        <div aria-hidden="true" inert>
          <ProfileHeader compact profile={{ ...profile, displayName: previewName, memberSince: profile.memberSince }} />
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-[12rem_minmax(0,1fr)]">
        <nav aria-label={t("sectionsNav")} className="lg:sticky lg:top-24 lg:self-start">
          <ul className="flex gap-2 overflow-x-auto lg:flex-col lg:gap-1">
            {EDIT_SECTIONS.map((section) => (
              <li key={section} className="shrink-0">
                <a
                  href={`#${section}`}
                  className="inline-flex min-h-10 w-full items-center rounded-control border-l-2 border-transparent px-3 text-sm text-text-secondary transition-colors hover:border-primary hover:bg-surface-elevated hover:text-text-primary"
                >
                  {t(`sections.${section satisfies EditSection}`)}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="flex min-w-0 flex-col gap-6">
          <ImagesCard profile={profile} onChange={setProfile} />

          <section id="info" className="flex scroll-mt-24 flex-col gap-5 rounded-panel border border-border bg-surface p-5 sm:p-6">
            <h2 className="font-display text-base font-semibold">{t("info")}</h2>

            <Field label={t("displayName")} htmlFor={ids.name} hint={t("displayNameHint")}>
              <input
                id={ids.name}
                className={inputClass}
                value={displayName}
                maxLength={32}
                onChange={(event) => setDisplayName(event.target.value)}
                aria-describedby={fieldError ? ids.hint : undefined}
              />
            </Field>

            <Field label={t("bio")} htmlFor={ids.bio} hint={t("bioCount", { count: Array.from(bio).length })}>
              <textarea
                id={ids.bio}
                className={cn(inputClass, "min-h-28 py-2")}
                value={bio}
                maxLength={500}
                onChange={(event) => setBio(event.target.value)}
              />
            </Field>

            <Field label={t("country")} htmlFor={ids.country} hint={t("countryHint")}>
              <div className="max-w-sm">
                <CountrySelect id={ids.country} value={country} onChange={setCountry} />
              </div>
            </Field>
          </section>

          <section id="prefs" className="flex scroll-mt-24 flex-col gap-5 rounded-panel border border-border bg-surface p-5 sm:p-6">
            <h2 className="font-display text-base font-semibold">{t("sections.prefs")}</h2>
            <PlayerTypePicker value={types} onChange={setTypes} />
            <OptionPicker
              label={t("genres")}
              hint={t("genresHint")}
              options={labels.genreOptions}
              value={genres}
              onChange={setGenres}
              max={MAX_FAVORITES}
            />
            <OptionPicker
              label={t("platforms")}
              hint={t("platformsHint")}
              options={labels.platformOptions}
              groups={labels.platformGroups}
              value={platforms}
              onChange={setPlatforms}
              max={MAX_FAVORITES}
            />
          </section>
        </div>
      </div>

      <div aria-live="polite" className="min-h-6">
        {fieldError ? (
          <p id={ids.hint} role="alert" className="text-sm text-brand-text">
            {fieldError}
          </p>
        ) : null}
        {message ? (
          <p role={message.kind === "error" ? "alert" : "status"} className={cn("text-sm", message.kind === "ok" ? "text-success" : "text-brand-text")}>
            {message.text}
          </p>
        ) : null}
      </div>

      {/* Sabit kaydet çubuğu: sayfa uzun olsa da kaydet/vazgeç hep erişilebilir kalır. */}
      <div className="sticky bottom-4 z-10 flex flex-wrap items-center justify-between gap-3 rounded-panel border border-border bg-surface/95 px-4 py-3 shadow-popover backdrop-blur">
        <p className={cn("text-sm", dirty ? "text-text-primary" : "text-text-secondary")} aria-live="polite">
          {dirty ? (
            <>
              <span aria-hidden="true" className="mr-2 inline-block size-2 rounded-full bg-primary align-middle" />
              {t("unsaved")} <span className="text-text-secondary">· {t("unsavedCount", { count: changed.length })}</span>
            </>
          ) : null}
        </p>
        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={leave} className={buttonClass}>
            {t("cancel")}
          </button>
          <button
            type="submit"
            disabled={!dirty || busy}
            className="inline-flex min-h-10 items-center justify-center rounded-control bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary disabled:opacity-50"
          >
            {busy ? t("saving") : t("save")}
          </button>
        </div>
      </div>

      {dialog === "save" ? (
        <ConfirmDialog
          title={t("confirmSave.title")}
          description={t("confirmSave.body")}
          confirmLabel={busy ? t("saving") : t("confirmSave.confirm")}
          cancelLabel={t("confirmSave.cancel")}
          busy={busy}
          frame={<CardSnake initialDelayMs={250} />}
          onConfirm={() => void save()}
          onCancel={() => setDialog(null)}
        >
          <ul className="flex flex-wrap gap-2">
            {changed.map((field) => (
              <li key={field} className="rounded-full border border-border bg-surface-elevated px-3 py-1 text-xs font-medium">
                {t(`changed.${field}`)}
              </li>
            ))}
          </ul>
        </ConfirmDialog>
      ) : null}
      {dialog === "discard" ? (
        <ConfirmDialog
          tone="danger"
          title={t("confirmDiscard.title")}
          description={t("confirmDiscard.body")}
          confirmLabel={t("confirmDiscard.confirm")}
          cancelLabel={t("confirmDiscard.cancel")}
          frame={<CardSnake initialDelayMs={250} />}
          onConfirm={() => router.push("/profile")}
          onCancel={() => setDialog(null)}
        />
      ) : null}
    </form>
  );
}

function Field({ label, htmlFor, hint, children }: { label: string; htmlFor: string; hint: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium">
        {label}
      </label>
      {children}
      <p className="text-xs text-text-secondary">{hint}</p>
    </div>
  );
}

function ImagesCard({ profile, onChange }: { profile: MyProfile; onChange: (profile: MyProfile) => void }) {
  const t = useTranslations("Profile");
  const te = useTranslations("Profile.errors");
  const [busyKind, setBusyKind] = useState<ImageKind | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmKind, setConfirmKind] = useState<ImageKind | null>(null);
  const name = profile.displayName ?? profile.username ?? "";

  async function upload(kind: ImageKind, file: File | undefined) {
    if (!file || busyKind) return;
    setError(null);
    if (!IMAGE_TYPES.includes(file.type)) {
      setError(te("unsupported"));
      return;
    }
    if (file.size > MAX_BYTES[kind]) {
      setError(te("tooLarge"));
      return;
    }
    setBusyKind(kind);
    try {
      onChange(await uploadMyImage(kind, file));
      emitProfileChanged();
    } catch (failure) {
      setError(te(errorKey(failure)));
    } finally {
      setBusyKind(null);
    }
  }

  async function remove(kind: ImageKind) {
    if (busyKind) return;
    setError(null);
    setBusyKind(kind);
    try {
      await removeMyImage(kind);
      onChange({ ...profile, [kind]: null });
      emitProfileChanged();
    } catch (failure) {
      setError(te(errorKey(failure)));
    } finally {
      setBusyKind(null);
    }
  }

  return (
    <section id="images" className="scroll-mt-24 rounded-panel border border-border bg-surface p-5 sm:p-6">
      <h2 className="mb-4 font-display text-base font-semibold">{t("images")}</h2>
      <div className="h-32 overflow-hidden rounded-control bg-surface-elevated sm:h-44">
        {profile.cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={profile.cover.small} alt="" className="size-full object-cover" referrerPolicy="no-referrer" />
        ) : null}
      </div>
      <ImageButtons
        kind="cover"
        hasImage={profile.cover !== null}
        busy={busyKind === "cover"}
        disabled={busyKind !== null}
        onPick={(file) => void upload("cover", file)}
        onRemove={() => setConfirmKind("cover")}
      />
      <div className="mt-5 flex items-center gap-4">
        <ProfileAvatar name={name} src={profile.avatar?.small ?? null} className="size-20 shrink-0" />
        <ImageButtons
          kind="avatar"
          hasImage={profile.avatar !== null}
          busy={busyKind === "avatar"}
          disabled={busyKind !== null}
          onPick={(file) => void upload("avatar", file)}
          onRemove={() => setConfirmKind("avatar")}
        />
      </div>
      <p className="mt-3 text-xs text-text-secondary">{t("imageHint")}</p>
      <div aria-live="polite">
        {error ? (
          <p role="alert" className="mt-2 text-sm text-brand-text">
            {error}
          </p>
        ) : null}
      </div>
      {confirmKind ? (
        <ConfirmDialog
          tone="danger"
          title={t(`confirmRemove.${confirmKind}`)}
          description={t("confirmRemove.body")}
          confirmLabel={t("confirmRemove.confirm")}
          cancelLabel={t("confirmRemove.cancel")}
          busy={busyKind !== null}
          frame={<CardSnake initialDelayMs={250} />}
          onConfirm={() => void remove(confirmKind).finally(() => setConfirmKind(null))}
          onCancel={() => setConfirmKind(null)}
        />
      ) : null}
    </section>
  );
}

function ImageButtons({
  kind,
  hasImage,
  busy,
  disabled,
  onPick,
  onRemove
}: {
  kind: ImageKind;
  hasImage: boolean;
  busy: boolean;
  disabled: boolean;
  onPick: (file: File | undefined) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("Profile");
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      <input
        ref={inputRef}
        type="file"
        accept={IMAGE_TYPES.join(",")}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(event) => {
          onPick(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
      <button type="button" aria-disabled={disabled} onClick={() => !disabled && inputRef.current?.click()} className={buttonClass}>
        {busy ? t("uploading") : t(kind === "cover" ? "changeCover" : "changeAvatar")}
      </button>
      {hasImage ? (
        <button type="button" aria-disabled={disabled} onClick={() => !disabled && onRemove()} className={buttonClass}>
          {t("remove")}
        </button>
      ) : null}
    </div>
  );
}
