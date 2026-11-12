import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { SessionContext, type SessionValue } from "../hooks/use-session";
import type { AuthMe } from "../types/auth.types";

const api = vi.hoisted(() => ({
  getMe: vi.fn(),
  verifyAge: vi.fn(),
  revokeAge: vi.fn(),
  refreshSession: vi.fn()
}));
vi.mock("../services/auth-api", () => ({ authApi: api }));

import { AccountAgeSection } from "./account-age-section";

const base: AuthMe = {
  accountId: "1",
  roles: ["PLAYER"],
  authMethods: ["PASSWORD"],
  accessTokenExpiresAt: "2030-01-01T00:00:00Z",
  accountTier: "B",
  trusted: true
};
const reload = vi.fn();

function renderSection(me: Partial<AuthMe> = {}) {
  api.getMe.mockResolvedValue({ ...base, ...me });
  const session = { status: "authenticated", me: base, logout: vi.fn(), reload } as SessionValue;
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <SessionContext.Provider value={session}>
        <AccountAgeSection />
      </SessionContext.Provider>
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  for (const mock of Object.values(api)) mock.mockReset();
  reload.mockReset();
  api.refreshSession.mockResolvedValue(undefined);
});
afterEach(cleanup);

describe("AccountAgeSection", () => {
  it("doğrulanmamışken etiketli, bday otomatik tamamlamalı tarih alanı gösterir", async () => {
    renderSection();
    const input = await screen.findByLabelText("Doğum tarihin");
    expect(input).toHaveAttribute("type", "date");
    expect(input).toHaveAttribute("autocomplete", "bday");
    expect(screen.getByRole("button", { name: "Yaşımı doğrula" })).toBeDisabled();
  });

  it("beyanı gönderir, tarihi temizler, tokenı yeniler ve oturumu yeniden yükler", async () => {
    api.verifyAge.mockResolvedValue(undefined);
    renderSection();
    fireEvent.change(await screen.findByLabelText("Doğum tarihin"), { target: { value: "1990-05-17" } });
    api.getMe.mockResolvedValue({ ...base, adultVerified: true });
    fireEvent.click(screen.getByRole("button", { name: "Yaşımı doğrula" }));

    expect(await screen.findByText("18 yaşından büyük olduğunu beyan ettin.")).toBeInTheDocument();
    expect(api.verifyAge).toHaveBeenCalledWith("1990-05-17");
    expect(api.refreshSession).toHaveBeenCalledTimes(1);
    expect(reload).toHaveBeenCalledTimes(1);
    expect(document.body.innerHTML).not.toContain("1990-05-17");
  });

  it("18 yaş altı beyanında hata gösterir ve formu korur", async () => {
    api.verifyAge.mockRejectedValue(new ApiHttpError(403, { code: "AGE_NOT_ELIGIBLE" }));
    renderSection();
    fireEvent.change(await screen.findByLabelText("Doğum tarihin"), { target: { value: "2015-01-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Yaşımı doğrula" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Yaş doğrulaması tamamlanamadı.");
    expect(api.refreshSession).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Doğum tarihin")).toBeInTheDocument();
  });

  it("güvence yetersizse (STEP_UP_REQUIRED) yönlendirici mesaj gösterir", async () => {
    api.verifyAge.mockRejectedValue(new ApiHttpError(403, { code: "STEP_UP_REQUIRED" }));
    renderSection();
    fireEvent.change(await screen.findByLabelText("Doğum tarihin"), { target: { value: "1990-05-17" } });
    fireEvent.click(screen.getByRole("button", { name: "Yaşımı doğrula" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("iki aşamalı doğrulamayı aç");
  });

  it("çok fazla denemede uyarır", async () => {
    api.verifyAge.mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 7200 }));
    renderSection();
    fireEvent.change(await screen.findByLabelText("Doğum tarihin"), { target: { value: "2015-01-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Yaşımı doğrula" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Çok fazla deneme");
  });

  it("doğrulanmış hesapta geri alma önce onay ister, onaylanınca çağırır", async () => {
    api.revokeAge.mockResolvedValue(undefined);
    renderSection({ adultVerified: true });
    fireEvent.click(await screen.findByRole("button", { name: "Doğrulamayı geri al" }));
    expect(api.revokeAge).not.toHaveBeenCalled();
    api.getMe.mockResolvedValue({ ...base, adultVerified: false });
    fireEvent.click(screen.getByRole("button", { name: "Evet, geri al" }));

    await waitFor(() => expect(api.revokeAge).toHaveBeenCalledTimes(1));
    expect(await screen.findByLabelText("Doğum tarihin")).toBeInTheDocument();
    expect(reload).toHaveBeenCalled();
  });

  it("token yenileme başarısız olsa da beyan başarılı sayılır", async () => {
    api.verifyAge.mockResolvedValue(undefined);
    api.refreshSession.mockRejectedValue(new ApiHttpError(401, { code: "SESSION_INVALID" }));
    renderSection();
    fireEvent.change(await screen.findByLabelText("Doğum tarihin"), { target: { value: "1990-05-17" } });
    api.getMe.mockResolvedValue({ ...base, adultVerified: true });
    fireEvent.click(screen.getByRole("button", { name: "Yaşımı doğrula" }));

    expect(await screen.findByText("Teşekkürler, yaşın doğrulandı.")).toBeInTheDocument();
  });

  it("durum alınamazsa hata gösterir", async () => {
    api.getMe.mockRejectedValue(new ApiHttpError(500, {}));
    render(
      <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
        <AccountAgeSection />
      </NextIntlClientProvider>
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Yaş bilgisi alınamadı.");
  });
});
