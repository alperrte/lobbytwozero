"use client";

import { useEffect } from "react";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { authApi, type SessionApi } from "../services/auth-api";
import { hasSessionHint } from "../services/session-hint";

type RedirectIfAuthenticatedProps = {
  /** Test için sahte API adaptörü. */
  api?: SessionApi;
};

/**
 * Yalnız misafirlerin görmesi gereken sayfalara (giriş, kayıt, şifremi unuttum) konur: oturum zaten açıksa ziyaretçiyi
 * ana sayfaya gönderir; giriş ekranı yalnız çıkış yapıldıktan sonra görünür. Oturum ipucu çerezi yoksa hiç istek atılmaz.
 * İpucu varsa `GET /auth/me` ile gerçekten açık mı diye SUNUCUYA sorulur (çerez tek başına yetmez); hata ya da 401'de sayfa
 * olduğu gibi kalır (kimlik varsayılmaz). Yalnız sayfa açılırken bir kez çalışır: formdan giriş yapınca `?next=`
 * yönlendirmesini bozmaz. Bu yalnız kullanıcı deneyimidir, güvenlik sınırı değildir (yetki sunucudadır).
 */
export function RedirectIfAuthenticated({ api = authApi }: RedirectIfAuthenticatedProps) {
  const router = useRouter();

  useEffect(() => {
    if (!hasSessionHint()) {
      return;
    }
    const controller = new AbortController();
    api
      .getMe(controller.signal)
      .then((me) => {
        if (me && !controller.signal.aborted) {
          router.replace("/");
        }
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [api, router]);

  return null;
}
