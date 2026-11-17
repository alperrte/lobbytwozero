import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TotpQr } from "./totp-qr";

const URI = "otpauth://totp/LTZ:ada%40example.test?secret=JBSWY3DPEHPK3PXP&issuer=LTZ";

describe("TotpQr", () => {
  it("adresten bir SVG QR çizer; etiketi ekran okuyucuya verir, anahtarı yazıya dökmez", () => {
    const { container } = render(<TotpQr uri={URI} label="Kurulum QR kodu" />);
    const svg = screen.getByRole("img", { name: "Kurulum QR kodu" });
    expect(svg.tagName.toLowerCase()).toBe("svg");
    expect(container.querySelector("path")?.getAttribute("d")?.length).toBeGreaterThan(100);
    expect(container.innerHTML).not.toContain("JBSWY3DPEHPK3PXP");
  });

  it("aynı adres her zaman aynı deseni üretir, farklı adres farklı desen", () => {
    const d = (uri: string) => render(<TotpQr uri={uri} label="x" />).container.querySelector("path")?.getAttribute("d");
    expect(d(URI)).toBe(d(URI));
    expect(d(URI)).not.toBe(d(`${URI}2`));
  });

  it("boş adreste ya da sığmayacak kadar uzun veride hiçbir şey çizmez", () => {
    expect(render(<TotpQr uri="" label="x" />).container.querySelector("svg")).toBeNull();
    expect(render(<TotpQr uri={"a".repeat(5000)} label="x" />).container.querySelector("svg")).toBeNull();
  });
});
