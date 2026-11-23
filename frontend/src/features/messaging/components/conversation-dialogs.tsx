"use client";

import { useEffect, useId, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Checkbox } from "@/shared/components/ui/checkbox";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { ModalDialog } from "@/shared/components/ui/modal-dialog";
import { SearchField } from "@/shared/components/ui/search-field";
import { cn } from "@/shared/utils/cn";
import { useFriendIds } from "../hooks/use-friends";
import { useMessaging } from "../hooks/use-messaging";
import { usePeople, type PersonView } from "../hooks/use-people";
import { MAX_GROUP_TITLE, validateGroupTitle } from "../schemas/message-body";
import { messagingErrorKey } from "../services/messaging-errors";
import type { Conversation, ConversationId, MemberRole, UserId } from "../types/messaging.types";

const MAX_GROUP_MEMBERS = 100;

function usePersonFilter(ids: UserId[], person: (id: UserId) => PersonView, query: string) {
  return useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    if (!needle) return ids;
    return ids.filter((id) => {
      const view = person(id);
      return view.name.toLocaleLowerCase().includes(needle) || (view.handle ?? "").toLocaleLowerCase().includes(needle);
    });
  }, [ids, person, query]);
}

/**
 * Yeni sohbet (MSG-01, MSG-02): bir arkadaşla birebir sohbet aç ya da başlık + üyelerle grup kur. Birebir sohbet varsa aynısı
 * açılır (sunucu `200`). Engel varsa sunucu `403` döner; neden ve yön söylenmez.
 */
export function NewConversationDialog({ onClose, onOpened }: Readonly<{ onClose: () => void; onOpened: (conversationId: ConversationId) => void }>) {
  const t = useTranslations("Messaging");
  const { openDirect, createGroup } = useMessaging();
  const [mode, setMode] = useState<"direct" | "group">("direct");
  const friends = useFriendIds(true);
  const person = usePeople(friends.ids);
  const [query, setQuery] = useState("");
  const visible = usePersonFilter(friends.ids, person, query);
  const [title, setTitle] = useState("");
  const [selected, setSelected] = useState<UserId[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [titleIssue, setTitleIssue] = useState<string | null>(null);
  const titleId = useId();

  async function run(task: () => Promise<Conversation>) {
    setBusy(true);
    setError(null);
    try {
      const conversation = await task();
      onOpened(conversation.id);
    } catch (cause) {
      setError(t(`errors.${messagingErrorKey(cause)}`));
      setBusy(false);
    }
  }

  function submitGroup() {
    const issue = validateGroupTitle(title);
    if (issue) {
      setTitleIssue(t(`group.titleIssue.${issue}`, { max: MAX_GROUP_TITLE }));
      return;
    }
    if (selected.length === 0) {
      setError(t("group.pickMembers"));
      return;
    }
    void run(() => createGroup(title.trim(), selected));
  }

  return (
    <ModalDialog title={t("new.title")} onClose={onClose} closeLabel={t("dialog.close")}>
      <div role="tablist" aria-label={t("new.mode")} className="grid grid-cols-2 gap-1 rounded-xl bg-surface-elevated p-1">
        {(["direct", "group"] as const).map((key) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={mode === key}
            onClick={() => {
              setMode(key);
              setError(null);
            }}
            className={cn(
              "h-9 rounded-lg text-sm font-semibold text-text-secondary transition-colors focus-visible:outline-2 focus-visible:outline-focus",
              mode === key && "bg-surface text-text-primary shadow-sm"
            )}
          >
            {t(`new.${key}`)}
          </button>
        ))}
      </div>

      {mode === "group" ? (
        <div className="space-y-1.5">
          <label htmlFor={titleId} className="text-sm font-medium">
            {t("group.titleLabel")}
          </label>
          <input
            id={titleId}
            value={title}
            maxLength={MAX_GROUP_TITLE}
            aria-invalid={titleIssue ? true : undefined}
            aria-describedby={titleIssue ? `${titleId}-issue` : undefined}
            onChange={(event) => {
              setTitle(event.target.value);
              setTitleIssue(null);
            }}
            className="h-10 w-full rounded-xl border border-transparent bg-surface-elevated px-3.5 text-sm hover:border-control-border focus-visible:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus aria-invalid:border-primary"
          />
          {titleIssue ? (
            <p id={`${titleId}-issue`} className="text-xs text-brand-text">
              {titleIssue}
            </p>
          ) : null}
        </div>
      ) : null}

      <SearchField label={t("new.searchFriends")} showLabel maxLength={64} value={query} onChange={(event) => setQuery(event.target.value)} />

      <div className="max-h-72 overflow-y-auto pe-1">
        {friends.loading ? (
          <p role="status" className="py-6 text-center text-sm text-text-secondary">{t("states.loading")}</p>
        ) : friends.error ? (
          <p role="alert" className="py-6 text-center text-sm text-brand-text">{t("new.friendsFailed")}</p>
        ) : visible.length === 0 ? (
          <p className="py-6 text-center text-sm text-text-secondary">{query ? t("new.noMatch") : t("new.noFriends")}</p>
        ) : mode === "direct" ? (
          <ul className="space-y-1">
            {visible.map((id) => {
              const view = person(id);
              return (
                <li key={id}>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void run(() => openDirect(id))}
                    className="flex w-full items-center gap-3 rounded-panel px-2 py-2 text-start hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-focus disabled:opacity-60"
                  >
                    <Avatar seed={id} imageUrl={view.avatarUrl} size="sm" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold">{view.name}</span>
                      {view.handle ? <span className="block truncate text-xs text-text-secondary">{view.handle}</span> : null}
                    </span>
                    <span className="text-xs font-semibold text-brand-text">{t("new.start")}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        ) : (
          <fieldset className="space-y-1">
            <legend className="sr-only">{t("group.members")}</legend>
            {visible.map((id) => (
              <Checkbox
                key={id}
                className="rounded-panel px-2 py-2 hover:bg-surface-elevated"
                label={person(id).name}
                checked={selected.includes(id)}
                disabled={!selected.includes(id) && selected.length >= MAX_GROUP_MEMBERS - 1}
                onChange={(event) =>
                  setSelected((current) => (event.target.checked ? [...current, id] : current.filter((item) => item !== id)))
                }
              />
            ))}
          </fieldset>
        )}
      </div>

      {error ? (
        <p role="alert" className="text-sm text-brand-text">
          {error}
        </p>
      ) : null}

      {mode === "group" ? (
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs text-text-secondary" aria-live="polite">
            {t("group.selected", { count: selected.length })}
          </p>
          <button type="button" disabled={busy} aria-busy={busy || undefined} onClick={submitGroup} className={buttonClass("primary")}>
            {busy ? t("group.creating") : t("group.create")}
          </button>
        </div>
      ) : null}
    </ModalDialog>
  );
}

/**
 * Grup bilgisi (MSG-03..05): üyeler ve rolleri, üye ekleme (sahip/yönetici), çıkarma (sahip herkesi, yönetici yalnız üyeyi),
 * rol değiştirme (yalnız sahip) ve gruptan ayrılma. Yetki kararı sunucudadır; arayüz yalnız izinli düğmeleri gösterir.
 */
export function GroupInfoDialog({
  conversationId,
  onClose,
  onLeft
}: Readonly<{ conversationId: ConversationId; onClose: () => void; onLeft: () => void }>) {
  const t = useTranslations("Messaging");
  const { api, state, conversationChanged, conversationLeft } = useMessaging();
  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [toAdd, setToAdd] = useState<UserId[]>([]);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const friends = useFriendIds(adding);

  useEffect(() => {
    const controller = new AbortController();
    api.getConversation(conversationId, controller.signal).then(setConversation, (cause: unknown) => {
      if (!controller.signal.aborted) setLoadError(cause);
    });
    return () => controller.abort();
  }, [api, conversationId]);

  const memberIds = conversation?.members.map((member) => member.userId) ?? [];
  const person = usePeople([...memberIds, ...friends.ids]);
  const myRole = conversation?.myRole ?? "MEMBER";
  const candidates = friends.ids.filter((id) => !memberIds.includes(id));

  async function mutate(key: string, task: () => Promise<Conversation | void>) {
    setBusy(key);
    setError(null);
    try {
      const result = await task();
      if (result) {
        setConversation(result);
        conversationChanged(result);
      } else {
        const fresh = await api.getConversation(conversationId);
        setConversation(fresh);
        conversationChanged(fresh);
      }
    } catch (cause) {
      setError(t(`errors.${messagingErrorKey(cause)}`));
    } finally {
      setBusy(null);
    }
  }

  async function leave() {
    setBusy("leave");
    try {
      await api.leave(conversationId);
      conversationLeft(conversationId);
      onLeft();
    } catch (cause) {
      setError(t(`errors.${messagingErrorKey(cause)}`));
      setBusy(null);
      setConfirmLeave(false);
    }
  }

  const canRemove = (role: MemberRole) => myRole === "OWNER" || (myRole === "ADMIN" && role === "MEMBER");

  return (
    <>
      <ModalDialog
        title={conversation?.title ?? t("group.info")}
        description={conversation ? t("list.members", { count: conversation.members.length }) : undefined}
        onClose={onClose}
        closeLabel={t("dialog.close")}
      >
        {loadError ? (
          <p role="alert" className="text-sm text-brand-text">{t(`errors.${messagingErrorKey(loadError)}`)}</p>
        ) : !conversation ? (
          <p role="status" className="text-sm text-text-secondary">{t("states.loading")}</p>
        ) : (
          <>
            <ul className="max-h-80 space-y-1 overflow-y-auto pe-1">
              {conversation.members.map((member) => {
                const view = person(member.userId);
                const self = member.userId === state.meId;
                return (
                  <li key={member.userId} className="flex flex-wrap items-center gap-3 rounded-panel px-2 py-2 hover:bg-surface-elevated">
                    <Avatar seed={member.userId} imageUrl={view.avatarUrl} size="sm" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold">{self ? t("group.youMember", { name: view.name }) : view.name}</span>
                      <span className="block text-xs text-text-secondary">{t(`group.role.${member.role}`)}</span>
                    </span>
                    {!self && myRole === "OWNER" && member.role !== "OWNER" ? (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() =>
                          void mutate(`role-${member.userId}`, () =>
                            api.changeRole(conversationId, member.userId, member.role === "ADMIN" ? "MEMBER" : "ADMIN")
                          )
                        }
                        className={buttonClass("ghost", "sm")}
                      >
                        {member.role === "ADMIN" ? t("group.demote") : t("group.promote")}
                      </button>
                    ) : null}
                    {!self && member.role !== "OWNER" && canRemove(member.role) ? (
                      <button
                        type="button"
                        disabled={busy !== null}
                        onClick={() => void mutate(`remove-${member.userId}`, () => api.removeMember(conversationId, member.userId))}
                        aria-label={t("group.removeNamed", { name: view.name })}
                        className={buttonClass("ghost", "sm", "text-brand-text hover:text-brand-text")}
                      >
                        {t("group.remove")}
                      </button>
                    ) : null}
                  </li>
                );
              })}
            </ul>

            {myRole !== "MEMBER" ? (
              adding ? (
                <div className="space-y-2 rounded-panel border border-border p-3">
                  <p className="text-sm font-semibold">{t("group.addTitle")}</p>
                  {friends.loading ? (
                    <p role="status" className="text-sm text-text-secondary">{t("states.loading")}</p>
                  ) : candidates.length === 0 ? (
                    <p className="text-sm text-text-secondary">{t("group.noCandidates")}</p>
                  ) : (
                    <fieldset className="max-h-48 space-y-1 overflow-y-auto">
                      <legend className="sr-only">{t("group.addTitle")}</legend>
                      {candidates.map((id) => (
                        <Checkbox
                          key={id}
                          label={person(id).name}
                          checked={toAdd.includes(id)}
                          disabled={!toAdd.includes(id) && memberIds.length + toAdd.length >= MAX_GROUP_MEMBERS}
                          onChange={(event) =>
                            setToAdd((current) => (event.target.checked ? [...current, id] : current.filter((item) => item !== id)))
                          }
                        />
                      ))}
                    </fieldset>
                  )}
                  <div className="flex justify-end gap-2">
                    <button type="button" onClick={() => setAdding(false)} className={buttonClass("ghost", "sm")}>
                      {t("dialog.cancel")}
                    </button>
                    <button
                      type="button"
                      disabled={toAdd.length === 0 || busy !== null}
                      onClick={() =>
                        void mutate("add", async () => {
                          const result = await api.addMembers(conversationId, toAdd);
                          setToAdd([]);
                          setAdding(false);
                          return result;
                        })
                      }
                      className={buttonClass("primary", "sm")}
                    >
                      {t("group.addSubmit", { count: toAdd.length })}
                    </button>
                  </div>
                </div>
              ) : (
                <button type="button" onClick={() => setAdding(true)} className={buttonClass("secondary", "sm", "self-start")}>
                  {t("group.add")}
                </button>
              )
            ) : null}

            {error ? (
              <p role="alert" className="text-sm text-brand-text">
                {error}
              </p>
            ) : null}

            <div className="border-t border-border pt-4">
              <button type="button" onClick={() => setConfirmLeave(true)} className={buttonClass("secondary", "md", "w-full text-brand-text")}>
                {t("group.leave")}
              </button>
            </div>
          </>
        )}
      </ModalDialog>
      {confirmLeave ? (
        <ConfirmDialog
          title={t("group.leaveTitle")}
          description={myRole === "OWNER" ? t("group.leaveOwnerBody") : t("group.leaveBody")}
          confirmLabel={t("group.leaveConfirm")}
          cancelLabel={t("dialog.cancel")}
          tone="danger"
          busy={busy === "leave"}
          onConfirm={() => void leave()}
          onCancel={() => setConfirmLeave(false)}
        />
      ) : null}
    </>
  );
}
