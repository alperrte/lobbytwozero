"use client";

import { memo, useEffect, useId, useImperativeHandle, useRef, useState, type CSSProperties, type MouseEvent, type PointerEvent, type Ref } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { menuItemClass } from "@/shared/components/ui/styles";
import { focusFirstMenuItem, onMenuKeyDown, usePopover } from "@/shared/hooks/use-popover";
import { apiUrl } from "@/shared/lib/api/client";
import { cn } from "@/shared/utils/cn";
import type { PersonView } from "../hooks/use-people";
import type { DeleteScope, Message, ThreadMessage } from "../types/messaging.types";
import { QUICK_REACTIONS } from "./emoji-picker";
import { isSafeGifUrl } from "./gif-picker";
import { DeliveryStatus, typeLabel } from "./message-parts";

export type BubbleActions = {
  onReply: (message: Message) => void;
  onForward: (message: Message) => void;
  onInfo: (message: Message) => void;
  onDelete: (message: Message, scope: DeleteScope) => void;
  onReact: (message: Message, emoji: string | null) => void;
  onMoreReactions: (message: Message) => void;
  onRetry: (message: ThreadMessage) => void;
  onDiscard: (message: ThreadMessage) => void;
  onJump: (messageId: string) => void;
};

type MessageBubbleProps = {
  message: ThreadMessage;
  mine: boolean;
  sender: PersonView;
  replySender: PersonView | null;
  /** Grupta ardışık mesajların ilkinde gönderen adı gösterilir. */
  showSender: boolean;
  highlighted: boolean;
  actions: BubbleActions;
};

const LONG_PRESS_MS = 450;
const ATTACHMENT_PATH = /^\/api\/v1\/messaging\/conversations\/[0-9a-f-]{36}\/attachments\/[0-9a-f-]{36}$/;

/** Ek adresi: yalnız messaging-service yolu (Gateway üzerinden) ya da yerel önizleme (`blob:`) kabul edilir. */
export function attachmentSrc(url: string): string | null {
  if (url.startsWith("blob:")) return url;
  return ATTACHMENT_PATH.test(url) ? apiUrl(url as `/${string}`) : null;
}

/** Herkesten silme hakkı: sunucunun verdiği son an henüz geçmediyse (MSG-24/26). */
export function canDeleteForEveryone(message: Message, now = Date.now()): boolean {
  return !message.deleted && message.deletableForEveryoneUntil !== null && Date.parse(message.deletableForEveryoneUntil) > now;
}

function MessageBubbleView({ message, mine, sender, replySender, showSender, highlighted, actions }: MessageBubbleProps) {
  const t = useTranslations("Messaging");
  const format = useFormatter();
  const local = message.local;
  const time = format.dateTime(new Date(message.createdAt), { hour: "2-digit", minute: "2-digit" });
  const menu = useRef<MenuHandle>(null);
  const longPress = useLongPress(() => menu.current?.show());

  return (
    <div
      id={`message-${message.id}`}
      data-message-id={message.id}
      className={cn("group flex px-3 py-0.5 sm:px-4", mine ? "justify-end" : "justify-start")}
    >
      <div className={cn("relative flex max-w-[min(85%,36rem)] flex-col", mine ? "items-end" : "items-start")}>
        {showSender && !mine ? (
          <span className={cn("mb-0.5 px-1 text-xs font-semibold", sender.deleted ? "text-text-secondary" : "text-brand-text")}>{sender.name}</span>
        ) : null}
        <div className={cn("flex items-start gap-1", mine && "flex-row-reverse")}>
          <article
            {...(local ? {} : longPress)}
            aria-label={t("bubble.label", { name: mine ? t("list.you") : sender.name, time })}
            className={cn(
              "min-w-0 rounded-float px-3 py-2 text-sm text-text-primary transition-shadow duration-300",
              mine ? "rounded-se-control border border-primary/35 bg-primary/12" : "rounded-ss-control border border-border bg-surface-elevated",
              local?.state === "failed" && "border-primary",
              highlighted && "ring-2 ring-focus"
            )}
          >
            {message.forwarded && !message.deleted ? (
              <p className="mb-1 flex items-center gap-1 text-xs italic text-text-secondary">
                <Icon name="forward" className="size-3.5" />
                {message.forwardedManyTimes ? t("bubble.forwardedMany") : t("bubble.forwarded")}
              </p>
            ) : null}
            {message.replyTo && !message.deleted ? (
              <ReplyQuote message={message} author={replySender} mine={mine} onJump={actions.onJump} />
            ) : null}
            <BubbleContent message={message} />
            <span className="float-end ms-3 mt-1 inline-flex translate-y-0.5 items-center gap-1 text-[0.6875rem] text-text-secondary">
              <time dateTime={message.createdAt}>{time}</time>
              {mine ? <DeliveryStatus message={message} /> : null}
            </span>
          </article>
          {!local ? <MessageMenu handle={menu} message={message} mine={mine} senderName={sender.name} actions={actions} /> : null}
        </div>

        {message.reactions.summary.length > 0 ? (
          <ul aria-label={t("bubble.reactions")} className={cn("-mt-1.5 flex flex-wrap gap-1 px-2", mine && "justify-end")}>
            {message.reactions.summary.map((reaction) => {
              const selected = reaction.emoji === message.reactions.mine;
              return (
                <li key={reaction.emoji}>
                  <button
                    type="button"
                    aria-pressed={selected}
                    aria-label={t("bubble.reactionChip", { emoji: reaction.emoji, count: reaction.count })}
                    onClick={() => actions.onReact(message, selected ? null : reaction.emoji)}
                    className={cn(
                      "inline-flex h-6 items-center gap-1 rounded-full border bg-surface px-2 text-xs shadow-sm hover:border-primary/60 focus-visible:outline-2 focus-visible:outline-focus",
                      selected ? "border-primary/60 text-text-primary" : "border-border text-text-secondary"
                    )}
                  >
                    <span aria-hidden="true">{reaction.emoji}</span>
                    <span aria-hidden="true" className="font-semibold">{reaction.count}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        ) : null}

        {local?.state === "failed" ? (
          <div role="alert" className="mt-1 flex flex-wrap items-center justify-end gap-2 text-xs">
            <span className="text-brand-text">{t(`errors.${local.errorKey ?? "generic"}`)}</span>
            <button type="button" onClick={() => actions.onRetry(message)} className="font-semibold text-text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-focus">
              {t("bubble.retry")}
            </button>
            <button type="button" onClick={() => actions.onDiscard(message)} className="text-text-secondary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-focus">
              {t("bubble.discard")}
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}

export const MessageBubble = memo(MessageBubbleView);

function ReplyQuote({ message, author, mine, onJump }: { message: Message; author: PersonView | null; mine: boolean; onJump: (id: string) => void }) {
  const t = useTranslations("Messaging");
  const reply = message.replyTo!;
  let text: string;
  if (reply.unavailable) text = t("bubble.replyUnavailable");
  else if (reply.deleted) text = t("snippet.deleted");
  else text = reply.snippet ?? typeLabel((key) => t(`snippet.${key}`), reply.type);
  const name = reply.unavailable ? null : author ? (author.userId === message.senderId && mine ? t("list.you") : author.name) : null;

  return (
    <button
      type="button"
      onClick={() => onJump(reply.messageId)}
      disabled={reply.unavailable}
      className={cn(
        "mb-1.5 block w-full min-w-0 rounded-control border-s-2 border-primary bg-background/60 px-2.5 py-1.5 text-start text-xs hover:bg-background/80 focus-visible:outline-2 focus-visible:outline-focus disabled:cursor-default",
        mine && "bg-surface/70"
      )}
      aria-label={t("bubble.jumpToReply")}
    >
      {name ? <span className="block font-semibold text-brand-text">{name}</span> : null}
      <span className={cn("line-clamp-2 text-text-secondary", (reply.deleted || reply.unavailable) && "italic")}>{text}</span>
    </button>
  );
}

function BubbleContent({ message }: { message: ThreadMessage }) {
  const t = useTranslations("Messaging");
  if (message.deleted) {
    return (
      <p className="flex items-center gap-1.5 italic text-text-secondary">
        <Icon name="ban" className="size-4" />
        {t("snippet.deleted")}
      </p>
    );
  }
  const media = (() => {
    if (message.type === "IMAGE" && message.attachment) {
      const src = attachmentSrc(message.attachment.url);
      if (!src) return null;
      const { width, height } = message.attachment;
      return (
        <a
          href={message.local ? undefined : src}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={t("bubble.openImage")}
          className="-mx-1 -mt-0.5 mb-1 block overflow-hidden rounded-panel focus-visible:outline-2 focus-visible:outline-focus"
        >
          {/* eslint-disable-next-line @next/next/no-img-element -- yetkili ek (no-store), Next görsel optimizasyonundan geçemez */}
          <img
            src={src}
            alt={t("bubble.imageAlt")}
            width={width ?? undefined}
            height={height ?? undefined}
            loading="lazy"
            className={cn("max-h-80 w-auto max-w-full object-contain", message.local && "opacity-70")}
          />
        </a>
      );
    }
    if (message.type === "GIF" && message.gif && isSafeGifUrl(message.gif.url)) {
      return (
        // eslint-disable-next-line @next/next/no-img-element -- izinli sağlayıcının animasyonlu GIF'i
        <img
          src={message.gif.url}
          alt={t("bubble.gifAlt")}
          width={message.gif.width}
          height={message.gif.height}
          loading="lazy"
          referrerPolicy="no-referrer"
          className="-mx-1 -mt-0.5 mb-1 max-h-60 w-auto max-w-full rounded-panel"
        />
      );
    }
    if (message.type === "VOICE" && message.attachment) {
      const src = attachmentSrc(message.attachment.url);
      if (!src) return null;
      const seconds = Math.round((message.attachment.durationMs ?? 0) / 1000);
      return (
        <div className="flex flex-col gap-1">
          <audio controls preload="none" src={src} className="h-10 max-w-64" aria-label={t("bubble.voice", { seconds })} />
          <span className="text-xs text-text-secondary">{t("bubble.voiceDuration", { minutes: Math.floor(seconds / 60), seconds: String(seconds % 60).padStart(2, "0") })}</span>
        </div>
      );
    }
    return null;
  })();
  return (
    <>
      {media}
      {message.body ? <p className="whitespace-pre-wrap break-words [overflow-wrap:anywhere]">{message.body}</p> : null}
    </>
  );
}

/**
 * Mesaj işlem menüsü (MSG-24). Fareyle mesajın üzerine gelince ok görünür; dokunmatikte mesaja uzun basınca açılır; klavyede
 * ok düğmesi her zaman odaklanabilir (Tab ile görünür olur), menüde ↑/↓ ile gezilir, Esc kapatır.
 */
type MenuHandle = { show: () => void };

const MENU_WIDTH = 240;
const MENU_HEIGHT = 340;
const EDGE = 8;

/**
 * Menünün ekrandaki yeri: altında yer varsa aşağı, yoksa (ve üstte daha çok yer varsa) yukarı açılır. Kendi mesajında sağa,
 * başkasınınkinde sola hizalanır; ekran kenarından taşmaz.
 */
export function menuPosition(rect: Pick<DOMRect, "top" | "bottom" | "left" | "right">, mine: boolean, viewport = { width: window.innerWidth, height: window.innerHeight }): CSSProperties {
  const below = viewport.height - rect.bottom;
  const above = rect.top;
  const up = below < MENU_HEIGHT && above > below;
  const width = Math.min(MENU_WIDTH, viewport.width - EDGE * 2);
  const preferred = mine ? rect.right - width : rect.left;
  const left = Math.max(EDGE, Math.min(preferred, viewport.width - width - EDGE));
  const vertical = up ? { bottom: Math.max(EDGE, viewport.height - rect.top + 4) } : { top: Math.min(rect.bottom + 4, viewport.height - EDGE) };
  return { left, ...vertical, maxHeight: Math.max(160, (up ? above : below) - EDGE * 2), overflowY: "auto" };
}

function MessageMenu({
  message,
  mine,
  senderName,
  actions,
  handle
}: {
  message: Message;
  mine: boolean;
  senderName: string;
  actions: BubbleActions;
  handle: Ref<MenuHandle>;
}) {
  const t = useTranslations("Messaging.menu");
  const { open, setOpen, close, rootRef, triggerRef } = usePopover();
  const menuId = useId();
  const [position, setPosition] = useState<CSSProperties>({});

  // Menü `fixed` konumludur (sohbetin kaydırma alanı onu kırpmasın). Sohbet kayınca (yeni mesaj dahil) mesajı izler; mesaj
  // görünür alandan çıkarsa ya da pencere boyutu değişirse kapanır.
  useEffect(() => {
    if (!open) return;
    const scroller = triggerRef.current?.closest("[data-thread-scroll]");
    const follow = () => {
      const rect = triggerRef.current?.getBoundingClientRect();
      const bounds = scroller?.getBoundingClientRect();
      if (!rect || (bounds && (rect.bottom < bounds.top || rect.top > bounds.bottom))) {
        close();
        return;
      }
      setPosition(menuPosition(rect, mine));
    };
    const dismiss = () => close();
    scroller?.addEventListener("scroll", follow, { passive: true });
    window.addEventListener("resize", dismiss);
    return () => {
      scroller?.removeEventListener("scroll", follow);
      window.removeEventListener("resize", dismiss);
    };
  }, [open, close, triggerRef, mine]);

  function show() {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (rect) setPosition(menuPosition(rect, mine));
    setOpen(true);
  }

  function choose(run: () => void) {
    close(true);
    run();
  }

  const deleted = message.deleted;
  const everyone = canDeleteForEveryone(message);

  useImperativeHandle(handle, () => ({ show }));

  return (
    <div ref={rootRef} className="relative shrink-0">
      <button
        ref={triggerRef}
        type="button"
        aria-label={t("open", { name: senderName })}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => (open ? close() : show())}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && !open) {
            event.preventDefault();
            show();
          }
        }}
        className="mt-1 inline-flex size-7 items-center justify-center rounded-full text-text-secondary opacity-0 transition-opacity hover:bg-surface-elevated hover:text-text-primary focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-focus aria-expanded:opacity-100 group-hover:opacity-100 pointer-coarse:opacity-60 motion-reduce:transition-none"
      >
        <Icon name="chevron" className="size-4" />
      </button>
      {open ? (
        <div
          id={menuId}
          role="menu"
          aria-label={t("label")}
          ref={(element) => focusFirstMenuItem(element)}
          onKeyDown={onMenuKeyDown}
          style={position}
          className="fixed z-40 w-60 max-w-[calc(100vw-1rem)] rounded-panel border border-border bg-surface p-1.5 shadow-popover animate-pop-in"
        >
          {!deleted ? (
            <div role="group" aria-label={t("react")} className="mb-1 flex items-center justify-between border-b border-border/70 px-1 pb-1.5">
              {QUICK_REACTIONS.map((emoji) => (
                <button
                  key={emoji}
                  type="button"
                  role="menuitemradio"
                  aria-checked={message.reactions.mine === emoji}
                  aria-label={t("reactWith", { emoji })}
                  onClick={() => choose(() => actions.onReact(message, message.reactions.mine === emoji ? null : emoji))}
                  className={cn(
                    "inline-flex size-8 items-center justify-center rounded-full text-lg hover:bg-surface-elevated focus-visible:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-focus",
                    message.reactions.mine === emoji && "bg-primary/15 ring-1 ring-primary/50"
                  )}
                >
                  {emoji}
                </button>
              ))}
              <button
                type="button"
                role="menuitem"
                aria-label={t("moreReactions")}
                onClick={() => choose(() => actions.onMoreReactions(message))}
                className="inline-flex size-8 items-center justify-center rounded-full text-text-secondary hover:bg-surface-elevated focus-visible:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-focus"
              >
                <Icon name="smile" className="size-4" />
              </button>
            </div>
          ) : null}
          {!deleted ? (
            <>
              <MenuItem icon="reply" label={t("reply")} onSelect={() => choose(() => actions.onReply(message))} />
              <MenuItem icon="forward" label={t("forward")} onSelect={() => choose(() => actions.onForward(message))} />
              {message.body ? (
                <MenuItem
                  icon="copy"
                  label={t("copy")}
                  onSelect={() => choose(() => void navigator.clipboard?.writeText(message.body ?? "").catch(() => undefined))}
                />
              ) : null}
            </>
          ) : null}
          <MenuItem icon="info" label={mine && !deleted ? t("info") : t("reactions")} onSelect={() => choose(() => actions.onInfo(message))} />
          <MenuItem icon="trash" label={t("deleteForMe")} danger onSelect={() => choose(() => actions.onDelete(message, "me"))} />
          {everyone ? (
            <MenuItem icon="trash" label={t("deleteForEveryone")} danger onSelect={() => choose(() => actions.onDelete(message, "everyone"))} />
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

/**
 * Dokunmatikte uzun basma: 450 ms parmak kalkmaz ve kaymazsa menü açılır (kaydırma menü açmaz). Açılınca tarayıcının metin
 * seçimi/bağlam menüsü bastırılır.
 */
function useLongPress(onLongPress: () => void) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const start = useRef<{ x: number; y: number } | null>(null);
  const fired = useRef(false);
  const cancel = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    start.current = null;
  };
  return {
    onPointerDown: (event: PointerEvent<HTMLElement>) => {
      if (event.pointerType !== "touch") return;
      fired.current = false;
      start.current = { x: event.clientX, y: event.clientY };
      timer.current = setTimeout(() => {
        fired.current = true;
        timer.current = null;
        onLongPress();
      }, LONG_PRESS_MS);
    },
    onPointerMove: (event: PointerEvent<HTMLElement>) => {
      const origin = start.current;
      if (origin && Math.hypot(event.clientX - origin.x, event.clientY - origin.y) > 10) cancel();
    },
    onPointerUp: cancel,
    onPointerCancel: cancel,
    onContextMenu: (event: MouseEvent<HTMLElement>) => {
      if (fired.current) event.preventDefault();
    }
  };
}

function MenuItem({ icon, label, danger = false, onSelect }: { icon: "reply" | "forward" | "copy" | "info" | "trash"; label: string; danger?: boolean; onSelect: () => void }) {
  return (
    <button type="button" role="menuitem" onClick={onSelect} className={cn(menuItemClass, danger && "text-brand-text hover:text-brand-text")}>
      <Icon name={icon} className="size-4" />
      {label}
    </button>
  );
}
