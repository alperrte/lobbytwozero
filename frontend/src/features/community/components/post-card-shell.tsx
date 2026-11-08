"use client";

import type { ReactNode } from "react";
import { cn } from "@/shared/utils/cn";

/**
 * Tüm gönderi kartlarının (Akış, Gönderilerim, oyuncu gönderileri, forum konuları, detay) ortak kabuğu: aynı iç boşluk ve aynı
 * içerik sırası. Kart, bulunduğu ortak içerik containerının (bkz. `CommunityFrame`: mobilde %96, sm ve üstünde %93, ortalı) tam
 * genişliğidir; başlık, sekmeler, hızlı paylaşım ve araç çubukları da aynı containerda olduğundan kenarları kartlarla hizalıdır.
 * Köşeleri sidebar ile aynı yumuşak yarıçaptadır (`rounded-2xl`). Her satırda tek kart bulunur; yükseklik içeriğin doğal yüksekliğidir.
 * Kart içeriğindeki uzun kelimeler taşmaz (`min-w-0`). Menüler kesilmesin diye `overflow` kapatılmaz.
 */
export function PostCardShell({ labelledBy, className, children }: { labelledBy: string; className?: string; children: ReactNode }) {
  return (
    <article
      aria-labelledby={labelledBy}
      className={cn("w-full min-w-0 rounded-2xl border border-border bg-surface p-4 sm:px-6 sm:py-5", className)}
    >
      {children}
    </article>
  );
}
