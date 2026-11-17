import { render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { SessionApi } from "../services/auth-api";
import { RedirectIfAuthenticated } from "./redirect-if-authenticated";

const replace = vi.hoisted(() => vi.fn());
const hasHint = vi.hoisted(() => vi.fn());

vi.mock("@/shared/lib/i18n/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("../services/session-hint", () => ({ hasSessionHint: hasHint }));

const me = { accountId: "a1", roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "x" };

function api(getMe: SessionApi["getMe"]): SessionApi {
  return { getMe, logout: vi.fn() } as unknown as SessionApi;
}

describe("RedirectIfAuthenticated", () => {
  beforeEach(() => {
    replace.mockClear();
    hasHint.mockReset();
  });

  it("oturum ipucu yoksa sunucuya sormaz ve yönlendirmez", () => {
    hasHint.mockReturnValue(false);
    const getMe = vi.fn();
    render(<RedirectIfAuthenticated api={api(getMe)} />);
    expect(getMe).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
  });

  it("oturum sunucuda açıksa ana sayfaya yönlendirir", async () => {
    hasHint.mockReturnValue(true);
    render(<RedirectIfAuthenticated api={api(vi.fn().mockResolvedValue(me))} />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
  });

  it("ipucu var ama sunucu oturumu reddederse (hata) sayfada bırakır", async () => {
    hasHint.mockReturnValue(true);
    const getMe = vi.fn().mockRejectedValue(new Error("401"));
    render(<RedirectIfAuthenticated api={api(getMe)} />);
    await waitFor(() => expect(getMe).toHaveBeenCalledTimes(1));
    expect(replace).not.toHaveBeenCalled();
  });

  it("sayfadan ayrılınca bekleyen isteği iptal eder; geç gelen yanıt yönlendirmez", async () => {
    hasHint.mockReturnValue(true);
    let resolve: (value: typeof me) => void = () => undefined;
    const getMe = vi.fn().mockReturnValue(new Promise((done) => (resolve = done)));
    const view = render(<RedirectIfAuthenticated api={api(getMe)} />);
    view.unmount();
    resolve(me);
    await Promise.resolve();
    expect(replace).not.toHaveBeenCalled();
  });
});
