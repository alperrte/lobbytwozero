"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { cn } from "@/shared/utils/cn";
import { useAuthors } from "../hooks/use-directory";
import { usePagedList } from "../hooks/use-paged-list";
import { useViewer } from "../hooks/use-viewer";
import { fetchPostPage } from "../services/community-api";
import { buildPostQuery, isUuid } from "../services/post-query";
import { emptyFilters, type PostFilters } from "../types/community.types";
import { CommunityFrame } from "./community-frame";
import { Avatar, authorLabel, EmptyBlock, panel } from "./community-ui";
import { PostFilterBar } from "./post-filters";
import { PostList } from "./post-list";

/**
 * Başka bir oyuncunun herkese açık gönderi geçmişi (kendi sayfam için bkz. `MyPostsScreen`). Sunucunun döndürdüğü liste
 * olduğu gibi gösterilir; yeniden paylaşımlar orijinal yazar atfıyla kartlarda görünür.
 */
export function UserPostsScreen({ userId }: { userId: string }) {
  const t = useTranslations("Community");
  const session = useRequireSession();
  const viewer = useViewer();
  const valid = isUuid(userId);
  const [filters, setFilters] = useState<PostFilters>(emptyFilters);
  const key = valid && viewer.status !== "loading" ? `user:${userId}:${viewer.status}:${buildPostQuery(filters)}` : null;
  const list = usePagedList(key, (cursor, signal) => fetchPostPage(buildPostQuery(filters, { cursor }), signal, userId));
  const author = useAuthors([userId], viewer.status === "authenticated")(userId);

  if (session.status !== "authenticated") {
    return (
      <CommunityFrame title={t("user.title")} description={t("user.description")}>
        <EmptyBlock title={t("state.loading")} />
      </CommunityFrame>
    );
  }

  const label = authorLabel(author, t("author.unknown"));
  const filtered = JSON.stringify({ ...filters, sort: "NEWEST" }) !== JSON.stringify(emptyFilters);

  return (
    <CommunityFrame title={t("user.title")} description={t("user.description")}>
      <div className={cn(panel, "mb-5 flex items-center gap-4 p-4")}>
        <Avatar author={author} label={label} size="lg" />
        <div className="min-w-0">
          <p className="truncate font-display text-xl font-semibold text-text-primary">{label}</p>
          {author?.username ? <p className="truncate text-sm text-text-secondary">@{author.username}</p> : null}
        </div>
      </div>

      <PostFilterBar filters={filters} onChange={setFilters} options={{ category: true, dates: true }} />

      <PostList
        list={list}
        viewer={viewer}
        emptyTitle={filtered ? t("mine.emptyFilteredTitle") : t("user.emptyTitle")}
        emptyDescription={filtered ? t("mine.emptyFilteredBody") : t("user.emptyBody")}
      />
    </CommunityFrame>
  );
}
