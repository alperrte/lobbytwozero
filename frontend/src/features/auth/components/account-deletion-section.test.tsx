import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { AuthMe } from "../types/auth.types";

const api = vi.hoisted(() => ({
  getMe: vi.fn(),
  requestAccountDeletion: vi.fn(),
  requestDeletionSupport: vi.fn(),
  getDeletionSupportRequest: vi.fn()
}));
vi.mock("../services/auth-api", () => ({ authApi: api }));

import { AccountDeletionSection } from "./account-deletion-section";

const base: AuthMe = {
  accountId: "1",
  roles: ["USER"],
  authMethods: ["PASSWORD"],
  accessTokenExpiresAt: "2030-01-01T00:00:00Z",
  accountTier: "A",
  passwordSet: true,
  providers: [],
  totpEnabled: false,
  registrationMethod: "PASSWORD"
};

function renderSection(me: Partial<AuthMe> = {}) {
  api.getMe.mockResolvedValue({ ...base, ...me });
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <AccountDeletionSection />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  for (const mock of Object.values(api)) mock.mockReset();
  api.getDeletionSupportRequest.mockResolvedValue(null);
});
afterEach(cleanup);

describe("AccountDeletionSection", () => {
  it("A türü: gönder önce onay penceresi açar, vazgeçince istek atılmaz", async () => {
    renderSection();
    fireEvent.change(await screen.findByLabelText("Parolan"), { target: { value: "gizli-parola" } });
    fireEvent.click(screen.getByRole("button", { name: "Silme isteği gönder" }));

    expect(screen.getByRole("dialog", { name: "Hesabın silinsin mi?" })).toBeInTheDocument();
    expect(api.requestAccountDeletion).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(api.requestAccountDeletion).not.toHaveBeenCalled();
  });

  it("A türü: onaylayınca isteği atar ve e-posta bilgisini gösterir", async () => {
    api.requestAccountDeletion.mockResolvedValue(undefined);
    renderSection();
    fireEvent.change(await screen.findByLabelText("Parolan"), { target: { value: "gizli-parola" } });
    fireEvent.click(screen.getByRole("button", { name: "Silme isteği gönder" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, devam et" }));

    await waitFor(() => expect(api.requestAccountDeletion).toHaveBeenCalledWith({ password: "gizli-parola" }));
    expect(await screen.findByText(/Onay bağlantısını e-postana gönderdik/)).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("B türü: talep açmadan önce onay penceresi çıkar", async () => {
    api.requestDeletionSupport.mockResolvedValue({ status: "PENDING", createdAt: "2026-11-10T10:00:00Z" });
    renderSection({ accountTier: "B", providers: ["google"] });
    fireEvent.change(await screen.findByLabelText("Parolan"), { target: { value: "gizli-parola" } });
    fireEvent.click(screen.getByRole("button", { name: "Silme talebi aç" }));

    expect(screen.getByRole("dialog", { name: "Hesabın silinsin mi?" })).toBeInTheDocument();
    expect(api.requestDeletionSupport).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Evet, devam et" }));
    await waitFor(() => expect(api.requestDeletionSupport).toHaveBeenCalledTimes(1));
  });
});
