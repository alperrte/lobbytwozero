import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { LimitationsNotice, RankBadge, RecommendationError, errorMessageKey } from "./recommendation-ui";

const wrap = (node: ReactNode) => render(<NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">{node}</NextIntlClientProvider>);
const errors = tr.Recommendation.errors;

afterEach(() => vi.useRealTimers());

describe("RecommendationError", () => {
  it.each([
    [new ApiHttpError(401), errors.status["401"].title],
    [new ApiHttpError(403), errors.status["403"].title],
    [new ApiHttpError(404), errors.status["404"].title],
    [new ApiHttpError(409), errors.status["409"].title],
    [new ApiHttpError(500), errors.status["500"].title],
    [new ApiHttpError(503), errors.status["503"].title],
    [new ApiHttpError(0, { code: "NETWORK" }), errors.codes.NETWORK.title],
    [new TypeError("x"), errors.status.network.title],
    [new ApiHttpError(403, { code: "CONSENT_REQUIRED" }), errors.codes.CONSENT_REQUIRED.title],
    [new ApiHttpError(429, { code: "HISTORY_LIMIT_REACHED" }), errors.codes.HISTORY_LIMIT_REACHED.title],
    [new ApiHttpError(503, { code: "ASSISTANT_TIMEOUT" }), errors.codes.ASSISTANT_UNAVAILABLE.title]
  ])("%s için ayrı başlık gösterir", (error, title) => {
    wrap(<RecommendationError error={error} />);
    expect(screen.getByRole("alert")).toHaveTextContent(title);
  });

  it("429 Retry-After süresince tekrar dene pasif kalır ve geri sayar", () => {
    vi.useFakeTimers();
    const retry = vi.fn();
    wrap(<RecommendationError error={new ApiHttpError(429, { retryAfterSeconds: 2 })} retry={retry} />);
    const button = screen.getByRole("button", { name: tr.Recommendation.common.retry });
    expect(button).toBeDisabled();
    expect(screen.getByText("2 sn sonra tekrar dene")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1000));
    expect(screen.getByText("1 sn sonra tekrar dene")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1000));
    expect(button).toBeEnabled();
    fireEvent.click(button);
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("hata yoksa hiçbir şey çizmez", () => {
    const { container } = wrap(<RecommendationError error={undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("bilinmeyen durum ağ hatası metnine düşer", () => {
    expect(errorMessageKey(new ApiHttpError(418))).toBe("status.network");
    expect(errorMessageKey(new ApiHttpError(400, { code: "SOMETHING_ELSE" }))).toBe("status.400");
  });
});

describe("yardımcı göstergeler", () => {
  it("sınırlamalar tekilleşir, bilinmeyen kod genel metinle gösterilir", () => {
    wrap(<LimitationsNotice codes={["VIEWER_PREFERENCES_MISSING", "VIEWER_PREFERENCES_MISSING", "NEW_SERVER_CODE"]} />);
    const items = screen.getAllByRole("listitem").map((item) => item.textContent);
    expect(items).toEqual([tr.Recommendation.limitations.VIEWER_PREFERENCES_MISSING, tr.Recommendation.limitations.UNKNOWN]);
  });

  it("sıra rozeti yüzde değil sıra okur", () => {
    wrap(<RankBadge rank={3} />);
    expect(screen.getByLabelText("3. sıra")).toHaveTextContent("#3");
    expect(document.body.textContent).not.toContain("%");
  });
});
