import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { AccountDeletionApi } from "../services/auth-api";
import { forgetFragmentToken } from "../services/fragment-token";
import { DeleteAccountForm } from "./delete-account-form";

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});

const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdEf";

function open(hash: string) {
  window.history.replaceState(null, "", `/account/delete${hash}`);
}

function renderForm(confirmAccountDeletion: AccountDeletionApi["confirmAccountDeletion"]) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <DeleteAccountForm api={{ confirmAccountDeletion }} />
    </NextIntlClientProvider>
  );
}

const username = () => screen.getByLabelText("Kullanıcı adın");
const submit = () => screen.getByRole("button", { name: /Hesabımı sil/ });

beforeEach(() => {
  forgetFragmentToken();
  window.history.replaceState(null, "", "/");
});

describe("DeleteAccountForm", () => {
  it("token yoksa form yerine geçersiz bağlantı mesajı gösterir", async () => {
    open("");
    renderForm(vi.fn());
    expect(await screen.findByRole("alert")).toHaveTextContent("geçersiz ya da süresi dolmuş");
    expect(screen.queryByLabelText("Kullanıcı adın")).toBeNull();
  });

  it("token adresten silinir; kullanıcı adı ile onaylanınca sonuç ekranı çıkar", async () => {
    open(`#token=${TOKEN}`);
    const confirm = vi.fn().mockResolvedValue(undefined);
    renderForm(confirm);
    await waitFor(() => expect(username()).toBeInTheDocument());
    expect(window.location.hash).toBe("");

    fireEvent.change(username(), { target: { value: " ada " } });
    fireEvent.click(submit());

    expect(await screen.findByRole("status")).toHaveTextContent("Silme talebin alındı");
    expect(confirm).toHaveBeenCalledWith(TOKEN, "ada");
    expect(screen.getByText(/14 gün içinde/)).toBeInTheDocument();
  });

  it("boş kullanıcı adında istek atmaz", async () => {
    open(`#token=${TOKEN}`);
    const confirm = vi.fn();
    renderForm(confirm);
    await waitFor(() => expect(username()).toBeInTheDocument());

    fireEvent.click(submit());

    expect(await screen.findByText("Bir kullanıcı adı belirle.")).toBeInTheDocument();
    expect(confirm).not.toHaveBeenCalled();
  });

  it.each([
    ["DELETION_CONFIRMATION_INVALID", 400, /Kullanıcı adı eşleşmedi/],
    ["ADMIN_DELETION_FORBIDDEN", 409, /Yönetici hesabı silinemez/]
  ])("%s kodunu çevrilmiş mesajla gösterir; form yerinde kalır", async (code, status, pattern) => {
    open(`#token=${TOKEN}`);
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(status, { code })));
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "ada" } });

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(pattern);
    expect(username()).toBeInTheDocument();
  });

  it("401'de giriş yapma yönlendirmesi gösterir", async () => {
    open(`#token=${TOKEN}`);
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(401, { code: "UNAUTHENTICATED" })));
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "ada" } });

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("giriş yapmış olmalısın");
    expect(screen.getByRole("link", { name: "Giriş yap" })).toHaveAttribute("href", "/login");
  });
});
