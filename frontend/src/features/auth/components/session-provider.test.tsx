import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { useShellSession } from "@/shared/hooks/use-shell-user";
import { AuthApiError } from "../services/auth-http";
import type { AuthMe } from "../types/auth.types";
import { SessionProvider, useSession } from "./session-provider";

const ME: AuthMe = {
  accountId: "a1",
  roles: ["PLAYER"],
  authMethods: ["pwd"],
  accessTokenExpiresAt: "2026-11-07T10:00:00Z"
};

function Probe() {
  const session = useSession();
  const shell = useShellSession();
  return (
    <div>
      <p data-testid="status">{session.status}</p>
      <p data-testid="roles">{session.me ? session.me.roles.join(",") : "-"}</p>
      <p data-testid="shell-user">{shell.user ? shell.user.displayName : "yok"}</p>
      <p data-testid="shell-loading">{String(shell.loading)}</p>
      <button onClick={() => void session.logout().catch(() => undefined)}>çık</button>
    </div>
  );
}

function renderSession(api: Parameters<typeof SessionProvider>[0]["api"], locale: "tr" | "en" = "tr") {
  return render(
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en} timeZone="Europe/Istanbul">
      <SessionProvider api={api}>
        <Probe />
      </SessionProvider>
    </NextIntlClientProvider>
  );
}

const status = () => screen.getByTestId("status").textContent;

describe("SessionProvider", () => {
  it("yüklenirken 'loading', sonra /me başarılıysa 'authenticated' olur ve kabuğa genel etiket verir", async () => {
    let resolve!: (me: AuthMe) => void;
    const getMe = vi.fn(() => new Promise<AuthMe>((r) => (resolve = r)));
    renderSession({ getMe, logout: vi.fn() });

    expect(status()).toBe("loading");
    expect(screen.getByTestId("shell-loading")).toHaveTextContent("true");
    expect(screen.getByTestId("shell-user")).toHaveTextContent("yok");

    await act(async () => resolve(ME));

    expect(status()).toBe("authenticated");
    expect(screen.getByTestId("roles")).toHaveTextContent("PLAYER");
    expect(screen.getByTestId("shell-user")).toHaveTextContent("Hesabım");
    expect(screen.getByTestId("shell-loading")).toHaveTextContent("false");
  });

  it("401'de misafir olur; kabuğa kullanıcı verilmez", async () => {
    renderSession({ getMe: vi.fn().mockRejectedValue(new AuthApiError(401, "SESSION_INVALID")), logout: vi.fn() });

    await waitFor(() => expect(status()).toBe("guest"));
    expect(screen.getByTestId("shell-user")).toHaveTextContent("yok");
  });

  it("ağ hatasında kimlik varsaymaz (misafir)", async () => {
    renderSession({ getMe: vi.fn().mockRejectedValue(new AuthApiError(0, "NETWORK")), logout: vi.fn() });

    await waitFor(() => expect(status()).toBe("guest"));
  });

  it("çıkışta API'yi çağırır ve durumu temizler", async () => {
    const logout = vi.fn().mockResolvedValue(undefined);
    renderSession({ getMe: vi.fn().mockResolvedValue(ME), logout });
    await waitFor(() => expect(status()).toBe("authenticated"));

    fireEvent.click(screen.getByRole("button", { name: "çık" }));

    await waitFor(() => expect(status()).toBe("guest"));
    expect(logout).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("roles")).toHaveTextContent("-");
  });

  it("çıkış isteği başarısız olsa da yerel durum misafire döner", async () => {
    const logout = vi.fn().mockRejectedValue(new AuthApiError(0, "NETWORK"));
    renderSession({ getMe: vi.fn().mockResolvedValue(ME), logout });
    await waitFor(() => expect(status()).toBe("authenticated"));

    fireEvent.click(screen.getByRole("button", { name: "çık" }));

    await waitFor(() => expect(status()).toBe("guest"));
  });

  it("İngilizce arayüzde genel etiket İngilizcedir", async () => {
    renderSession({ getMe: vi.fn().mockResolvedValue(ME), logout: vi.fn() }, "en");

    await waitFor(() => expect(screen.getByTestId("shell-user")).toHaveTextContent("My account"));
  });

  it("SessionProvider dışında useSession hata verir", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    expect(() =>
      render(
        <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
          <Probe />
        </NextIntlClientProvider>
      )
    ).toThrow(/SessionProvider/);
    spy.mockRestore();
  });
});
