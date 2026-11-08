"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { usePagedList } from "../hooks/use-paged-list";
import { usePublishHandoff } from "../hooks/use-publish-handoff";
import { useViewer } from "../hooks/use-viewer";
import { fetchPostPage } from "../services/community-api";
import { completePublish } from "../services/publish-flow";
import { buildPostQuery, isInvalidRange } from "../services/post-query";
import { emptyFilters, type PostFilters } from "../types/community.types";
import { CommunityFrame, NewPostLink } from "./community-frame";
import { PostFilterBar } from "./post-filters";
import { PostList } from "./post-list";
import { PublishNotice } from "./publish-notice";
import { QuickShare } from "./quick-share";

/** Topluluk akışı: herkese açık gönderiler (misafir dahil). Yeni/Popüler/En eski sıralama, arama ve filtreler API'dedir. */
export function FeedScreen() {
  const t = useTranslations("Community");
  const viewer = useViewer();
  const [filters, setFilters] = useState<PostFilters>(emptyFilters);
  // Kişiye özel alan (likedByMe) için oturum durumu netleşince okunur; geçersiz tarih aralığı sunucuya gönderilmez.
  const key = viewer.status === "loading" ? null : `feed:${viewer.status}:${buildPostQuery(filters)}`;
  const list = usePagedList(key, (cursor, signal) => fetchPostPage(buildPostQuery(filters, { cursor }), signal));
  const filtered = JSON.stringify({ ...filters, sort: "NEWEST" }) !== JSON.stringify(emptyFilters);
  // Paylaşım sonrası: liste sunucudan okunup hazır olunca yükleme katmanı kalkar ve yeni gönderi yumuşakça belirir.
  const revealId = usePublishHandoff(!list.loading);

  return (
    <CommunityFrame title={t("feed.title")} description={t("feed.description")} action={<NewPostLink />}>
      <PublishNotice />
      <QuickShare
        viewer={viewer}
        onPublished={(postId) => {
          // Yeni gönderi sunucudan okunan listede görünür: süzgeç/sıralama varsa varsayılana dönülür (gönderi süzgece takılıp
          // gizlenmesin), yoksa liste yeniden okunur. Yerel kopya eklenmez; böylece kart yinelenmez. Her ikisi de listeyi
          // "yükleniyor" yapar ve `usePublishHandoff` katmanı liste hazır olunca kaldırır.
          if (JSON.stringify(filters) !== JSON.stringify(emptyFilters)) setFilters(emptyFilters);
          else list.reload();
          completePublish(postId);
        }}
      />
      <PostFilterBar filters={filters} onChange={setFilters} />
      <PostList
        list={list}
        viewer={viewer}
        revealId={revealId}
        comments
        emptyTitle={filtered ? t("feed.emptyFilteredTitle") : t("feed.emptyTitle")}
        emptyDescription={filtered ? (isInvalidRange(filters) ? t("filters.invalidRange") : t("feed.emptyFilteredBody")) : t("feed.emptyBody")}
        onReposted={(post) => {
          if (filters.sort === "NEWEST" && !filtered) list.prepend(post);
        }}
      />
    </CommunityFrame>
  );
}
