"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { safeNextPath } from "../schemas/safe-next-path";
import { useSession, type SessionValue } from "./use-session";

/**
 * KORUMALI SAYFALAR İÇİN ORTAK YARDIMCI (tüm servislerin sayfaları kullanabilir).
 *
 * Bir sayfanın içinde çağır; oturumsuz (guest) ziyaretçiyi `/login?next=<bu sayfa>` adresine yönlendirir, girişten sonra
 * kullanıcı bu sayfaya döner. `next` yalnız uygulama içi güvenli yollar için eklenir (açık yönlendirme engellenir).
 * Dönen değeri kullan: yalnız `status === "authenticated"` iken sayfa içeriğini çiz; `loading` ve `guest` iken hiçbir şey
 * (ya da iskelet) göster. Bu yalnız KULLANICI DENEYİMİDİR, güvenlik sınırı değildir: yetki kararı her zaman ilgili
 * servisin sunucu tarafındadır (rol bilgisi `session.me.roles` arayüz içindir).
 *
 * Gereksinim: sayfa `SessionProvider` içinde olmalı (`app/[locale]/(public)/layout.tsx` zaten sarar).
 *
 *   const session = useRequireSession();
 *   if (session.status !== "authenticated") return null;
 */
export function useRequireSession(): SessionValue {
  const session = useSession();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (session.status !== "guest") {
      return;
    }
    const next = safeNextPath(pathname);
    router.replace(next ? { pathname: "/login", query: { next } } : "/login");
  }, [session.status, pathname, router]);

  return session;
}
