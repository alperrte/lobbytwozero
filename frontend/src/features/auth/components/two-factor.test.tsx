import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { TwoFactorApi } from "../services/auth-api";
import { TwoFactor } from "./two-factor";

function api(overrides: Partial<TwoFactorApi> = {}): TwoFactorApi {
  return {
    verifyTwoFactor: vi.fn().mockResolvedValue(undefined),
    startTwoFactorEnrollment: vi
      .fn()
      .mockResolvedValue({ secret: "JBSWY3DPEHPK3PXP", otpauthUri: "otpauth://totp/LTZ:ada?secret=JBSWY3DPEHPK3PXP" }),
    completeTwoFactorEnrollment: vi.fn().mockResolvedValue({ recoveryCodes: ["AAAAA-BBBBB", "CCCCC-DDDDD"] }),
    ...overrides
  };
}

function wrap(kind: "verify" | "enroll", fake: TwoFactorApi) {
  const handlers = { onAuthenticated: vi.fn(), onCancel: vi.fn() };
  render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <TwoFactor challenge={{ kind, id: "ch-1" }} api={fake} {...handlers} />
    </NextIntlClientProvider>
  );
  return handlers;
}

describe("TwoFactor: giriş adımı", () => {
  it("6 haneli kodu gönderir ve başarıda onAuthenticated çağırır; kod alandan silinir", async () => {
    const fake = api();
    const { onAuthenticated } = wrap("verify", fake);
    const input = screen.getByLabelText("Doğrulama kodu") as HTMLInputElement;

    fireEvent.change(input, { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /Doğrula/ }));

    await waitFor(() => expect(onAuthenticated).toHaveBeenCalled());
    expect(fake.verifyTwoFactor).toHaveBeenCalledWith("ch-1", { code: "123456" });
    expect(input.value).toBe("");
  });

  it("geçersiz biçimde istek atmaz ve hata gösterir", async () => {
    const fake = api();
    wrap("verify", fake);
    fireEvent.change(screen.getByLabelText("Doğrulama kodu"), { target: { value: "12ab" } });
    fireEvent.click(screen.getByRole("button", { name: /Doğrula/ }));

    expect(await screen.findByText("6 haneli bir kod gir.")).toBeInTheDocument();
    expect(fake.verifyTwoFactor).not.toHaveBeenCalled();
  });

  it("kurtarma koduna geçer; büyük harfe çevirip recoveryCode olarak gönderir", async () => {
    const fake = api();
    const { onAuthenticated } = wrap("verify", fake);
    fireEvent.click(screen.getByRole("button", { name: "Kurtarma kodu kullan" }));

    fireEvent.change(screen.getByLabelText("Kurtarma kodu"), { target: { value: "ab12c-de34f" } });
    fireEvent.click(screen.getByRole("button", { name: /Doğrula/ }));

    await waitFor(() => expect(onAuthenticated).toHaveBeenCalled());
    expect(fake.verifyTwoFactor).toHaveBeenCalledWith("ch-1", { recoveryCode: "AB12C-DE34F" });
  });

  it("MFA_FAILED (yanlış kod / bitmiş challenge) çevrilmiş hata ve 'Girişe dön' gösterir; oturum açmaz", async () => {
    const fake = api({ verifyTwoFactor: vi.fn().mockRejectedValue(new ApiHttpError(401, { code: "MFA_FAILED" })) });
    const { onAuthenticated, onCancel } = wrap("verify", fake);
    fireEvent.change(screen.getByLabelText("Doğrulama kodu"), { target: { value: "111111" } });
    fireEvent.click(screen.getByRole("button", { name: /Doğrula/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Kod geçersiz ya da doğrulama oturumunun süresi doldu");
    expect(onAuthenticated).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Girişe dön" }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("429'da geri sayım gösterir ve düğmeyi pasif yapar", async () => {
    const fake = api({
      verifyTwoFactor: vi.fn().mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 30 }))
    });
    wrap("verify", fake);
    fireEvent.change(screen.getByLabelText("Doğrulama kodu"), { target: { value: "111111" } });
    fireEvent.click(screen.getByRole("button", { name: /Doğrula/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("30 saniye sonra tekrar dene");
    expect(screen.getByRole("button", { name: /30 saniye sonra tekrar dene/ })).toBeDisabled();
  });
});

describe("TwoFactor: zorunlu kurulum", () => {
  it("başlat → anahtar → kodla etkinleştir → kurtarma kodları → onay kutusu olmadan devam edilemez", async () => {
    const fake = api();
    const { onAuthenticated } = wrap("enroll", fake);

    fireEvent.click(screen.getByRole("button", { name: "Kurulumu başlat" }));
    expect(await screen.findByTestId("totp-secret")).toHaveTextContent("JBSWY3DPEHPK3PXP");
    expect(fake.startTwoFactorEnrollment).toHaveBeenCalledWith("ch-1");

    fireEvent.change(screen.getByLabelText("Doğrulama kodu"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /Etkinleştir ve giriş yap/ }));

    expect(await screen.findByRole("list", { name: "Kurtarma kodları" })).toHaveTextContent("AAAAA-BBBBB");
    expect(fake.completeTwoFactorEnrollment).toHaveBeenCalledWith("ch-1", "123456");

    const next = screen.getByRole("button", { name: /Devam et/ });
    expect(next).toBeDisabled();
    expect(onAuthenticated).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(next);
    expect(onAuthenticated).toHaveBeenCalled();
  });

  it("yanlış kodda hata gösterir; kurtarma kodları gösterilmez", async () => {
    const fake = api({
      completeTwoFactorEnrollment: vi.fn().mockRejectedValue(new ApiHttpError(400, { code: "INVALID_TWO_FACTOR_CODE" }))
    });
    wrap("enroll", fake);
    fireEvent.click(screen.getByRole("button", { name: "Kurulumu başlat" }));
    await screen.findByTestId("totp-secret");
    fireEvent.change(screen.getByLabelText("Doğrulama kodu"), { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: /Etkinleştir ve giriş yap/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Kod yanlış");
    expect(screen.queryByRole("list", { name: "Kurtarma kodları" })).toBeNull();
  });
});
