"use client";

import { useCallback, useEffect, useState } from "react";
import { authApi, type AdminDeletionRequest } from "@/features/auth";
import { adminErrorMessage } from "../services/admin-errors";
import { errorClass, labelClass, linkButton, okClass, outlineButton, primaryButton } from "./ui";

type Loaded =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; items: AdminDeletionRequest[] };

const dateFormat = new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium", timeStyle: "short" });

export function formatDate(value: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : dateFormat.format(date);
}

/**
 * B/C türü hesapların silme talebi kuyruğu. Onay geri alınamaz olduğundan iki adımlıdır; ret hesabı değiştirmez.
 * Yetki kararı Auth'tadır (ROLE_ADMIN); sayaç üst bileşene `onCount` ile bildirilir.
 */
export function DeletionRequestsPanel({
  onCount,
  onOpenAccount
}: {
  onCount: (count: number) => void;
  onOpenAccount: (accountId: string) => void;
}) {
  const [loaded, setLoaded] = useState<Loaded>({ status: "loading" });
  const [notice, setNotice] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const items = await authApi.listDeletionRequests(50, controller.signal);
        setLoaded({ status: "ready", items });
        onCount(items.length);
      } catch {
        if (!controller.signal.aborted) setLoaded({ status: "error" });
      }
    })();
    return () => controller.abort();
  }, [attempt, onCount]);

  const reload = useCallback(() => {
    setLoaded({ status: "loading" });
    setAttempt((value) => value + 1);
  }, []);

  if (loaded.status === "loading") {
    return <p role="status" className={labelClass}>Talepler yükleniyor…</p>;
  }
  if (loaded.status === "error") {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <p role="alert" className={errorClass}>Talepler alınamadı.</p>
        <button type="button" onClick={reload} className={linkButton}>Tekrar dene</button>
      </div>
    );
  }

  const items = loaded.items;
  return (
    <div className="flex flex-col gap-4">
      <p className="max-w-prose text-sm text-text-secondary">
        Google/Steam ya da iki adımlı doğrulama ile korunan hesapların silme talepleri. Onay, 14 günlük geri alma
        süresiyle silme sürecini başlatır; ret hesabı değiştirmez.
      </p>
      <div className="flex items-center justify-between gap-3">
        {notice ? <p role="status" className={okClass}>{notice}</p> : <span />}
        <button type="button" onClick={reload} className={linkButton}>Yenile</button>
      </div>
      {items.length === 0 ? (
        <p className="border border-dashed border-border p-6 text-center font-mono text-xs uppercase tracking-[0.14em] text-text-secondary">Bekleyen silme talebi yok</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {items.map((item) => (
            <RequestCard
              key={item.id}
              item={item}
              onOpenAccount={onOpenAccount}
              onDecided={(message) => {
                const rest = items.filter((other) => other.id !== item.id);
                setNotice(message);
                setLoaded({ status: "ready", items: rest });
                onCount(rest.length);
              }}
              onStale={reload}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

function RequestCard({
  item,
  onDecided,
  onStale,
  onOpenAccount
}: {
  item: AdminDeletionRequest;
  onDecided: (message: string) => void;
  onStale: () => void;
  onOpenAccount: (accountId: string) => void;
}) {
  const [note, setNote] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  async function decide(decision: "approve" | "reject") {
    setBusy(true);
    setProblem(null);
    try {
      const message = note.trim() || undefined;
      if (decision === "approve") await authApi.approveDeletionRequest(item.id, message);
      else await authApi.rejectDeletionRequest(item.id, message);
      onDecided(decision === "approve" ? "Talep onaylandı; silme süreci başlatıldı." : "Talep reddedildi.");
    } catch (error) {
      setProblem(adminErrorMessage(error));
      setConfirming(false);
      // Talep başkasınca işlenmiş ya da kaybolmuşsa liste bayattır; hata kısa süre görünür, sonra liste yenilenir.
      const status = (error as { status?: number }).status;
      if (status === 404 || status === 409) window.setTimeout(onStale, 2500);
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-4 border border-border border-l-2 border-l-primary bg-surface p-4">
      <dl className="grid gap-2 text-sm sm:grid-cols-2">
        <div>
          <dt className={labelClass}>Hesap</dt>
          <dd className="mt-1 break-all font-mono text-sm text-text-primary">{item.accountId}</dd>
        </div>
        <div>
          <dt className={labelClass}>Talep zamanı</dt>
          <dd className="mt-1 text-sm text-text-primary">{formatDate(item.createdAt)}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className={labelClass}>Kullanıcının notu</dt>
          <dd className="mt-1 whitespace-pre-wrap break-words text-sm text-text-primary">{item.message || "Not bırakılmamış."}</dd>
        </div>
      </dl>
      <button type="button" onClick={() => onOpenAccount(item.accountId)}
        className={`${linkButton} self-start`}>
        Hesabı incele →
      </button>
      <label className="flex flex-col gap-1 text-sm">
        <span className={labelClass}>Karar notu (isteğe bağlı, en fazla 500 karakter)</span>
        <textarea value={note} maxLength={500} rows={2} disabled={busy}
          onChange={(event) => setNote(event.target.value)}
          className="w-full rounded-sm border border-control-border bg-background px-3 py-2 text-text-primary focus-visible:border-accent focus-visible:outline-none" />
      </label>
      {problem && <p role="alert" className={errorClass}>{problem}</p>}
      {confirming ? (
        <div className="flex flex-col gap-2">
          <p className={errorClass}>
            Onay geri alınamaz: kullanıcının oturumları kapanır ve 14 günlük süre başlar.
          </p>
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={busy} onClick={() => void decide("approve")} className={primaryButton}>
              Evet, silmeyi başlat
            </button>
            <button type="button" disabled={busy} onClick={() => setConfirming(false)} className={outlineButton}>
              Vazgeç
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={busy} onClick={() => setConfirming(true)} className={primaryButton}>Onayla</button>
          <button type="button" disabled={busy} onClick={() => void decide("reject")} className={outlineButton}>Reddet</button>
        </div>
      )}
    </li>
  );
}
