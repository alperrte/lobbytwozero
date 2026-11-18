"use client";

import { useEffect, useId, useState, type FormEvent } from "react";
import { searchAdminProfiles, type AdminProfileHit } from "@/features/user";
import { adminErrorMessage } from "../services/admin-errors";
import { STATUS_LABEL } from "./account-status";
import { errorClass, inputClass, labelClass, primaryButton } from "./ui";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MIN_LENGTH = 2;
const DEBOUNCE_MS = 350;

type Result =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; query: string; hits: AdminProfileHit[] };

/**
 * Hesap arama: `@kullanıcıadı` ya da görünen ad yazılır, sonuç listesinden hesap seçilir; yönetici UUID bilmek zorunda
 * değildir (UUID yazmak yine çalışır). Arama User servisinin ADMIN'e özel ucundadır ve askıdaki/yasaklı hesapları da
 * bulur. Yazarken kısa bir beklemeden sonra aranır, eski istek iptal edilir. Seçilen hesabın ayrıntısı Auth'tan alınır
 * (`onPick`); yetki kararı iki serviste de sunucudadır.
 */
export function AccountSearch({ busy, onPick }: { busy: boolean; onPick: (accountId: string) => void }) {
  const [input, setInput] = useState("");
  const [result, setResult] = useState<Result>({ status: "idle" });
  const listId = useId();
  const value = input.trim();
  const isId = UUID.test(value);
  const query = value.replace(/^@/, "").trim();
  const searchable = !isId && Array.from(query).length >= MIN_LENGTH;

  useEffect(() => {
    if (!searchable) {
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setResult({ status: "loading" });
      searchAdminProfiles(query, controller.signal)
        .then((hits) => setResult({ status: "ready", query, hits }))
        .catch((failure: unknown) => {
          if (!controller.signal.aborted) {
            setResult({ status: "error", message: adminErrorMessage(failure) });
          }
        });
    }, DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, searchable]);

  // Aranamayacak girişte (boş/kısa ya da UUID) eski sonuçlar gösterilmez.
  const shown: Result = searchable ? result : { status: "idle" };

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (isId) {
      onPick(value);
    } else if (shown.status === "ready" && shown.hits[0]) {
      onPick(shown.hits[0].userId);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <form onSubmit={onSubmit} className="flex flex-col gap-2 sm:flex-row sm:items-end" noValidate role="search">
        <label className="flex flex-1 flex-col gap-1 text-sm">
          <span className={labelClass}>Hesap ara (@kullanıcıadı, görünen ad ya da kimlik)</span>
          <input
            className={`${inputClass} font-mono`}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="@kullanıcıadı"
            spellCheck={false}
            autoComplete="off"
            aria-controls={listId}
          />
        </label>
        {isId && (
          <button type="submit" className={primaryButton} disabled={busy}>Kimlikle aç</button>
        )}
      </form>

      <div id={listId} aria-live="polite">
        {shown.status === "loading" && <p className={`${labelClass} py-2`}>Aranıyor…</p>}
        {shown.status === "error" && <p role="alert" className={errorClass}>{shown.message}</p>}
        {shown.status === "ready" && shown.hits.length === 0 && (
          <p className="py-2 text-sm text-text-secondary">“{shown.query}” ile başlayan hesap bulunamadı.</p>
        )}
        {shown.status === "ready" && shown.hits.length > 0 && (
          <ul className="flex flex-col divide-y divide-border border border-border bg-surface">
            {shown.hits.map((hit) => (
              <li key={hit.userId}>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => onPick(hit.userId)}
                  className="flex min-h-11 w-full items-center justify-between gap-3 px-3 py-2 text-left transition-colors hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-focus disabled:opacity-40"
                >
                  <span className="flex min-w-0 flex-col">
                    <span className="truncate font-mono text-sm text-text-primary">
                      {hit.username ? `@${hit.username}` : "(kullanıcı adı yok)"}
                    </span>
                    {hit.displayName && <span className="truncate text-xs text-text-secondary">{hit.displayName}</span>}
                  </span>
                  <span className={`${labelClass} shrink-0 ${hit.accountStatus && hit.accountStatus !== "ACTIVE" ? "text-primary" : ""}`}>
                    {hit.accountStatus ? (STATUS_LABEL[hit.accountStatus] ?? hit.accountStatus) : "-"}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
