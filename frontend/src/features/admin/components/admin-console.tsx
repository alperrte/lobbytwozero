"use client";

import { useCallback, useEffect, useState } from "react";
import { authApi, type AuthMe } from "@/features/auth";
import { AccountsPanel } from "./accounts-panel";
import { AdminLogin } from "./admin-login";
import { DeletionRequestsPanel } from "./deletion-requests-panel";
import { errorClass, labelClass, linkButton } from "./ui";

type Session = { status: "loading" } | { status: "guest" } | { status: "signed-in"; me: AuthMe };
type Tab = "requests" | "accounts";

/**
 * Yönetim paneli girişi: oturum yok → giriş formu; oturum var ama ADMIN rolü yok → erişim yok (çıkış yapılabilir);
 * ADMIN → dashboard. Rol kontrolü yalnız arayüz içindir; asıl yetki Auth'ta `ROLE_ADMIN` + zorunlu 2FA ile uygulanır.
 * Oturum, normal site ile aynı çerezleri paylaşır.
 */
export function AdminConsole() {
  const [session, setSession] = useState<Session>({ status: "loading" });

  const refresh = useCallback(async () => {
    try {
      setSession({ status: "signed-in", me: await authApi.getMe() });
    } catch {
      setSession({ status: "guest" });
    }
  }, []);

  useEffect(() => {
    let alive = true;
    authApi
      .getMe()
      .then((me) => alive && setSession({ status: "signed-in", me }))
      .catch(() => alive && setSession({ status: "guest" }));
    return () => {
      alive = false;
    };
  }, []);

  async function signOut() {
    try {
      await authApi.logout();
    } finally {
      setSession({ status: "guest" });
    }
  }

  if (session.status === "loading") {
    return <p role="status" className={`${labelClass} p-6`}>Yükleniyor…</p>;
  }
  if (session.status === "guest") {
    return <AdminLogin onSignedIn={() => void refresh()} />;
  }
  if (!session.me.roles.includes("ADMIN")) {
    return (
      <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-4 px-4">
        <p role="alert" className={errorClass}>Bu hesapla yönetim paneline girilemez.</p>
        <button type="button" onClick={() => void signOut()} className={`${linkButton} self-start`}>
          Çıkış yap
        </button>
      </main>
    );
  }
  return <Dashboard onSignOut={() => void signOut()} />;
}

function Dashboard({ onSignOut }: { onSignOut: () => void }) {
  const [tab, setTab] = useState<Tab>("requests");
  const [pending, setPending] = useState<number | null>(null);
  const [accountId, setAccountId] = useState<string | null>(null);

  const tabClass = (active: boolean) =>
    `inline-flex min-h-11 items-center gap-2 border-b-2 px-4 font-mono text-xs font-semibold uppercase tracking-[0.14em] transition-colors ${
      active ? "border-primary text-text-primary" : "border-transparent text-text-secondary hover:text-text-primary"
    }`;

  return (
    <div className="min-h-screen">
      <header className="border-b border-border border-t-2 border-t-primary bg-surface">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-3 px-4 py-3">
          <span className="font-display text-sm font-semibold uppercase tracking-[0.2em] text-text-primary">
            LTZ <span className="text-primary">{"//"}</span> Yönetim
          </span>
          <button type="button" onClick={onSignOut} className={linkButton}>Çıkış</button>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6">
        <section aria-label="Özet" className="flex flex-wrap gap-3">
          <div className="min-w-44 border border-border bg-surface p-4">
            <p className={labelClass}>Bekleyen talep</p>
            <p className={`mt-2 font-display text-3xl font-semibold tabular-nums ${pending ? "text-primary" : "text-text-primary"}`}>
              {pending ?? "–"}
            </p>
          </div>
        </section>

        <nav aria-label="Bölümler" className="flex border-b border-border">
          <button type="button" className={tabClass(tab === "requests")} aria-current={tab === "requests"} onClick={() => setTab("requests")}>
            Silme talepleri{pending ? ` · ${pending}` : ""}
          </button>
          <button type="button" className={tabClass(tab === "accounts")} aria-current={tab === "accounts"} onClick={() => setTab("accounts")}>
            Hesaplar
          </button>
        </nav>

        <div hidden={tab !== "requests"}>
          <DeletionRequestsPanel
            onCount={setPending}
            onOpenAccount={(id) => {
              setAccountId(id);
              setTab("accounts");
            }}
          />
        </div>
        <div hidden={tab !== "accounts"}>
          <AccountsPanel key={accountId ?? "none"} initialAccountId={accountId} />
        </div>
      </main>
    </div>
  );
}
