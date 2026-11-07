"use client";

import { createContext } from "react";

/**
 * Kabuğun (navbar profil menüsü) oturum sınırı. Kabuk `features/*` içeriğini import edemez; oturum katmanı
 * (`features/auth` SessionProvider) bu bağlamı doldurur, kabuk `useShellUser()`/`useShellSession()` ile okur.
 * Sağlayıcı yoksa (testler, oturum katmanı olmayan sayfalar) kullanıcı `null` (misafir) kalır ve sahte ad/fotoğraf
 * üretilmez.
 */
export type ShellUser = {
  displayName: string;
  avatarUrl?: string;
  /** `true` ise ad baş harfleri avatarda gösterilmez (ad henüz gerçek profilden gelmiyorsa anlamsız harf çıkmasın). */
  hideInitials?: boolean;
};

export type ShellSession = {
  user: ShellUser | null;
  /** Oturum durumu henüz bilinmiyor (ilk yükleme); menü giriş/çıkış eylemi göstermez. */
  loading?: boolean;
  /** Tanımlıysa oturum katmanı bağlıdır: menü giriş bağlantısı ve çalışan çıkış eylemi gösterir. */
  logout?: () => Promise<void>;
};

export const ShellSessionContext = createContext<ShellSession>({ user: null });
