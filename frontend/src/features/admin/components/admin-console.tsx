"use client";

import { useCallback, useEffect, useState } from "react";
import { authApi, type AuthMe } from "@/features/auth";
import { AdminLogin } from "./admin-login";
import { AdminShell } from "./admin-shell";
import { errorClass, labelClass, linkButton } from "./ui";

type Session = { status: "loading" } | { status: "guest" } | { status: "signed-in"; me: AuthMe };

/**
 * Yönetim paneli girişi: oturum yok → giriş formu; oturum var ama ADMIN rolü yok → erişim yok (çıkış yapılabilir);
 * ADMIN → yan menülü panel (`AdminShell`). Rol kontrolü yalnız arayüz içindir; asıl yetki Auth'ta `ROLE_ADMIN` + zorunlu 2FA ile uygulanır.
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
  return <AdminShell onSignOut={() => void signOut()} />;
}
