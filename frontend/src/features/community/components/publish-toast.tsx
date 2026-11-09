"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { dismissFlash, FADE_MS, FLASH_MS, usePublishState, type PublishFlash } from "../services/publish-flow";

/**
 * "Gönderin paylaşıldı." başarı bildirimi: ekranın sağ altından girer, görünür olduktan 2 saniye sonra yumuşak geçişle
 * kendiliğinden kapanır. Projede ortak bir toast bileşeni olmadığından Community'ye özgü tek örnektir ve Community
 * yerleşiminde (`PublishOverlay` ile birlikte) durur: oluşturma sayfasından akışa geçişte kaybolmaz ve sayfa içi ayrı bir
 * başarı bandı yoktur. Her yeni başarılı paylaşım (`flash.at`) bileşeni yeniden kurar ve süreyi baştan başlatır; süre dolunca
 * depodaki kayıt da silinir (bkz. `publish-flow`), bildirim kalıcı kalmaz. Yalnız başarıda (`completePublish`) kurulur.
 */
export function PublishToast() {
  const { flash } = usePublishState();
  return flash ? <ToastBody key={flash.at} flash={flash} /> : null;
}

function ToastBody({ flash }: { flash: PublishFlash }) {
  const t = useTranslations("Community");
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    const remaining = Math.max(0, FLASH_MS - (Date.now() - flash.at));
    const timer = setTimeout(() => setLeaving(true), remaining);
    return () => clearTimeout(timer);
  }, [flash.at]);

  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(() => dismissFlash(flash.at), FADE_MS);
    return () => clearTimeout(timer);
  }, [leaving, flash.at]);

  return (
    <div className="pointer-events-none fixed inset-x-4 bottom-4 z-[60] flex justify-end sm:inset-x-auto sm:bottom-6 sm:end-6">
      <div
        role="status"
        className={cn(
          "ltz-toast-in pointer-events-auto flex w-full items-center gap-3 rounded-control border border-success/60 bg-surface px-4 py-3 text-sm text-text-primary shadow-popover transition-[opacity,translate] duration-300 ease-out motion-reduce:transition-none sm:w-auto sm:min-w-72",
          leaving && "translate-y-2 opacity-0"
        )}
      >
        <Icon name="check" className="size-5 shrink-0 text-success" />
        <span className="min-w-0 flex-1 font-medium">{t("feed.published")}</span>
        <button
          type="button"
          aria-label={t("toast.close")}
          className="-me-2 inline-flex size-9 shrink-0 items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
          onClick={() => setLeaving(true)}
        >
          <Icon name="close" className="size-4" />
        </button>
      </div>
    </div>
  );
}
