import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { SocialSessionGate } from "./social-session-gate";

const session = vi.hoisted(() => ({ status: "loading" as "loading" | "guest" | "authenticated" }));
vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: session.status }) }));

function renderGate() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <SocialSessionGate>
        <p>sosyal içerik</p>
      </SocialSessionGate>
    </NextIntlClientProvider>
  );
}

describe("SocialSessionGate", () => {
  it.each(["loading", "guest"] as const)("%s iken içeriği (ve sosyal istekleri) başlatmaz", (status) => {
    session.status = status;
    renderGate();
    expect(screen.queryByText("sosyal içerik")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Yükleniyor…");
  });

  it("oturum açıkken içeriği çizer", () => {
    session.status = "authenticated";
    renderGate();
    expect(screen.getByText("sosyal içerik")).toBeInTheDocument();
  });
});
