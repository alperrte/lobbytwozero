import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import type { GameDetail } from "../types/game";
import { GameMetadataDetails } from "./game-metadata-details";

describe("Game metadata görünümü", () => {
  it("veri kaynakları listesi ve IGDB atfı gösterilmez", () => {
    const base = { game: { title: "Fixture", coverUrl: null }, metadata: { developers: [], publishers: [], languages: ["tr"], genres: [], platforms: [], languageSupport: [], media: [] } };
    const withProvenance = (source: string) => ({ ...base, metadata: { ...base.metadata, provenance: [{ field: "RELEASE_DATE", source, dataState: "AVAILABLE", recordedAt: "2026-11-08T04:08:01Z", verifiedAt: null }] } });
    const view = render(<NextIntlClientProvider locale="tr" messages={messages}><GameMetadataDetails game={withProvenance("IGDB") as never} /></NextIntlClientProvider>);
    expect(screen.queryByText(messages.Game.dataSources)).toBeNull();
    expect(screen.queryByText(/Kaynaktan alındı:/)).toBeNull();
    expect(screen.queryByText(/Doğrulama:/)).toBeNull();
    expect(screen.queryByRole("link", { name: /IGDB/ })).toBeNull();
    expect(screen.queryByText(messages.Game.fields.languages)).toBeNull();
    view.unmount();
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameMetadataDetails game={withProvenance("ADMIN") as never} /></NextIntlClientProvider>);
    expect(screen.queryByRole("link", { name: /IGDB/ })).toBeNull();
  });
  it("kategori ve etiketler tür ve platformlarla aynı çip görünümünü kullanır", () => {
    const term = (id: number, code: string, name: string) => ({ id: `0198a3c2-00${id}0-7d44-9a55-3f1c2b4d5e66`, code, nameTr: name, nameEn: name });
    const detail = { game: { title: "Fixture", coverUrl: null }, metadata: { developers: [], publishers: [], languages: [], genres: [term(1, "rpg", "Rol yapma")], platforms: [term(2, "pc", "Bilgisayar")], categories: [term(3, "single", "Tek oyunculu")], tags: [term(4, "action", "Aksiyon")], languageSupport: [], media: [] } };
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameMetadataDetails game={detail as never} /></NextIntlClientProvider>);
    const chipClass = (name: string) => (screen.getByText(name).closest("a, span") as HTMLElement).className;
    for (const name of ["Tek oyunculu", "Aksiyon"]) {
      expect(chipClass(name)).toContain("rounded-full");
      expect(chipClass(name)).toContain("min-h-9");
      expect(screen.queryByRole("link", { name })).toBeNull();
    }
    expect(chipClass("Rol yapma")).toContain("rounded-full");
  });
  it("bilinmeyen dil desteğini desteklenmiyor yapmaz; gereksinim metnini HTML olarak çalıştırmaz", () => {
    const detail = { game: { title: "Fixture", coverUrl: null }, metadata: { developers: [], publishers: [], languages: [], genres: [], platforms: [], languageSupport: [{ languageCode: "tr", interfaceSupport: "SUPPORTED", audioSupport: "UNKNOWN", subtitleSupport: "UNSUPPORTED" }], provenance: [{ field: "RELEASE_DATE", source: "IGDB", verifiedAt: null }], media: [{ type: "TRAILER", url: "javascript:alert(1)" }], systemRequirements: [{ level: "MINIMUM", platform: "Windows", rawText: "<script>untrusted()</script>" }] } } as unknown as GameDetail;
    render(<NextIntlClientProvider locale="tr" messages={messages}><GameMetadataDetails game={detail} /></NextIntlClientProvider>);
    const row = within(screen.getByRole("row", { name: /^Türkçe/ }));
    const cells = row.getAllByRole("cell").map((cell) => cell.textContent);
    expect(cells).toEqual([messages.Game.support.SUPPORTED, messages.Game.support.UNKNOWN, messages.Game.support.UNSUPPORTED]);
    expect(screen.queryByText(messages.Game.unknownSupportNote)).toBeNull();
    expect(screen.getByText("<script>untrusted()</script>")).toBeVisible();
    expect(document.querySelector("script")).toBeNull();
    expect(screen.queryByRole("link", { name: messages.Game.openVideo })).toBeNull();
    expect(screen.queryByRole("link", { name: /IGDB/ })).toBeNull();
    expect(screen.queryByText(/RELEASE_DATE/)).toBeNull();
    expect(screen.queryByText(new RegExp(messages.Game.notVerified))).toBeNull();
  });
});
