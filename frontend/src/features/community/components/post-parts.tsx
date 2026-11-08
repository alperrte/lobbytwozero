"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { imageSrc } from "../services/community-api";
import { DELETED_ACCOUNT_ID, type AuthorCard, type GameCard, type PostImage, type RepostSource } from "../types/community.types";
import { Avatar, authorLabel, chipClass, RelativeTime } from "./community-ui";
import { ImageViewer, MediaCarousel, type CarouselVariant, type MediaItem } from "./media-carousel";

/** Liste/detay düzeyinde toplu çözümlenen yazar ve oyun kartları (kart başına ayrı istek atılmaz). */
export type Lookups = {
  author: (id: string | null | undefined) => AuthorCard | null;
  game: (id: string | null | undefined) => GameCard | null;
};

/** Yazar adı çözülemezse (misafir, engelli, silinmiş profil) dürüst bir yedek etiket kullanılır; ad uydurulmaz. */
export function useAuthorLabel(lookups: Lookups) {
  const t = useTranslations("Community.author");
  return (id: string | null | undefined) => {
    if (!id || id === DELETED_ACCOUNT_ID) return { label: t("deleted"), author: null, linkable: false };
    const author = lookups.author(id);
    return { label: authorLabel(author, t("unknown")), author, linkable: true };
  };
}

export function AuthorLine({
  authorId,
  lookups,
  createdAt,
  size = "md",
  trailing
}: {
  authorId: string | null | undefined;
  lookups: Lookups;
  createdAt?: string;
  size?: "sm" | "md";
  trailing?: React.ReactNode;
}) {
  const describe = useAuthorLabel(lookups);
  const { label, author, linkable } = describe(authorId);
  const name = <span className="truncate font-display text-[0.9375rem] font-semibold text-text-primary">{label}</span>;
  return (
    <div className="flex min-w-0 items-center gap-3">
      <Avatar author={author} label={label} size={size} />
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
          {linkable && authorId ? (
            <Link
              href={{ pathname: "/community/users/[userId]", params: { userId: authorId } }}
              className="min-w-0 rounded-control hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
            >
              {name}
            </Link>
          ) : (
            name
          )}
          {createdAt ? <RelativeTime value={createdAt} className="text-xs text-text-secondary" /> : null}
          {trailing}
        </div>
      </div>
    </div>
  );
}

export function GameChip({ gameId, lookups }: { gameId: string | null | undefined; lookups: Lookups }) {
  const t = useTranslations("Community.game");
  if (!gameId) return null;
  const game = lookups.game(gameId);
  return (
    <span className={chipClass}>
      <Icon name="gamepad" className="size-3.5 shrink-0" />
      <span className="truncate">{game?.title ?? t("linked")}</span>
    </span>
  );
}

/** Gönderi görseli: sunucunun bildirdiği boyutlar yerleşimi sabitler; yüklenemezse yer tutucu gösterilir. */
export function PostImageView({ image, index, className }: { image: PostImage; index: number; className?: string }) {
  const t = useTranslations("Community.images");
  const [broken, setBroken] = useState(false);
  const alt = t("alt", { number: index + 1 });
  if (broken) {
    return (
      <div className={cn("flex aspect-video items-center justify-center rounded-control border border-border bg-surface-elevated px-3 text-center text-sm text-text-secondary", className)}>
        {t("broken")}
      </div>
    );
  }
  const img = (
    // Gateway görsel yolu (kimliksiz okunabilir); boyutlar API'den gelir, optimizasyon vekili uygulanamaz.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={imageSrc(image)}
      alt={alt}
      width={image.width}
      height={image.height}
      loading="lazy"
      decoding="async"
      onError={() => setBroken(true)}
      className={cn("h-auto max-h-[28rem] w-full rounded-control border border-border bg-surface-elevated object-cover", className)}
    />
  );
  return img;
}

/** Sunucu görsellerini carousel öğelerine çevirir (sıra: `sortOrder`); alt metin genel ve numaralıdır. */
export function toMediaItems(images: readonly PostImage[], alt: (number: number) => string): MediaItem[] {
  return [...images]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((image, index) => ({ key: image.id, src: imageSrc(image), alt: alt(index + 1), width: image.width, height: image.height }));
}

/** Yayınlanmış gönderi görselleri: ortak carousel + tam boy görüntüleyici (tek görselde de aynı çerçeve). */
export function PostImages({ images, variant = "feed" }: { images: PostImage[]; variant?: CarouselVariant }) {
  const t = useTranslations("Community.images");
  const [viewer, setViewer] = useState<number | null>(null);
  const items = useMemo(() => toMediaItems(images, (number) => t("alt", { number })), [images, t]);
  if (items.length === 0) return null;
  const label = t("galleryLabel", { count: items.length });
  return (
    <div className="mt-3">
      <MediaCarousel items={items} variant={variant} label={label} onOpen={setViewer} />
      <ImageViewer items={items} startIndex={viewer} label={label} onClose={() => setViewer(null)} />
    </div>
  );
}

/** Repost'un orijinali: açık atıf. Erişilemeyen orijinal için yalnız dürüst bir yer tutucu gösterilir. */
export function RepostBlock({ source, lookups }: { source: RepostSource; lookups: Lookups }) {
  const t = useTranslations("Community.post");
  if (!source.available) {
    return (
      <p className="mt-3 rounded-control border border-dashed border-border px-4 py-3 text-sm text-text-secondary">
        {t("repostUnavailable")}
      </p>
    );
  }
  return (
    <div className="mt-3 rounded-control border border-border bg-surface-elevated/60 p-4">
      <AuthorLine authorId={source.authorId} lookups={lookups} createdAt={source.createdAt} size="sm" trailing={<GameChip gameId={source.gameId} lookups={lookups} />} />
      {source.body ? <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-text-primary">{source.body}</p> : null}
      <PostImages images={source.images ?? []} variant="preview" />
      <Link
        href={{ pathname: "/community/posts/[postId]", params: { postId: source.postId } }}
        className="mt-2 inline-flex min-h-11 items-center text-sm font-medium text-brand-text underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
      >
        {t("openOriginal")}
      </Link>
    </div>
  );
}
