"use client";

import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { Icon } from "@/shared/components/ui/icons";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { MessagingProvider, useMessaging } from "../hooks/use-messaging";
import { PeopleProvider } from "../hooks/use-people";
import { messagingErrorKey } from "../services/messaging-errors";
import type { ConversationId } from "../types/messaging.types";
import { ConversationList } from "./conversation-list";
import { NewConversationDialog } from "./conversation-dialogs";
import { ThreadView } from "./thread-view";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Mesajlar sayfası. Yalnız oturum açmış kullanıcıya çizilir; oturum doğrulanmadan mesajlaşma isteği atılmaz, misafir girişe
 * yönlenir (yetki kararı her zaman messaging-service'tedir).
 */
export function MessagingScreen() {
  const session = useRequireSession();
  const t = useTranslations("Messaging.states");
  if (session.status !== "authenticated" || !session.me) {
    return (
      <div role="status" aria-live="polite" className="grid h-[calc(100dvh-8rem)] min-h-[30rem] gap-4 lg:grid-cols-[22rem_minmax(0,1fr)]">
        <span className="sr-only">{t("loading")}</span>
        <div aria-hidden="true" className="animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none" />
        <div aria-hidden="true" className="hidden animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none lg:block" />
      </div>
    );
  }
  return (
    <PeopleProvider>
      <MessagingProvider meId={session.me.accountId}>
        <MessagingLayout />
      </MessagingProvider>
    </PeopleProvider>
  );
}

/**
 * İki panel: solda sohbet listesi, sağda açık sohbet. `lg` altında tek panel görünür (liste ya da sohbet; geri düğmesi listeye
 * döner). Açık sohbet adres çubuğunda tutulur (`?c=`); profil/arkadaş kartından `?to=<userId>` ile gelinirse birebir sohbet
 * açılır ya da mevcut olan bulunur.
 */
function MessagingLayout() {
  const t = useTranslations("Messaging");
  const params = useSearchParams();
  const router = useRouter();
  const { setActive, openDirect } = useMessaging();
  const requested = params.get("c");
  const activeId = requested && UUID.test(requested) ? requested : null;
  const target = params.get("to");
  const [newOpen, setNewOpen] = useState(false);
  const [openError, setOpenError] = useState<string | null>(null);
  const handledTarget = useRef<string | null>(null);

  const select = (conversationId: ConversationId | null) => {
    router.replace(conversationId ? { pathname: "/messages", query: { c: conversationId } } : "/messages", { scroll: false });
  };

  useEffect(() => {
    setActive(activeId);
    return () => setActive(null);
    // `setActive` sağlayıcıda her çizimde yenidir; yalnız seçim değişince bildirilir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  useEffect(() => {
    if (!target || handledTarget.current === target) return;
    handledTarget.current = target;
    if (!UUID.test(target)) {
      router.replace("/messages");
      return;
    }
    openDirect(target).then(
      (conversation) => router.replace({ pathname: "/messages", query: { c: conversation.id } }),
      (error: unknown) => {
        setOpenError(t(`errors.${messagingErrorKey(error)}`));
        router.replace("/messages");
      }
    );
  }, [target, openDirect, router, t]);

  return (
    <div className="space-y-3">
      {openError ? (
        <p role="alert" className="flex items-center gap-2 rounded-panel border border-primary/50 bg-surface px-4 py-3 text-sm">
          <Icon name="info" className="size-4 shrink-0 text-brand-text" />
          <span className="flex-1">{openError}</span>
          <button type="button" onClick={() => setOpenError(null)} aria-label={t("dialog.close")} className="text-text-secondary hover:text-text-primary">
            <Icon name="close" className="size-4" />
          </button>
        </p>
      ) : null}
      <div className="grid h-[calc(100dvh-8rem)] min-h-[30rem] gap-4 lg:grid-cols-[20rem_minmax(0,1fr)] xl:grid-cols-[23rem_minmax(0,1fr)]">
        <div className={cn("min-h-0 min-w-0 flex-col", activeId ? "hidden lg:flex" : "flex")}>
          <ConversationList activeId={activeId} onSelect={select} onNew={() => setNewOpen(true)} />
        </div>
        <div className={cn("min-h-0 min-w-0 flex-col", activeId ? "flex" : "hidden lg:flex")}>
          {activeId ? (
            <ThreadView conversationId={activeId} onBack={() => select(null)} onLeft={() => select(null)} />
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center rounded-panel border border-dashed border-border bg-surface px-6 text-center">
              <span className="inline-flex size-14 items-center justify-center rounded-full bg-primary/12 text-brand-text ring-1 ring-primary/40">
                <Icon name="messages" className="size-7" />
              </span>
              <p className="mt-4 font-display text-lg font-semibold">{t("thread.pickTitle")}</p>
              <p className="mt-1.5 max-w-sm text-sm text-text-secondary">{t("thread.pickBody")}</p>
            </div>
          )}
        </div>
      </div>
      {newOpen ? (
        <NewConversationDialog
          onClose={() => setNewOpen(false)}
          onOpened={(conversationId) => {
            setNewOpen(false);
            select(conversationId);
          }}
        />
      ) : null}
    </div>
  );
}
