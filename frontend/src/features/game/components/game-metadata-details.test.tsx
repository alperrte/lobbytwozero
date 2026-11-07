import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import type { GameDetail } from "../types/game";
import { GameMetadataDetails } from "./game-metadata-details";

describe("Game metadata görünümü", () => {
  it("bilinmeyen dil desteğini desteklenmiyor yapmaz; gereksinim metnini HTML olarak çalıştırmaz", () => {
    const detail = { game: { title: "Fixture", coverUrl: null }, metadata: { developers: [], publishers: [], languages: [], genres: [], platforms: [], languageSupport: [{ languageCode: "tr", interfaceSupport: "SUPPORTED", audioSupport: "UNKNOWN", subtitleSupport: "UNSUPPORTED" }], provenance: [{ field: "RELEASE_DATE", source: "IGDB", verifiedAt: null }], media: [{ type: "TRAILER", url: "javascript:alert(1)" }], systemRequirements: [{ level: "MINIMUM", platform: "Windows", rawText: "<script>untrusted()</script>" }] } } as unknown as GameDetail;
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameMetadataDetails game={detail} /></NextIntlClientProvider>);
    expect(screen.getByText(`${messages.Game.audio}: ${messages.Game.support.UNKNOWN}`)).toBeVisible();
    expect(screen.getByText(`${messages.Game.subtitles}: ${messages.Game.support.UNSUPPORTED}`)).toBeVisible();
    expect(screen.getByText("<script>untrusted()</script>")).toBeVisible();
    expect(document.querySelector("script")).toBeNull();
    expect(screen.queryByRole("link", { name: messages.Game.openVideo })).toBeNull();
    expect(screen.getByRole("link", { name: messages.Game.igdbAttribution })).toHaveAttribute("href", "https://www.igdb.com/");
    expect(screen.queryByText(/RELEASE_DATE/)).toBeNull();
    expect(screen.getByText(new RegExp(messages.Game.notVerified))).toBeVisible();
  });
});
