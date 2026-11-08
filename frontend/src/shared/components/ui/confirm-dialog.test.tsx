import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./confirm-dialog";

function renderDialog(props: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
  const onConfirm = vi.fn();
  const onClose = vi.fn();
  const view = render(
    <ConfirmDialog
      open
      title="Engeli kaldır?"
      description="Bu kullanıcı seninle yeniden iletişim kurabilir."
      confirmLabel="Engeli Kaldır"
      pendingLabel="Kaldırılıyor…"
      cancelLabel="Vazgeç"
      closeLabel="Kapat"
      onConfirm={onConfirm}
      onClose={onClose}
      {...props}
    />
  );
  return { ...view, onConfirm, onClose };
}

describe("ConfirmDialog", () => {
  it("açılınca modal olur, başlık/açıklamayla adlandırılır ve odak 'Vazgeç'te olur", () => {
    renderDialog();
    const dialog = screen.getByRole("dialog", { name: "Engeli kaldır?" });
    expect(dialog).toHaveAttribute("open");
    expect(dialog).toHaveAccessibleDescription("Bu kullanıcı seninle yeniden iletişim kurabilir.");
    expect(screen.getByRole("button", { name: "Vazgeç" })).toHaveFocus();
  });

  it("onay, vazgeç, kapat ve Esc doğru geri çağrıları tetikler", () => {
    const { onConfirm, onClose } = renderDialog();
    fireEvent.click(screen.getByRole("button", { name: "Engeli Kaldır" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    fireEvent.click(screen.getByRole("button", { name: "Kapat" }));
    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
    expect(onClose).toHaveBeenCalledTimes(3);
  });

  it("işlem sürerken kapatılamaz ve düğme bekleme metnini gösterir", () => {
    const { onClose } = renderDialog({ pending: true });
    expect(screen.getByRole("button", { name: "Kaldırılıyor…" })).toBeDisabled();
    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("hata metnini pencerede duyurur", () => {
    renderDialog({ error: "Kayıt bulunamadı; liste güncellendi." });
    expect(screen.getByRole("alert")).toHaveTextContent("Kayıt bulunamadı");
  });
});
