import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ProfileMenu } from "./profile-menu";

function renderProfile(user: Parameters<typeof ProfileMenu>[0]["user"]) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <ProfileMenu user={user} />
    </NextIntlClientProvider>
  );
}

describe("ProfileMenu tetikleyicisi", () => {
  it("misafirde 'Misafir' adını ve kullanıcı simgesini gösterir", () => {
    const { container } = renderProfile(null);
    expect(screen.getByRole("button", { name: /Misafir/ })).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });

  it("giriş yapmış kullanıcının fotoğrafını ve adını birlikte gösterir; 'Misafir' yazmaz", () => {
    const { container } = renderProfile({
      displayName: "Ada Lovelace",
      avatarUrl: "https://example.test/ada.png"
    });
    const trigger = screen.getByRole("button", { name: /Ada Lovelace/ });
    expect(trigger).toHaveTextContent("Ada Lovelace");
    expect(trigger).not.toHaveTextContent("Misafir");
    const photo = container.querySelector("img");
    expect(photo).toHaveAttribute("src", "https://example.test/ada.png");
    expect(photo?.className).toContain("object-cover");
  });

  it("fotoğrafı olmayan kullanıcıda ad baş harflerini gösterir", () => {
    renderProfile({ displayName: "Ada Lovelace" });
    expect(screen.getByRole("button", { name: /Ada Lovelace/ })).toHaveTextContent("AL");
  });
});
