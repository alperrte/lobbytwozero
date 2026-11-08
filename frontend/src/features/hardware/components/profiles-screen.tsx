"use client";
import { useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { hardwareApi } from "../services/hardware-api";
import { saveWorkshop } from "../services/workshop-save";
import { useHardwareMutation, useHardwareQuery } from "../hooks/use-hardware-query";
import { buildFromDraft, draftFromProfile, isCompleteBuild, missingSlots, slotComponents, type WorkshopDraft, type WorkshopSlot } from "../schemas/workshop";
import type { Product, Profile, ProfileDetail } from "../types/hardware";
import { ProfileWorkspace } from "./profile-workspace";
import { ProfileComparison } from "./profile-comparison";
import { WorkshopBuilder } from "./workshop-builder";
import { WorkshopPartPicker } from "./workshop-part-picker";
import { WorkshopStage } from "./workshop-stage";
import { WorkshopSummary } from "./workshop-summary";
import { panel, primaryButton, HardwareError, HardwareHeading, HardwareLoading, HardwarePagination } from "./hardware-ui";

const NEW = "new";
type Snapshot = { id: string; detail: ProfileDetail; products?: Record<string, Product | undefined> };
const emptyDraft: WorkshopDraft = { name: "", caseColor: "BLACK", slots: {} };

/** Bilgisayarlarım: LTZ Atölyesi (3D montaj sahnesi, kurulum formu, Bilgisayar Bilgilerim) ve Gelişmiş yönetim. */
export function ProfilesScreen() {
  const t = useTranslations("Hardware");
  const [page, setPage] = useState(0);
  const [active, setActive] = useState<string>();
  const [compared, setCompared] = useState<Profile[]>([]);
  const [edit, setEdit] = useState<{ key: string; draft: WorkshopDraft }>();
  const [picking, setPicking] = useState<WorkshopSlot>();
  const [replay, setReplay] = useState(0);
  const [savedKey, setSavedKey] = useState<string>();
  const nameRef = useRef<HTMLInputElement>(null);
  const builderRef = useRef<HTMLDivElement>(null);

  const profiles = useHardwareQuery(`profiles:${page}`, (signal) => hardwareApi.profiles(page, signal));
  const statistics = useHardwareQuery("statistics", (signal) => hardwareApi.statistics(signal));
  // Son yüklenen liste: kayıt sonrası yeniden yüklemede Gelişmiş yönetim ve açık diyaloglar kapanmaz.
  const [lastProfiles, setLastProfiles] = useState(profiles.data);
  if (profiles.data && profiles.data !== lastProfiles) setLastProfiles(profiles.data);
  const profileList = profiles.data ?? (profiles.error || lastProfiles?.page !== page ? undefined : lastProfiles);
  const items = profileList?.items ?? [];
  const activeId = profiles.error ? undefined : active ?? (profileList ? items[0]?.id ?? NEW : undefined);
  const profileId = activeId && activeId !== NEW ? activeId : undefined;

  const detail = useHardwareQuery(`workshop:${profileId}`, (signal) => hardwareApi.profile(profileId!, 0, signal), Boolean(profileId));
  const productIds = [...new Set(Object.values(slotComponents(detail.data?.components ?? [])).flatMap((component) => component.productId ? [component.productId] : []))].sort();
  const products = useHardwareQuery(`workshop-products:${productIds.join(",")}`, async (signal) => {
    const loaded = await Promise.all(productIds.map((id) => hardwareApi.product(id, signal).catch(() => undefined)));
    return Object.fromEntries(productIds.map((id, index) => [id, loaded[index]])) as Record<string, Product | undefined>;
  }, Boolean(detail.data) && productIds.length > 0);

  // Son tam yüklenen hâl: yeniden yükleme sırasında sahne ve form yerinde kalır (3D sahne baştan kurulmaz, montaj kesilmez).
  const [snapshot, setSnapshot] = useState<Snapshot>();
  const productsLoaded = productIds.length === 0 || products.data !== undefined;
  const loadedProducts = productIds.length ? products.data : undefined;
  if (profileId && detail.data && productsLoaded && (snapshot?.detail !== detail.data || snapshot.products !== loadedProducts)) {
    setSnapshot({ id: profileId, detail: detail.data, products: loadedProducts });
  }
  const view = profileId && snapshot?.id === profileId ? snapshot : undefined;
  const profile = view?.detail.profile;
  const components = view?.detail.components ?? [];
  const initial = profile ? draftFromProfile(profile, components, view?.products ?? {}) : emptyDraft;
  // Sunucudaki hâl değişince (yeni sürüm) yerel taslak bırakılır; kayıt bitene kadar taslak korunur.
  const sourceKey = `${activeId}:${profile?.version ?? 0}`;
  // Yarım kalan ilk kayıtta seçilen parçalar, yeni profil sunucudan okununca taslak olarak geri konur.
  const [carry, setCarry] = useState<{ id: string; draft: WorkshopDraft }>();
  if (carry && profile?.id === carry.id) { setCarry(undefined); setEdit({ key: sourceKey, draft: carry.draft }); }
  const draft = edit?.key === sourceKey ? edit.draft : initial;
  const build = buildFromDraft(draft);
  const missing = missingSlots(draft);
  const ready = activeId === NEW || Boolean(view);

  const mutation = useHardwareMutation(() => { profiles.reload(); statistics.reload(); detail.reload(); });

  function setDraft(next: WorkshopDraft) { setEdit({ key: sourceKey, draft: next }); setSavedKey(undefined); }
  function refresh(next?: Profile) { profiles.reload(); statistics.reload(); detail.reload(); setCompared([]); if (next) setActive(next.id); }
  /** Silinen bilgisayar, liste yeniden yüklenirken bir an bile seçili kalmaz. */
  function removed(id: string) {
    if (lastProfiles) setLastProfiles({ ...lastProfiles, items: lastProfiles.items.filter((item) => item.id !== id) });
    setActive(undefined); setPage(0); setEdit(undefined); refresh();
  }
  function selectProfile(id: string) { setActive(id); setEdit(undefined); setSavedKey(undefined); }
  function focusBuilder() {
    builderRef.current?.scrollIntoView({ block: "start", behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
    nameRef.current?.focus({ preventScroll: true });
  }
  function startNew() { selectProfile(NEW); requestAnimationFrame(focusBuilder); }
  /**
   * Sunucunun oluşturduğu profil hemen listeye ve seçime alınır; liste her durumda sunucudan yeniden okunur. Parça kaydı
   * reddedildiyse (ör. 400/422, mutasyon kancası bu durumda yeniden yüklemez) taslak yeni profile taşınır: seçilen parçalar
   * kaybolmaz, yeniden denemede yalnız kaydedilemeyenler gider. Başarı mesajı yalnız tam kayıtta gösterilir.
   */
  function adopt(created: Profile, unsaved?: WorkshopDraft) {
    profiles.reload(); statistics.reload();
    setLastProfiles((list) => list && !list.items.some((item) => item.id === created.id) && list.items.length < list.size
      ? { ...list, items: [...list.items, created], totalElements: list.totalElements + 1, totalPages: Math.max(1, list.totalPages) } : list);
    setActive(created.id);
    setCarry(unsaved ? { id: created.id, draft: unsaved } : undefined);
  }
  async function save() {
    // Yeni profil, bütün parçalar yazıldıktan sonra açılır (yarım kurulum gösterilmez); parça kaydı başarısız olsa da
    // oluşturulan profile geçilir ki aynı bilgisayar ikinci kez oluşturulmasın.
    let created: Profile | undefined;
    const result = await mutation.run(() => saveWorkshop({ profile, draft, saved: slotComponents(components), onProfile: (next) => { if (!profile) created = next; } }));
    if (created) adopt(created, result ? undefined : draft);
    if (!result) return;
    setSavedKey(result.profile.id);
    // Yeni bilgisayarda sahne yeni profil için kurulur ve kendiliğinden oynatır; düzenlemede aynı sahne baştan oynatır.
    if (profile) setReplay((value) => value + 1);
  }

  return <>
    <HardwareHeading title={t("profiles.title")} description={t("workshop.concept")} />

    {profiles.loading && !profileList ? <HardwareLoading /> : null}
    <HardwareError error={profiles.error} retry={() => { profiles.reload(); statistics.reload(); }} />

    {activeId ? <div className="space-y-6">
      {items.length > 0 ? <nav aria-label={t("workshop.switcher")} className="flex flex-wrap items-center gap-2">
        {items.map((item) => <button key={item.id} type="button" aria-pressed={activeId === item.id} onClick={() => selectProfile(item.id)}
          className={`min-h-11 max-w-full truncate rounded-xl border px-4 text-sm font-semibold transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus ${activeId === item.id ? "border-primary bg-primary/5" : "border-border bg-surface"}`}>{item.name}</button>)}
        <button type="button" aria-pressed={activeId === NEW} onClick={startNew}
          className={`min-h-11 rounded-xl border border-dashed px-4 text-sm font-semibold text-text-secondary transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus ${activeId === NEW ? "border-primary" : "border-control-border"}`}>{t("workshop.newComputer")}</button>
        {profileList ? <HardwarePagination page={page} totalPages={profileList.totalPages} onPage={(next) => { setPage(next); setActive(undefined); }} /> : null}
      </nav> : null}

      {/* İlk kurulum: henüz kayıtlı bilgisayar yokken oluşturma eylemi ana akışta, formun hemen üstündedir. Profil
          oluşturulduğu anda (kayıt yarım kalsa da) kaybolur; liste yeniden yüklenirken geri gelmez. */}
      {items.length === 0 && profileList && !profileId ? <section data-testid="workshop-first-run" aria-labelledby="workshop-first-run-title"
        className={`${panel} flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between`}>
        <div className="flex min-w-0 items-start gap-4">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-panel border border-border bg-surface-elevated">
            <Icon name="desktop" className="size-5 text-text-secondary" />
          </span>
          <div className="min-w-0">
            <h2 id="workshop-first-run-title" className="font-display text-lg font-semibold">{t("profiles.emptyTitle")}</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-text-secondary">{t("profiles.emptyDescription")}</p>
          </div>
        </div>
        <button type="button" className={`${primaryButton} shrink-0`} onClick={focusBuilder}><Icon name="desktop" className="size-4" />{t("workshop.firstRunAction")}</button>
      </section> : null}

      {profileId ? <HardwareError error={detail.error} retry={detail.reload} /> : null}
      {!ready && !detail.error ? <HardwareLoading /> : null}

      {ready ? <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <WorkshopStage build={build} replayToken={replay} profileKey={activeId} missing={missing} onPickParts={() => { if (missing[0]) setPicking(missing[0]); else focusBuilder(); }} />
        <div ref={builderRef} className="scroll-mt-header-offset lg:sticky lg:top-header-offset">
          <WorkshopBuilder mode={profile ? "edit" : "create"} draft={draft} initial={initial} onChange={setDraft} onPick={setPicking} onSave={() => void save()}
            onReset={() => setEdit(undefined)} pending={mutation.pending} error={mutation.error} saved={Boolean(savedKey) && savedKey === profile?.id} nameRef={nameRef} />
        </div>
      </div> : null}

      {profile && view ? <WorkshopSummary profile={profile} components={components} saved={initial} totalComponents={view.detail.totalComponents}
        complete={isCompleteBuild(buildFromDraft(initial))} onPick={setPicking} onEdit={focusBuilder} onReplay={() => setReplay((value) => value + 1)}
        onChanged={() => refresh()} onDeleted={() => removed(profile.id)} /> : null}

      {items.length > 0 ? <details className={`${panel} group p-5`} data-testid="workshop-advanced">
        <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 font-display font-semibold">
          <span>{t("workshop.advanced.title")}<span className="mt-0.5 block text-sm font-normal text-text-secondary">{t("workshop.advanced.description")}</span></span>
          <Icon name="chevron" className="size-5 shrink-0 transition-transform group-open:rotate-180" />
        </summary>
        <div className="mt-5 grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_15rem]">
          <div className="min-w-0 space-y-5">
            <div className="grid gap-3 sm:grid-cols-2">{items.map((item) => <article key={item.id} className={`rounded-panel border p-4 ${activeId === item.id ? "border-primary" : "border-border"}`}>
              <p className="text-xs text-text-secondary">{t(`devices.${item.deviceType}`)}</p>
              <p className="mt-1 break-words font-display text-lg font-semibold">{item.name}</p>
              <label className="mt-3 flex min-h-11 items-center gap-2 text-xs text-text-secondary">
                <input type="checkbox" checked={compared.some((entry) => entry.id === item.id)} disabled={compared.length === 2 && !compared.some((entry) => entry.id === item.id)} onChange={(event) => setCompared(event.target.checked ? [...compared, item] : compared.filter((entry) => entry.id !== item.id))} />
                {t("comparison.add")}
              </label>
            </article>)}</div>
            {compared.length ? <ProfileComparison profiles={compared} clear={() => setCompared([])} /> : null}
            {profileId ? <ProfileWorkspace key={profileId} profileId={profileId} onChanged={refresh} onDeleted={() => removed(profileId)} /> : null}
          </div>
          <section className="rounded-panel border border-border p-5">
            <h2 className="font-display text-lg font-semibold">{t("stats.title")}</h2>
            {statistics.loading ? <p role="status" className="mt-3 text-sm text-text-secondary">{t("common.loading")}</p> : null}
            <HardwareError error={statistics.error} retry={statistics.reload} />
            {statistics.data ? <dl className="mt-4 divide-y divide-border">
              {[["profiles", statistics.data.totalProfiles], ["entries", statistics.data.totalComponentEntries], ["units", statistics.data.totalComponentUnits]].map(([key, value]) => <div key={key} className="flex items-center justify-between gap-2 py-3">
                <dt className="text-sm text-text-secondary">{t(`stats.${key}`)}</dt>
                <dd className="font-display text-xl font-semibold">{value}</dd>
              </div>)}
              {["ram", "storage"].map((key) => <div key={key} className="py-3">
                <dt className="text-sm text-text-secondary">{t(`stats.${key}`)}</dt>
                <dd className="mt-1 font-display text-xl font-semibold">{t("stats.capacity", { value: key === "ram" ? statistics.data!.recordedRamCapacityGb : statistics.data!.recordedStorageCapacityGb })}</dd>
                <dd className="mt-1 text-xs text-text-secondary">{t("stats.unknownUnits", { count: key === "ram" ? statistics.data!.ramUnitsWithoutCapacity : statistics.data!.storageUnitsWithoutCapacity })}</dd>
              </div>)}
            </dl> : null}
            <div className="mt-4 border-t border-border pt-4">
              <h3 className="font-display text-sm font-semibold">{t("profiles.syncTitle")}</h3>
              <p className="mt-1 text-xs leading-5 text-text-secondary">{t("profiles.syncDescription")}</p>
            </div>
          </section>
        </div>
      </details> : null}
    </div> : null}

    {picking ? <WorkshopPartPicker slot={picking} selectedId={draft.slots[picking]?.productId} onClose={() => setPicking(undefined)}
      onSelect={(product) => { setDraft({ ...draft, slots: { ...draft.slots, [picking]: { productId: product.id, product, quantity: draft.slots[picking]?.quantity ?? 1 } } }); setPicking(undefined); }} /> : null}
  </>;
}
