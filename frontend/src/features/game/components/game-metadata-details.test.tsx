import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import type { GameDetail } from "../types/game";
import { GameMetadataDetails } from "./game-metadata-details";

describe("Game metadata görünümü", () => {
  it("sağlayıcıdan alınan veriyi 'doğrulanmadı' yerine alınma tarihiyle gösterir; yalnız gerçek doğrulama 'Doğrulama' der", () => {
    const detail = { game: { title: "Fixture", coverUrl: null }, metadata: { developers: [], publishers: [], languages: [], genres: [], platforms: [], languageSupport: [],
      provenance: [{ field: "RELEASE_DATE", source: "IGDB", dataState: "AVAILABLE", recordedAt: "2026-11-08T04:08:01Z", verifiedAt: null },
        { field: "TITLE", source: "ADMIN", dataState: "AVAILABLE", recordedAt: "2026-11-08T04:08:01Z", verifiedAt: "2026-11-08T05:00:00Z" }], media: [] } };
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameMetadataDetails game={detail as never} /></NextIntlClientProvider>);
    expect(screen.getByText(/Kaynaktan alındı:/)).toBeVisible();
    expect(screen.getByText(/Doğrulama:/)).toBeVisible();
    expect(screen.queryByText(new RegExp(messages.Game.notVerified))).toBeNull();
  });
  it("bilinmeyen dil desteğini desteklenmiyor yapmaz; gereksinim metnini HTML olarak çalıştırmaz", () => {
    const detail = { game: { title: "Fixture", coverUrl: null }, metadata: { developers: [], publishers: [], languages: [], genres: [], platforms: [], languageSupport: [{ languageCode: "tr", interfaceSupport: "SUPPORTED", audioSupport: "UNKNOWN", subtitleSupport: "UNSUPPORTED" }], provenance: [{ field: "RELEASE_DATE", source: "IGDB", verifiedAt: null }], media: [{ type: "TRAILER", url: "javascript:alert(1)" }], systemRequirements: [{ level: "MINIMUM", platform: "Windows", rawText: "<script>untrusted()</script>" }] } } as unknown as GameDetail;
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameMetadataDetails game={detail} /></NextIntlClientProvider>);
    const row = within(screen.getByRole("row", { name: /^Türkçe/ }));
    const cells = row.getAllByRole("cell").map((cell) => cell.textContent);
    expect(cells).toEqual([messages.Game.support.SUPPORTED, messages.Game.support.UNKNOWN, messages.Game.support.UNSUPPORTED]);
    expect(screen.getByText(messages.Game.unknownSupportNote)).toBeVisible();
    expect(screen.getByText("<script>untrusted()</script>")).toBeVisible();
    expect(document.querySelector("script")).toBeNull();
    expect(screen.queryByRole("link", { name: messages.Game.openVideo })).toBeNull();
    expect(screen.getByRole("link", { name: messages.Game.igdbAttribution })).toHaveAttribute("href", "https://www.igdb.com/");
    expect(screen.queryByText(/RELEASE_DATE/)).toBeNull();
    expect(screen.getByText(new RegExp(messages.Game.notVerified))).toBeVisible();
  });
});
