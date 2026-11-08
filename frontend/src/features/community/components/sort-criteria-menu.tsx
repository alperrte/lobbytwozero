"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { Checkbox } from "@/shared/components/ui/checkbox";
import { Icon } from "@/shared/components/ui/icons";
import { usePopover } from "@/shared/hooks/use-popover";
import { cn } from "@/shared/utils/cn";
import type { PostSort } from "../types/community.types";
import { buttonClass, primaryButtonClass } from "./community-ui";

/**
 * Sıralama ölçütleri (sıra sabit); dördü de işaretlenebilir. `supported`: SUNUCUNUN sıralayabildiği ölçüt. API tek bir sıralama
 * anahtarı alır (`NEWEST` | `OLDEST` | `POPULAR`); "en fazla beğeni" ve "en fazla yeniden paylaşım" için sıralama (ve yeniden
 * paylaşım sayacı) yoktur. Bu ölçütler işaretlenebilir ama "Uygula" bunları uygulayamaz: seçim korunur, menü açık kalır ve
 * nedeni açıkça yazılır (çalışmayan sıralama çalışıyormuş gibi sunulmaz, yüklenmiş kayıtlar istemcide sıralanmaz).
 * Backend `MOST_LIKED` / `MOST_REPOSTED` desteği verince ilgili `supported` değerleri `true` yapılır.
 */
const CRITERIA = [
  { value: "NEWEST", supported: true },
  { value: "OLDEST", supported: true },
  { value: "MOST_LIKED", supported: false },
  { value: "MOST_REPOSTED", supported: false }
] as const;
type Criterion = (typeof CRITERIA)[number]["value"];

const DEFAULT = "NEWEST" as const;
const isSupported = (value: string): value is "NEWEST" | "OLDEST" => value === "NEWEST" || value === "OLDEST";

/**
 * "Filtrele" çoklu seçim menüsü. Tetikleyici DAİMA "Filtrele" yazar (seçim başlığın yerine geçmez); seçim sayısı küçük bir
 * rozetle gösterilir. Menüde her ölçütün yanında onay kutusu vardır, seçim menüyü kapatmaz; seçili ölçüte tekrar basmak seçimi
 * kaldırır. Seçim SIRASI önemlidir ve küçük sıra numaralarıyla gösterilir: ilk seçilen ana sıralama, sonrakiler eşitlikte kullanılan
 * ikincil ölçütlerdir. "En yeni" ve "En eski" zıt yönlerdir; ikisi seçilirse önce seçilen uygulanır, diğeri sonucu değiştirmez ve
 * bu menüde açıkça belirtilir. "Uygula" bütün seçimi birlikte uygular (menü kapanır, seçim korunur); "Temizle" tüm seçimleri ve
 * sıra numaralarını sıfırlar (seçim yokken sıralama En yeni'dir) ve uygular.
 *
 * Sunucu tek sıralama anahtarı kabul ettiği için gönderilen anahtar ilk seçilen ölçüttür (`onApply`); liste ve sayfalama bu
 * değerle baştan yüklenir.
 */
export function SortCriteriaMenu({ value, onApply }: { value: PostSort; onApply: (sort: "NEWEST" | "OLDEST") => void }) {
  const t = useTranslations("Community.filters");
  const id = useId();
  const { open, setOpen, close, rootRef, triggerRef } = usePopover();
  const [applied, setApplied] = useState<Criterion[]>([DEFAULT]);
  const [draft, setDraft] = useState<Criterion[]>([DEFAULT]);
  const [unsupported, setUnsupported] = useState<Criterion[]>([]);

  // Dışarıdan değişen sıralama (ör. adres/durum sıfırlama) menüdeki uygulanmış seçime yansır.
  const primary = applied.find(isSupported) ?? DEFAULT;
  if (isSupported(value) && value !== primary) {
    setApplied([value]);
    setDraft([value]);
  }

  const first = draft.find(isSupported);
  const opposite = draft.includes("NEWEST") && draft.includes("OLDEST");
  const changed = applied.length > 0 && (applied.length !== 1 || applied[0] !== DEFAULT);

  function toggleOpen() {
    if (!open) setDraft(applied);
    setUnsupported([]);
    setOpen(!open);
  }

  function toggle(criterion: Criterion) {
    setUnsupported([]);
    setDraft((current) => (current.includes(criterion) ? current.filter((item) => item !== criterion) : [...current, criterion]));
  }

  function apply(next: Criterion[]) {
    // Sunucunun sıralayamadığı ölçüt varsa hiçbir şey uygulanmaz; seçim korunur ve neden gösterilir.
    const missing = next.filter((criterion) => !CRITERIA.some((item) => item.value === criterion && item.supported));
    if (missing.length > 0) {
      setUnsupported(missing);
      return;
    }
    setUnsupported([]);
    setApplied(next);
    setDraft(next);
    onApply(next.find(isSupported) ?? DEFAULT);
    close(true);
  }

  function clear() {
    setUnsupported([]);
    setApplied([]);
    setDraft([]);
    onApply(DEFAULT);
    close(true);
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        className={cn(buttonClass, "w-full justify-between gap-2 sm:w-auto")}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={`${id}-panel`}
        onClick={toggleOpen}
      >
        <span>{t("sortFilter")}</span>
        {changed ? (
          <span className="rounded-full bg-primary px-1.5 text-[11px] font-semibold leading-5 text-primary-foreground">
            <span aria-hidden="true">{applied.length}</span>
            <span className="sr-only">{t("criteriaCount", { count: applied.length })}</span>
          </span>
        ) : null}
        <Icon name="chevron" className={cn("size-4 shrink-0 text-text-secondary transition-transform duration-150", open && "rotate-180")} />
      </button>

      {open ? (
        <div
          id={`${id}-panel`}
          role="group"
          aria-label={t("sortFilter")}
          // Panel odaklanabilirdir (tabIndex -1): etiket/boşluk gibi odaklanamayan yere basınca odak <main>'e kaçmaz; aksi halde
          // `usePopover` odak dışarı çıktı sanıp menüyü kapatır ve çoklu seçim ikinci tıklamada bozulur.
          tabIndex={-1}
          className="absolute start-0 top-full z-30 mt-1 w-[min(21rem,calc(100vw-3rem))] rounded-panel border border-border bg-surface p-3 shadow-popover focus:outline-none sm:start-auto sm:end-0"
        >
          <ul className="space-y-1">
            {CRITERIA.map(({ value: criterion, supported }) => {
              const position = draft.indexOf(criterion);
              const ineffective = opposite && supported && criterion !== first && position >= 0;
              return (
                <li key={criterion} className="flex min-h-11 items-center gap-2 rounded-control px-1">
                  <Checkbox
                    className="min-w-0 flex-1"
                    label={t(`sort.${criterion}`)}
                    hint={ineffective ? t("ineffective") : undefined}
                    checked={position >= 0}
                    onChange={() => toggle(criterion)}
                  />
                  {position >= 0 ? (
                    <span
                      className={cn(
                        "inline-flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold tabular-nums",
                        position === 0 ? "border-primary bg-primary text-primary-foreground" : "border-control-border text-text-secondary"
                      )}
                    >
                      <span aria-hidden="true">{position + 1}</span>
                      <span className="sr-only">{t("criteriaOrder", { number: position + 1 })}</span>
                    </span>
                  ) : null}
                </li>
              );
            })}
          </ul>
          {opposite && first ? (
            <p role="status" className="mt-2 rounded-control border border-warning/60 bg-warning/10 px-3 py-2 text-xs leading-5 text-text-primary">
              {t("criteriaOpposite", { first: t(`sort.${first}`) })}
            </p>
          ) : null}
          {unsupported.length > 0 ? (
            <p role="alert" className="mt-2 rounded-control border border-primary/60 bg-primary/10 px-3 py-2 text-xs leading-5 text-text-primary">
              {t("criteriaUnsupported", { list: unsupported.map((criterion) => t(`sort.${criterion}`)).join(", ") })}
            </p>
          ) : null}
          <div className="mt-3 flex justify-end gap-2">
            <button type="button" className={buttonClass} onClick={clear}>
              {t("clearCriteria")}
            </button>
            <button type="button" className={primaryButtonClass} onClick={() => apply(draft)}>
              {t("applyCriteria")}
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
