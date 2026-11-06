import { describe, expect, it } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { faqAnchorId, faqCategories, faqItems, faqItemsOf } from "./faq";

describe("faq config", () => {
  it("altı kategoriyi belirlenen sırayla sunar", () => {
    expect(faqCategories.map((category) => category.id)).toEqual([
      "general",
      "account",
      "games",
      "social",
      "hardware",
      "security"
    ]);
    expect(faqCategories.map((category) => category.order)).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it("kategori başına 4, 4, 4, 5, 5, 4 soru ve toplam 26 soru içerir", () => {
    expect(faqCategories.map((category) => faqItemsOf(category.id).length)).toEqual([
      4, 4, 4, 5, 5, 4
    ]);
    expect(faqItems).toHaveLength(26);
  });

  it("soru kimlikleri benzersizdir ve her kategoride sıra 1'den ardışık gider", () => {
    expect(new Set(faqItems.map((item) => item.id)).size).toBe(faqItems.length);
    for (const category of faqCategories) {
      expect(faqItemsOf(category.id).map((item) => item.order)).toEqual(
        faqItemsOf(category.id).map((_, index) => index + 1)
      );
    }
  });

  it("anchor kimliği kategoriden türetilir", () => {
    expect(faqAnchorId("hardware")).toBe("faq-hardware");
  });

  it.each([
    ["tr", tr],
    ["en", en]
  ] as const)("%s: her kategori adı, soru başlığı ve cevap dolu ve benzersizdir", (_, messages) => {
    const titles = new Set<string>();
    for (const category of faqCategories) {
      expect(messages.Faq.categories[category.id].trim()).not.toBe("");
    }
    for (const item of faqItems) {
      const entry = messages.Faq.items[item.id];
      expect(entry.title.trim().length).toBeGreaterThan(8);
      expect(entry.answer.trim().length).toBeGreaterThan(80);
      expect(entry.answer).not.toMatch(/lorem|TODO|placeholder|\.\.\.$/i);
      titles.add(entry.title);
    }
    expect(titles.size).toBe(faqItems.length);
  });

  it("cevaplar 2 ile 4 cümle arasındadır", () => {
    for (const messages of [tr, en]) {
      for (const item of faqItems) {
        const sentences = messages.Faq.items[item.id].answer
          .split(/(?<=[.!?”])\s+/)
          .filter((sentence) => sentence.trim().length > 0);
        expect(sentences.length).toBeGreaterThanOrEqual(2);
        expect(sentences.length).toBeLessThanOrEqual(4);
      }
    }
  });

  it("tarayıcının sıcaklık veya FPS ölçebildiğini iddia etmez", () => {
    const hardware = faqItemsOf("hardware").map((item) => tr.Faq.items[item.id].answer);
    for (const answer of hardware) {
      expect(answer).not.toMatch(/tarayıcı[^.]*(ölçer|ölçebilir|ölçüyor)(?![^.]*(ama|ancak|yalnızca))/i);
    }
    expect(tr.Faq.items.ltzPerformance.answer).toMatch(/ölçemez/);
    expect(en.Faq.items.ltzPerformance.answer).toMatch(/cannot measure/);
  });

  it("yayında olmayan özellikleri hazırmış gibi sunmaz", () => {
    const planned = /planlan|hedefle|henüz|geliştirme aşamasında|ilk sürümün|tasarlan|yayında değil/i;
    const nonLive = faqItems.filter(
      (item) => item.id !== "whatIs" && item.id !== "purpose"
    );
    for (const item of nonLive) {
      expect(tr.Faq.items[item.id].answer).toMatch(planned);
    }
  });
});
