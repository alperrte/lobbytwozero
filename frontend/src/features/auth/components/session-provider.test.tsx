import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { useShellSession } from "@/shared/hooks/use-shell-user";
import { ApiHttpError } from "@/shared/lib/api/client";
import type { AuthMe } from "../types/auth.types";
import { useSession } from "../hooks/use-session";
import { SessionProvider } from "./session-provider";

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

const noProfile = () => vi.fn().mockRejectedValue(new Error("profil yok"));

function renderSession(
  api: Parameters<typeof SessionProvider>[0]["api"],
  locale: "tr" | "en" = "tr",
  profileRequest: Parameters<typeof SessionProvider>[0]["profileRequest"] = noProfile()
) {
  return render(
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en} timeZone="Europe/Istanbul">
      <SessionProvider api={api} profileRequest={profileRequest}>
        <Probe />
      </SessionProvider>
    </NextIntlClientProvider>
  );
}

const status = () => screen.getByTestId("status").textContent;

function setHint(present: boolean) {
  document.cookie = present
    ? "ltz_session=1; path=/"
    : "ltz_session=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
}

beforeEach(() => setHint(true));
afterEach(() => setHint(false));

describe("SessionProvider: profil özeti", () => {
  const authed = { getMe: vi.fn().mockResolvedValue(ME), logout: vi.fn() };

  it("oturum açılınca User profilinden ad gelir; kabuk gerçek adı gösterir", async () => {
    const request = vi.fn().mockResolvedValue({ displayName: "Ada", avatar: { small: "/api/v1/user/media/k-s.jpg" } });
    renderSession(authed, "tr", request);

    await waitFor(() => expect(screen.getByTestId("shell-user").textContent).toBe("Ada"));
    expect(request).toHaveBeenCalledWith("/api/v1/user/me", expect.any(AbortSignal));
  });

  it("profil alınamazsa oturum bozulmaz; genel etiket kalır", async () => {
    renderSession(authed, "tr", vi.fn().mockRejectedValue(new ApiHttpError(500, { code: "UNKNOWN" })));

    await waitFor(() => expect(status()).toBe("authenticated"));
    expect(screen.getByTestId("shell-user").textContent).toBe("Hesabım");
  });

  it("misafirde profil isteği atılmaz", async () => {
    setHint(false);
    const request = vi.fn();
    renderSession({ getMe: vi.fn(), logout: vi.fn() }, "tr", request);

    await waitFor(() => expect(status()).toBe("guest"));
    expect(request).not.toHaveBeenCalled();
  });
});

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

  it("oturum ipucu çerezi yoksa hiç istek atmadan misafir olur", async () => {
    setHint(false);
    const getMe = vi.fn();
    renderSession({ getMe, logout: vi.fn() });

    await waitFor(() => expect(status()).toBe("guest"));
    expect(getMe).not.toHaveBeenCalled();
    expect(screen.getByTestId("shell-user")).toHaveTextContent("yok");
  });

  it("__Host- önekli ipucu çerezi de oturum ihtimali sayılır (öneksiz çerez yokken de)", async () => {
    setHint(false);
    const getMe = vi.fn().mockResolvedValue(ME);
    // jsdom http://localhost üzerinde Secure çerez yazmaz; bu yüzden yalnız öneksiz ad ile yazılır ve öncelik denenir.
    document.cookie = "ltz_session=1; path=/";
    renderSession({ getMe, logout: vi.fn() });

    await waitFor(() => expect(status()).toBe("authenticated"));
    expect(getMe).toHaveBeenCalledTimes(1);
  });

  it("401'de misafir olur; kabuğa kullanıcı verilmez", async () => {
    renderSession({ getMe: vi.fn().mockRejectedValue(new ApiHttpError(401, { code: "SESSION_INVALID" })), logout: vi.fn() });

    await waitFor(() => expect(status()).toBe("guest"));
    expect(screen.getByTestId("shell-user")).toHaveTextContent("yok");
  });

  it("ağ hatasında kimlik varsaymaz (misafir)", async () => {
    renderSession({ getMe: vi.fn().mockRejectedValue(new ApiHttpError(0, { code: "NETWORK" })), logout: vi.fn() });

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
    const logout = vi.fn().mockRejectedValue(new ApiHttpError(0, { code: "NETWORK" }));
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
