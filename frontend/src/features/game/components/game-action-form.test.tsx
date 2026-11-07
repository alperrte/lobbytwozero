import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ComponentProps } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { mutateGame } from "../services/game-api";
import { GameActionForm } from "./game-action-form";

vi.mock("../services/game-api", () => ({ mutateGame: vi.fn() }));
const mutate = vi.mocked(mutateGame);
function mount(props: Partial<ComponentProps<typeof GameActionForm>> = {}) {
  render(<NextIntlClientProvider locale="tr" messages={messages}><GameActionForm title="Fixture form" fields={[{ name: "title", required: true, max: 120 }]} initial={{ title: "Draft title" }} build={(values) => ({ operation: "create", body: values })} {...props} /></NextIntlClientProvider>);
}
afterEach(() => vi.resetAllMocks());

describe("Game mutasyon formu", () => {
  it("409 sonrasında taslağı korur ve otomatik tekrar göndermez", async () => {
    mutate.mockRejectedValueOnce(new ApiHttpError(409));
    mount(); fireEvent.submit(screen.getByRole("form", { name: "Fixture form" }));
    await screen.findByRole("alert");
    expect(screen.getByLabelText("Başlık")).toHaveValue("Draft title");
    expect(screen.queryByText(messages.Game.saved)).toBeNull();
    expect(mutate).toHaveBeenCalledTimes(1);
  });
  it("sunucu alan hatasını girdiye bağlar ve ayrıntı sızdırmaz", async () => {
    mutate.mockRejectedValueOnce(new ApiHttpError(400, { fields: ["title"] }));
    mount(); fireEvent.submit(screen.getByRole("form", { name: "Fixture form" }));
    await screen.findByRole("alert");
    expect(screen.getByLabelText("Başlık")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("Başlık")).toHaveAttribute("aria-describedby", screen.getByRole("alert").id);
  });
  it("aynı bekleyen işlemi iki kez göndermez; başarı kesinleşince taslağı temizler", async () => {
    let finish: (value: unknown) => void = () => {};
    mutate.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    mount({ resetOnSuccess: true });
    const form = screen.getByRole("form", { name: "Fixture form" });
    fireEvent.change(screen.getByLabelText("Başlık"), { target: { value: "Changed draft" } });
    fireEvent.submit(form); fireEvent.submit(form);
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: messages.Game.saving })).toBeDisabled();
    await act(async () => finish({}));
    await screen.findByText(messages.Game.saved);
    expect(screen.getByLabelText("Başlık")).toHaveValue("Draft title");
  });
  it("silme/gizleme onayı alınmadan işlem yapmaz", async () => {
    mutate.mockResolvedValue(undefined); mount({ destructive: true });
    expect(screen.getByRole("button", { name: messages.Game.save })).toBeDisabled();
    fireEvent.submit(screen.getByRole("form", { name: "Fixture form" }));
    expect(mutate).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: messages.Game.save }));
    await waitFor(() => expect(mutate).toHaveBeenCalledTimes(1));
  });
  it("gizli içerik formu kapalıysa programatik gönderimi de reddeder", () => {
    mount({ disabled: true }); fireEvent.submit(screen.getByRole("form", { name: "Fixture form" }));
    expect(mutate).not.toHaveBeenCalled();
  });
  it("karakter sayacını alanın erişilebilir adına eklemez", () => {
    mount({ fields: [{ name: "description", kind: "textarea", max: 4000 }] });
    expect(screen.getByRole("textbox", { name: messages.Game.fields.description })).toBeVisible();
  });
});
