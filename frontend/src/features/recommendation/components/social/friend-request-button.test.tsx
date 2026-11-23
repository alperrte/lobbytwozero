import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { RecommendationScopeContext } from "../../hooks/recommendation-scope";
import { enrichmentApi } from "../../services/enrichment-api";
import { FriendRequestButton } from "./friend-request-button";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string; children: ReactNode } & Record<string, unknown>) => <a {...rest} href={href}>{children}</a>
}));
vi.mock("../../services/enrichment-api", () => ({ enrichmentApi: { sendFriendRequest: vi.fn() } }));

const api = vi.mocked(enrichmentApi);
const social = tr.Recommendation.social;
const consentRequired = vi.fn();

function renderButton() {
  render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
    <RecommendationScopeContext.Provider value={{ accountId: "acc", consentRequired }}>
      <FriendRequestButton receiverId="u-1" name="Deniz" />
    </RecommendationScopeContext.Provider>
  </NextIntlClientProvider>);
  return screen.getByRole("button", { name: "Deniz kişisini arkadaş olarak ekle" });
}

afterEach(() => vi.clearAllMocks());

describe("FriendRequestButton", () => {
  it("201 sonrası 'İstek gönderildi' gösterir ve düğmeyi kaldırır", async () => {
    api.sendFriendRequest.mockResolvedValue(undefined);
    fireEvent.click(renderButton());
    expect(await screen.findByRole("status")).toHaveTextContent(social.outcome.SENT);
    expect(api.sendFriendRequest).toHaveBeenCalledWith("u-1");
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("çift tıklamada tek istek gönderir", async () => {
    let resolve!: () => void;
    api.sendFriendRequest.mockReturnValue(new Promise<void>((res) => { resolve = res; }));
    const button = renderButton();
    fireEvent.click(button);
    fireEvent.click(button);
    expect(api.sendFriendRequest).toHaveBeenCalledTimes(1);
    resolve();
    expect(await screen.findByRole("status")).toBeInTheDocument();
  });

  it.each([
    ["ALREADY_FRIENDS", social.outcome.ALREADY_FRIENDS],
    ["FRIEND_REQUEST_ALREADY_PENDING", social.outcome.FRIEND_REQUEST_ALREADY_PENDING],
    ["USER_BLOCKED", social.outcome.USER_BLOCKED]
  ])("409 %s hata değil durum olarak gösterilir", async (code, text) => {
    api.sendFriendRequest.mockRejectedValue(new ApiHttpError(409, { code }));
    fireEvent.click(renderButton());
    expect(await screen.findByRole("status")).toHaveTextContent(text);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("gelen istek varsa istekler sayfasına yönlendirir", async () => {
    api.sendFriendRequest.mockRejectedValue(new ApiHttpError(409, { code: "INCOMING_FRIEND_REQUEST_PENDING" }));
    fireEvent.click(renderButton());
    expect(await screen.findByRole("link", { name: social.openRequests })).toHaveAttribute("href", "/friends/requests");
  });

  it("diğer hatalar düğmenin yanında hata olarak kalır", async () => {
    api.sendFriendRequest.mockRejectedValue(new ApiHttpError(503));
    const button = renderButton();
    fireEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(tr.Recommendation.errors.status["503"].title);
    expect(button).toBeInTheDocument();
  });
});
