import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createTranslator } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import ContactPage from "./page";

let activeMessages: typeof tr | typeof en = tr;

vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: string) =>
    createTranslator({
      locale: activeMessages === tr ? "tr" : "en",
      messages: activeMessages,
      namespace: namespace as "Contact"
    })
}));
vi.mock("@/shared/lib/seo/page-metadata", () => ({ buildPageMetadata: vi.fn() }));

async function renderContact(messages: typeof tr | typeof en = tr) {
  activeMessages = messages;
  const page = await ContactPage();
  render(
    <NextIntlClientProvider
      locale={messages === tr ? "tr" : "en"}
      messages={messages}
      timeZone="Europe/Istanbul"
    >
      {page}
    </NextIntlClientProvider>
  );
}

describe("ContactPage", () => {
  it("başlık ve açıklamayı gösterir", async () => {
    await renderContact();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("İletişim");
    expect(
      screen.getByText("Bir fikrin, sorun veya önerin mi var? Seninle iletişim kurmak isteriz.")
    ).toBeInTheDocument();
  });

  it("demo formu ve Geri Bildirimlerin Önemli panelini gösterir", async () => {
    await renderContact();

    expect(screen.getByRole("form", { name: tr.Contact.formLabel })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Bu iletişim formu şu anda demo aşamasındadır. Gönderilen bilgiler herhangi bir sunucuya iletilmez."
      )
    ).toBeInTheDocument();
    expect(
      screen.getByRole("complementary", { name: "Geri Bildirimlerin Önemli" })
    ).toBeInTheDocument();
  });

  it("uydurma e-posta, telefon, adres veya mailto bağlantısı içermez", async () => {
    await renderContact();

    expect(document.querySelectorAll('a[href^="mailto:"], a[href^="tel:"]')).toHaveLength(0);
    expect(document.body.textContent).not.toMatch(/@[a-z0-9-]+\.[a-z]{2,}/i);
    expect(document.body.textContent).not.toMatch(/\+?\d[\d\s()-]{8,}\d/);
  });

  it("İngilizce başlığı gösterir", async () => {
    await renderContact(en);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(en.Contact.title);
  });
});
