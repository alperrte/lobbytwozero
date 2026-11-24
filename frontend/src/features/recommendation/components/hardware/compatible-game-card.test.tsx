import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { CompatibleGame } from "../../types/recommendation";
import { CompatibleGameCard } from "./compatible-game-card";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string | { params: { gameId: string } }; children: ReactNode } & Record<string, unknown>) =>
    <a {...rest} href={typeof href === "string" ? href : `/games/${href.params.gameId}`}>{children}</a>
}));

const hw = tr.Recommendation.hardware;
const item = (patch: Partial<CompatibleGame> = {}): CompatibleGame => ({ gameId: "g-1", affinity: 4, matchedGenres: ["rpg"], compatibility: "UNKNOWN", reason: "REQUIREMENTS_MISSING", ...patch });
const card = { gameId: "g-1", title: "Kuzey Yolu", coverUrl: null };

function renderCard(props: Partial<Parameters<typeof CompatibleGameCard>[0]> = {}) {
  const onAssess = vi.fn();
  render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
    <CompatibleGameCard item={item()} rank={1} card={card} available genreLabel={(code) => `tür:${code}`} onAssess={onAssess} {...props} />
  </NextIntlClientProvider>);
  return onAssess;
}

describe("CompatibleGameCard", () => {
  it("UNKNOWN asla uyumlu gösterilmez: nötr 'Doğrulanamadı' ve gerekçe", () => {
    renderCard();
    const chip = screen.getByText(hw.compatibility.UNKNOWN);
    expect(chip.className).toContain("text-text-secondary");
    expect(chip.className).not.toContain("text-success");
    expect(screen.getByText(hw.compatibilityReason.REQUIREMENTS_MISSING)).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/uyumlu\b/i);
  });

  it("MEETS_MINIMUM_COMPUTE yalnız minimum CPU/GPU/RAM beyanını söyler", () => {
    renderCard({ item: item({ compatibility: "MEETS_MINIMUM_COMPUTE", reason: "MINIMUM_CPU_GPU_RAM_PROVEN" }) });
    expect(screen.getByText(hw.compatibility.MEETS_MINIMUM_COMPUTE).className).toContain("text-success");
    expect(screen.getByText(hw.compatibilityReason.MINIMUM_CPU_GPU_RAM_PROVEN)).toHaveTextContent("doğrulanmadı");
  });

  it("başlık oyun detayına gider, gerekçe eşleşen türleri yazar, sıra gösterilir", () => {
    renderCard();
    expect(screen.getByRole("link", { name: "Kuzey Yolu" })).toHaveAttribute("href", "/games/g-1");
    expect(screen.getByText(/tür:rpg/)).toBeInTheDocument();
    expect(screen.getByLabelText("1. sıra")).toBeInTheDocument();
  });

  it("katalogda olmayan oyun link vermez ve durumu açıkça yazar", () => {
    renderCard({ available: false });
    expect(screen.queryByRole("link")).toBeNull();
    expect(screen.getByText(hw.unavailable)).toBeInTheDocument();
  });

  it("değerlendirme düğmesi pencereyi açar", () => {
    const onAssess = renderCard();
    const button = screen.getByRole("button", { name: hw.assess });
    expect(button).toHaveAttribute("aria-haspopup", "dialog");
    fireEvent.click(button);
    expect(onAssess).toHaveBeenCalledTimes(1);
  });
});
