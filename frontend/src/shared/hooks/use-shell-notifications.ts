"use client";

/**
 * Kabuğun bildirim sınırı. notification-service henüz bağlı değil; bu yüzden liste her zaman boştur
 * ve sahte bildirim ya da sayaç üretilmez. Servis bağlandığında yalnız bu hook gerçek veriyi döndürür.
 */
export type ShellNotification = {
  id: string;
  title: string;
  body?: string;
  read: boolean;
};

const EMPTY: readonly ShellNotification[] = [];

export function useShellNotifications(): readonly ShellNotification[] {
  return EMPTY;
}
