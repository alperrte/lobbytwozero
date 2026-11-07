import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { DeletionCancelApi } from "../services/auth-api";
import { CancelDeletionForm } from "./cancel-deletion-form";

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});

function renderForm(cancelAccountDeletion: DeletionCancelApi["cancelAccountDeletion"]) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <CancelDeletionForm api={{ cancelAccountDeletion }} />
    </NextIntlClientProvider>
  );
}

const identifier = () => screen.getByLabelText("E-posta veya kullanıcı adı");
const password = () => screen.getByLabelText("Şifre", { exact: true }) as HTMLInputElement;
const code = () => screen.getByLabelText("2FA kodu (varsa)") as HTMLInputElement;
const submit = () => screen.getByRole("button", { name: /Silmeyi iptal et/ });

function fill(id = "ada", pass = "dogru-parola-123456") {
  fireEvent.change(identifier(), { target: { value: id } });
  fireEvent.change(password(), { target: { value: pass } });
}

describe("CancelDeletionForm", () => {
  it("kimlik ve parola ile vazgeçer; başarı mesajı ve giriş bağlantısı çıkar", async () => {
    const cancel = vi.fn().mockResolvedValue(undefined);
    renderForm(cancel);
    fill(" ada ");

    fireEvent.click(submit());

    expect(await screen.findByRole("status")).toHaveTextContent("Silme talebin iptal edildi");
    expect(cancel).toHaveBeenCalledWith("ada", "dogru-parola-123456", undefined);
    expect(screen.getByRole("link", { name: "Giriş yap" })).toHaveAttribute("href", "/login");
  });

  it("TOTP kodunu code, kurtarma kodunu recoveryCode olarak gönderir", async () => {
    const cancel = vi.fn().mockRejectedValue(new ApiHttpError(400, { code: "INVALID_TWO_FACTOR_CODE" }));
    renderForm(cancel);
    fill();

    fireEvent.change(code(), { target: { value: "123456" } });
    fireEvent.click(submit());
    await waitFor(() => expect(cancel).toHaveBeenCalledTimes(1));
    expect(cancel).toHaveBeenLastCalledWith("ada", "dogru-parola-123456", { code: "123456" });

    fill();
    fireEvent.change(code(), { target: { value: "ab12c-de34f" } });
    fireEvent.click(submit());
    await waitFor(() => expect(cancel).toHaveBeenCalledTimes(2));
    expect(cancel).toHaveBeenLastCalledWith("ada", "dogru-parola-123456", { recoveryCode: "AB12C-DE34F" });
  });

  it("boş alanlarda ve bozuk kodda istek atmaz", async () => {
    const cancel = vi.fn();
    renderForm(cancel);

    fireEvent.click(submit());
    expect(await screen.findByText("E-posta adresini veya kullanıcı adını gir.")).toBeInTheDocument();
    expect(identifier()).toHaveFocus();

    fill();
    fireEvent.change(code(), { target: { value: "12ab" } });
    fireEvent.click(submit());
    expect(await screen.findByText(/6 haneli bir kod ya da XXXXX-XXXXX/)).toBeInTheDocument();
    expect(cancel).not.toHaveBeenCalled();
  });

  it.each([
    ["DELETION_NOT_PENDING", 409, /bekleyen bir silme talebi yok/],
    ["DELETION_CANCEL_WINDOW_CLOSED", 409, /Geri alma süresi doldu/],
    ["INVALID_TWO_FACTOR_CODE", 400, /kodu yanlış/],
    ["INVALID_CREDENTIALS", 401, /şifre hatalı/]
  ])("%s kodunu çevrilmiş mesajla gösterir", async (errorCode, status, pattern) => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(status, { code: errorCode })));
    fill();

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(pattern);
  });

  it("parola hata sonrası alandan silinir", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(401, { code: "INVALID_CREDENTIALS" })));
    fill();

    fireEvent.click(submit());

    await screen.findByRole("alert");
    expect(password().value).toBe("");
  });

  it("429'da geri sayım gösterir ve düğmeyi pasif yapar", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 30 })));
    fill();

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("30 saniye sonra tekrar dene");
    expect(screen.getByRole("button", { name: /30 saniye sonra tekrar dene/ })).toBeDisabled();
  });
});
