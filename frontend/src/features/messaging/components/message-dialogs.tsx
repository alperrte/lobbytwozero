"use client";

import { useEffect, useMemo, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Checkbox } from "@/shared/components/ui/checkbox";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import { useMessaging } from "../hooks/use-messaging";
import { usePeople } from "../hooks/use-people";
import { messagingErrorKey } from "../services/messaging-errors";
import type { ConversationId, DeleteScope, Message, MessageReactions, MessageReads } from "../types/messaging.types";
import { conversationTitle } from "./conversation-list";
import { EmojiPicker } from "./emoji-picker";
import { useMessageSnippet } from "./message-parts";

const MAX_FORWARD_TARGETS = 5;

/**
 * "Benden sil" ve "Herkesten sil" farklı onay metinleriyle sunulur (web-rules §7.2). Herkesten silme geri alınamaz ve diğer
 * katılımcıların görünümünü de değiştirir; benden silme yalnız kendi görünümünü.
 */
export function DeleteMessageDialog({
  conversationId,
  message,
  scope,
  onClose
}: Readonly<{ conversationId: ConversationId; message: Message; scope: DeleteScope; onClose: () => void }>) {
  const t = useTranslations("Messaging.delete");
  const tErrors = useTranslations("Messaging.errors");
  const { remove } = useMessaging();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await remove(conversationId, message.id, scope);
      onClose();
    } catch (cause) {
      setError(tErrors(messagingErrorKey(cause)));
      setBusy(false);
    }
  }

  return (
    <ConfirmDialog
      title={scope === "me" ? t("meTitle") : t("everyoneTitle")}
      description={scope === "me" ? t("meBody") : t("everyoneBody")}
      confirmLabel={scope === "me" ? t("meConfirm") : t("everyoneConfirm")}
      cancelLabel={t("cancel")}
      tone="danger"
      busy={busy}
      onConfirm={() => void confirm()}
      onCancel={onClose}
    >
      {error ? (
        <p role="alert" className="text-sm text-brand-text">
          {error}
        </p>
      ) : null}
    </ConfirmDialog>
  );
}

/** Mesajı en çok 5 sohbete iletme (MSG-20). Hepsi ya da hiçbiri: bir hedef uygun değilse hiçbirine gönderilmez. */
export function ForwardDialog({
  conversationId,
  message,
  onClose,
  onDone
}: Readonly<{ conversationId: ConversationId; message: Message; onClose: () => void; onDone: (count: number) => void }>) {
  const t = useTranslations("Messaging");
  const { state, forward } = useMessaging();
  const snippet = useMessageSnippet();
  const person = usePeople(state.conversations.flatMap((item) => (item.peerUserId ? [item.peerUserId] : [])));
  const [selected, setSelected] = useState<ConversationId[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggle(id: ConversationId, checked: boolean) {
    setSelected((current) => (checked ? [...current, id].slice(0, MAX_FORWARD_TARGETS) : current.filter((item) => item !== id)));
  }

  async function submit() {
    if (selected.length === 0) return;
    setBusy(true);
    setError(null);
    try {
      await forward(conversationId, message.id, selected);
      onDone(selected.length);
    } catch (cause) {
      setError(t(`errors.${messagingErrorKey(cause)}`));
      setBusy(false);
    }
  }

  return (
    <ModalDialog title={t("forward.title")} description={t("forward.description", { max: MAX_FORWARD_TARGETS })} onClose={onClose} closeLabel={t("dialog.close")}>
      <p className="rounded-panel border-s-2 border-primary bg-surface-elevated px-3 py-2 text-sm text-text-secondary line-clamp-2">{snippet(message)}</p>
      <fieldset className="max-h-72 space-y-1 overflow-y-auto pe-1">
        <legend className="sr-only">{t("forward.targets")}</legend>
        {state.conversations.map((item) => {
          const checked = selected.includes(item.id);
          return (
            <Checkbox
              key={item.id}
              className="rounded-panel px-2 py-2 hover:bg-surface-elevated"
              label={conversationTitle(item, person, t("list.untitled"))}
              checked={checked}
              disabled={!checked && selected.length >= MAX_FORWARD_TARGETS}
              onChange={(event) => toggle(item.id, event.target.checked)}
            />
          );
        })}
      </fieldset>
      <p aria-live="polite" className="text-xs text-text-secondary">
        {t("forward.selected", { count: selected.length, max: MAX_FORWARD_TARGETS })}
      </p>
      {error ? (
        <p role="alert" className="text-sm text-brand-text">
          {error}
        </p>
      ) : null}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onClose} className={buttonClass("ghost")}>
          {t("dialog.cancel")}
        </button>
        <button type="button" disabled={selected.length === 0 || busy} aria-busy={busy || undefined} onClick={() => void submit()} className={buttonClass("primary")}>
          {busy ? t("forward.sending") : t("forward.submit")}
        </button>
      </div>
    </ModalDialog>
  );
}

/**
 * Mesaj bilgisi (MSG-12, MSG-23). Okuyanları yalnız mesajın göndereni görür (sunucu başkasına `403` verir); tepkileri sohbetin
 * her üyesi görür.
 */
export function MessageInfoDialog({
  conversationId,
  message,
  mine,
  onClose
}: Readonly<{ conversationId: ConversationId; message: Message; mine: boolean; onClose: () => void }>) {
  const t = useTranslations("Messaging");
  const format = useFormatter();
  const { api } = useMessaging();
  const [reads, setReads] = useState<{ data?: MessageReads; error?: unknown }>({});
  const [reactions, setReactions] = useState<{ data?: MessageReactions; error?: unknown }>({});
  const showReads = mine && !message.deleted;

  useEffect(() => {
    const controller = new AbortController();
    if (showReads) {
      api.reads(conversationId, message.id, controller.signal).then(
        (data) => setReads({ data }),
        (error: unknown) => !controller.signal.aborted && setReads({ error })
      );
    }
    api.reactions(conversationId, message.id, controller.signal).then(
      (data) => setReactions({ data }),
      (error: unknown) => !controller.signal.aborted && setReactions({ error })
    );
    return () => controller.abort();
  }, [api, conversationId, message.id, showReads]);

  const ids = useMemo(
    () => [
      ...(reads.data?.readers.map((item) => item.userId) ?? []),
      ...(reads.data?.pending.map((item) => item.userId) ?? []),
      ...(reactions.data?.reactions.map((item) => item.userId) ?? [])
    ],
    [reads.data, reactions.data]
  );
  const person = usePeople(ids);
  const when = (value: string | null) =>
    value ? format.dateTime(new Date(value), { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : t("info.timeUnknown");

  return (
    <ModalDialog title={showReads ? t("info.title") : t("info.reactionsTitle")} onClose={onClose} closeLabel={t("dialog.close")}>
      <p className="text-xs text-text-secondary">
        {t("info.sentAt", { time: when(message.createdAt) })}
      </p>
      {showReads ? (
        <section aria-labelledby="message-info-reads" className="space-y-3">
          <h3 id="message-info-reads" className="font-display font-semibold">{t("info.readBy")}</h3>
          <InfoBody state={reads} empty={!reads.data?.readers.length}>
            <ul className="space-y-2">
              {reads.data?.readers.map((reader) => (
                <PersonLine key={reader.userId} person={person(reader.userId)} detail={when(reader.readAt)} />
              ))}
            </ul>
          </InfoBody>
          {reads.data && reads.data.pending.length > 0 ? (
            <>
              <h3 className="font-display font-semibold">{t("info.pending")}</h3>
              <ul className="space-y-2">
                {reads.data.pending.map((item) => (
                  <PersonLine key={item.userId} person={person(item.userId)} detail={t("info.notYet")} />
                ))}
              </ul>
            </>
          ) : null}
        </section>
      ) : null}
      <section aria-labelledby="message-info-reactions" className="space-y-3">
        <h3 id="message-info-reactions" className="font-display font-semibold">{t("info.reactions")}</h3>
        <InfoBody state={reactions} empty={!reactions.data?.reactions.length} emptyLabel={t("info.noReactions")}>
          <ul className="space-y-2">
            {reactions.data?.reactions.map((item) => (
              <PersonLine key={item.userId} person={person(item.userId)} detail={item.emoji} />
            ))}
          </ul>
        </InfoBody>
      </section>
    </ModalDialog>
  );
}

function InfoBody({
  state,
  empty,
  emptyLabel,
  children
}: Readonly<{ state: { data?: unknown; error?: unknown }; empty: boolean; emptyLabel?: string; children: React.ReactNode }>) {
  const t = useTranslations("Messaging");
  if (state.error) {
    return <p role="alert" className="text-sm text-brand-text">{t(`errors.${messagingErrorKey(state.error)}`)}</p>;
  }
  if (!state.data) {
    return <p role="status" className="text-sm text-text-secondary">{t("states.loading")}</p>;
  }
  if (empty) {
    return <p className="text-sm text-text-secondary">{emptyLabel ?? t("info.nobody")}</p>;
  }
  return children;
}

function PersonLine({ person, detail }: Readonly<{ person: ReturnType<ReturnType<typeof usePeople>>; detail: string }>) {
  return (
    <li className="flex items-center gap-3">
      <Avatar seed={person.userId} imageUrl={person.avatarUrl} size="sm" />
      <span className="min-w-0 flex-1 truncate text-sm font-medium">{person.name}</span>
      <span className="shrink-0 text-sm text-text-secondary">{detail}</span>
    </li>
  );
}

/** Hızlı seçimde olmayan bir emojiyle tepki (MSG-23). */
export function ReactionPickerDialog({ onPick, onClose }: Readonly<{ onPick: (emoji: string) => void; onClose: () => void }>) {
  const t = useTranslations("Messaging");
  return (
    <ModalDialog title={t("menu.moreReactions")} onClose={onClose} closeLabel={t("dialog.close")}>
      <EmojiPicker onPick={onPick} className="w-full max-w-none border-0 p-0 shadow-none" />
    </ModalDialog>
  );
}
