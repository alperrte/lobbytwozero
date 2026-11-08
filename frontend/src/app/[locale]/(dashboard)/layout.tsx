import type { Metadata } from "next";
import type { ReactNode } from "react";
import { SessionProvider } from "@/features/auth";

// Kişisel / giriş gerektiren alan: arama motorlarına kapalı.
export const metadata: Metadata = {
  robots: { index: false, follow: false }
};

/**
 * Oturum katmanı bu grupta da kurulur (`(public)` ile aynı): kabuktaki profil menüsü giriş yapmış kullanıcıyı gösterir ve
 * sayfalar `useSession` / `useRequireSession` kullanabilir. Grup değişince (ör. giriş sayfasından buraya) katman yeniden
 * kurulup oturum tazelenir; grup içi gezinmede korunur.
 */
export default function PrivateGroupLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  return <SessionProvider>{children}</SessionProvider>;
}
