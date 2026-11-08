"use client";

import { useEffect, useId, useRef, useState, type Dispatch, type DragEvent, type KeyboardEvent, type SetStateAction } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { ACCEPTED_IMAGE_TYPES, LIMITS } from "../schemas/community.schema";
import { moveItem, prepareImages, releaseImage, type ImageRejection, type LocalImage } from "../schemas/image-validation";
import { CommunityIcon } from "./community-icons";
import { actionClass } from "./community-ui";

export type { LocalImage } from "../schemas/image-validation";
export { releaseImage as releaseLocalImage } from "../schemas/image-validation";

const smallButton =
  "inline-flex size-9 items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary disabled:cursor-not-allowed disabled:opacity-35 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-focus";

/**
 * Görsel seçici: çoklu seçim, sürükle-bırak, sonradan ekleme (önceki seçimler korunur), tek tek kaldırma ve sıralama.
 * Sıra, yükleme sırasını belirler: Community sunucusu görselleri geldikleri sırayla `sortOrder` alır. Sınırlar sunucuyla
 * aynıdır (4 görsel, 2 MiB, JPEG/PNG, ≤6000 px, ≤12 MP); geçersiz dosyalar adı ve nedeniyle bildirilir ve geçerli seçimleri
 * etkilemez. Sıralama fare (sürükle), dokunma/klavye (oklu düğmeler) ve Alt+Ok kısayoluyla yapılabilir.
 */
export function ImagePicker({
  items,
  setItems,
  remoteCount = 0,
  disabled = false,
  addDisabled = false,
  activeKey,
  onActivate
}: {
  items: LocalImage[];
  setItems: Dispatch<SetStateAction<LocalImage[]>>;
  /** Gönderide zaten yayınlanmış görsel sayısı (toplam sınıra dahildir). */
  remoteCount?: number;
  disabled?: boolean;
  /** Yalnız ekleme kapalı (kaldırma ve sıralama açık kalır). */
  addDisabled?: boolean;
  activeKey?: string | null;
  onActivate?: (key: string) => void;
}) {
  const t = useTranslations("Community.images");
  const inputId = useId();
  const hintId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const focusRequest = useRef<string | null>(null);
  const [rejections, setRejections] = useState<ImageRejection[]>([]);
  const [announcement, setAnnouncement] = useState("");
  const [dragKey, setDragKey] = useState<string | null>(null);
  const [overFiles, setOverFiles] = useState(false);
  const [processing, setProcessing] = useState(false);
  const total = items.length + remoteCount;
  const full = total >= LIMITS.imagesPerPost;
  const cannotAdd = disabled || addDisabled || full || processing;

  // Taşıma sonrası odak, taşınan görselin aynı düğmesinde kalır.
  useEffect(() => {
    if (!focusRequest.current) return;
    document.querySelector<HTMLElement>(`[data-focus-id="${focusRequest.current}"]`)?.focus();
    focusRequest.current = null;
  });

  async function addFiles(list: FileList | File[] | null) {
    const files = Array.from(list ?? []);
    if (files.length === 0 || cannotAdd) return;
    setProcessing(true);
    try {
      const { added, rejected } = await prepareImages(files, items, remoteCount);
      setRejections(rejected);
      if (added.length > 0) {
        setItems((current) => [...current, ...added]);
        onActivate?.(added[0]!.key);
        setAnnouncement(t("added", { count: added.length }));
      }
    } finally {
      setProcessing(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  function move(index: number, target: number, focusId: string) {
    const item = items[index];
    if (!item || target < 0 || target >= items.length) return;
    focusRequest.current = focusId;
    setItems((current) => moveItem(current, index, target));
    onActivate?.(item.key);
    setAnnouncement(t("moved", { name: item.file.name, position: target + 1, total: items.length }));
  }

  function remove(index: number) {
    const item = items[index];
    if (!item) return;
    releaseImage(item);
    const neighbour = items[index + 1] ?? items[index - 1];
    setItems((current) => current.filter((candidate) => candidate.key !== item.key));
    if (neighbour) onActivate?.(neighbour.key);
    setAnnouncement(t("removedAnnounce", { name: item.file.name }));
    focusRequest.current = neighbour ? `select-${neighbour.key}` : `add-${inputId}`;
  }

  function onThumbKeyDown(event: KeyboardEvent, index: number, key: string) {
    if (!event.altKey || (event.key !== "ArrowLeft" && event.key !== "ArrowRight")) return;
    event.preventDefault();
    move(index, index + (event.key === "ArrowLeft" ? -1 : 1), `select-${key}`);
  }

  function onZoneDrop(event: DragEvent) {
    event.preventDefault();
    setOverFiles(false);
    if (dragKey === null) void addFiles(event.dataTransfer.files);
  }

  function onThumbDrop(event: DragEvent, targetIndex: number) {
    event.preventDefault();
    event.stopPropagation();
    const from = items.findIndex((item) => item.key === dragKey);
    setDragKey(null);
    if (from >= 0 && from !== targetIndex) move(from, targetIndex, `select-${items[from]!.key}`);
  }

  return (
    <div>
      <div
        onDragOver={(event) => {
          if (dragKey === null && !cannotAdd) {
            event.preventDefault();
            setOverFiles(true);
          }
        }}
        onDragLeave={() => setOverFiles(false)}
        onDrop={onZoneDrop}
        className={cn(
          "relative flex flex-wrap items-center gap-x-4 gap-y-3 rounded-control border border-dashed p-4 transition-colors",
          overFiles ? "border-primary bg-primary/10" : "border-control-border bg-background/60",
          cannotAdd && "opacity-80"
        )}
      >
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          multiple
          accept={ACCEPTED_IMAGE_TYPES.join(",")}
          disabled={cannotAdd}
          aria-describedby={hintId}
          onChange={(event) => void addFiles(event.target.files)}
          className="peer sr-only"
        />
        <label
          htmlFor={inputId}
          data-focus-id={`add-${inputId}`}
          className="inline-flex min-h-11 cursor-pointer items-center justify-center gap-2 rounded-control border border-primary bg-primary/10 px-4 text-sm font-semibold text-text-primary transition-colors hover:bg-primary/20 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-focus peer-disabled:cursor-not-allowed peer-disabled:opacity-50"
        >
          <CommunityIcon name="image" className="size-4" />
          {processing ? t("processing") : t(total > 0 ? "addMore" : "add")}
        </label>
        <p id={hintId} className="min-w-0 flex-1 basis-56 text-sm leading-5 text-text-secondary">
          <span className="font-semibold text-text-primary tabular-nums">
            {total}/{LIMITS.imagesPerPost}
          </span>{" "}
          {t("hint")}
        </p>
      </div>

      {rejections.length > 0 ? (
        <div role="alert" className="mt-3 rounded-control border border-warning/60 bg-warning/10 p-3 text-sm text-text-primary">
          <div className="flex items-start justify-between gap-3">
            <p className="font-medium">{t("rejectedTitle", { count: rejections.length })}</p>
            <button type="button" className={cn(actionClass, "-m-2 shrink-0")} onClick={() => setRejections([])} aria-label={t("dismiss")}>
              <Icon name="close" className="size-4" />
            </button>
          </div>
          <ul className="mt-1.5 space-y-1">
            {rejections.map((rejection, index) => (
              <li key={`${rejection.name}-${index}`} className="break-words text-text-secondary">
                <span className="font-medium text-text-primary">{rejection.name}</span> — {t(`reject.${rejection.reason}`)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {items.length > 0 ? (
        <>
          <p className="mb-2 mt-4 text-xs leading-5 text-text-secondary">{t("orderHelp")}</p>
          <ol aria-label={t("listLabel")} className="grid grid-cols-[repeat(auto-fill,minmax(5.75rem,1fr))] gap-3">
            {items.map((image, index) => {
              const isActive = image.key === activeKey;
              return (
                <li
                  key={image.key}
                  draggable={!disabled}
                  onDragStart={() => setDragKey(image.key)}
                  onDragEnd={() => setDragKey(null)}
                  onDragOver={(event) => dragKey !== null && event.preventDefault()}
                  onDrop={(event) => onThumbDrop(event, index)}
                  className={cn(
                    "overflow-hidden rounded-control border bg-surface-elevated transition-colors",
                    isActive ? "border-primary" : "border-border",
                    dragKey === image.key && "opacity-50"
                  )}
                >
                  <button
                    type="button"
                    data-focus-id={`select-${image.key}`}
                    aria-current={isActive ? "true" : undefined}
                    aria-label={t("select", { number: index + 1, name: image.file.name })}
                    aria-keyshortcuts="Alt+ArrowLeft Alt+ArrowRight"
                    onClick={() => onActivate?.(image.key)}
                    onKeyDown={(event) => onThumbKeyDown(event, index, image.key)}
                    className="relative block aspect-square w-full focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus"
                  >
                    {/* Yerel önizleme (blob adresi): optimizasyon vekili uygulanamaz. Küçük kare kırpma yalnız küçük resimdedir. */}
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={image.url} alt="" draggable={false} className="size-full object-cover" />
                    <span
                      aria-hidden="true"
                      className="absolute start-1 top-1 rounded-control bg-background/90 px-1.5 text-xs font-semibold tabular-nums text-text-primary"
                    >
                      {index + 1}
                    </span>
                  </button>
                  <div className="flex items-center justify-between border-t border-border px-0.5">
                    <button
                      type="button"
                      className={smallButton}
                      disabled={disabled || index === 0}
                      data-focus-id={`earlier-${image.key}`}
                      aria-label={t("moveEarlier", { name: image.file.name })}
                      onClick={() => move(index, index - 1, `earlier-${image.key}`)}
                    >
                      <Icon name="chevron" className="size-4 rotate-90" />
                    </button>
                    <button
                      type="button"
                      className={smallButton}
                      disabled={disabled || index === items.length - 1}
                      data-focus-id={`later-${image.key}`}
                      aria-label={t("moveLater", { name: image.file.name })}
                      onClick={() => move(index, index + 1, `later-${image.key}`)}
                    >
                      <Icon name="chevron" className="size-4 -rotate-90" />
                    </button>
                    <button
                      type="button"
                      className={cn(smallButton, "hover:text-brand-text")}
                      disabled={disabled}
                      aria-label={t("remove", { name: image.file.name })}
                      onClick={() => remove(index)}
                    >
                      <CommunityIcon name="trash" className="size-4" />
                    </button>
                  </div>
                </li>
              );
            })}
          </ol>
        </>
      ) : null}

      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </div>
  );
}
