"use client";

import { useEffect, useState, type FormEvent } from "react";
import { authApi, type AdminAccount, type AdminTargetStatus } from "@/features/auth";
import { adminErrorMessage } from "../services/admin-errors";
import { formatDate } from "./deletion-requests-panel";
import { errorClass, inputClass, labelClass, okClass, primaryButton, surfaceClass } from "./ui";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const STATUS_LABEL: Record<string, string> = {
  PENDING_VERIFICATION: "E-posta doğrulaması bekliyor",
  ACTIVE: "Etkin",
  SUSPENDED: "Askıda",
  BANNED: "Yasaklı",
  DISABLED: "Devre dışı",
  DELETION_REQUESTED: "Silinmesi bekleniyor",
  DELETED: "Silindi"
};

const TARGETS: { value: AdminTargetStatus; label: string }[] = [
  { value: "ACTIVE", label: "Etkin" },
  { value: "SUSPENDED", label: "Askıya al" },
  { value: "BANNED", label: "Yasakla" },
  { value: "DISABLED", label: "Devre dışı bırak" }
];

/** Askı süresi seçenekleri (gün); Auth sınırları dışında kalırsa sunucu reddeder. `0` = süresiz. */
const SUSPENSION_DAYS = [0, 1, 7, 30];

/**
 * Hesap yönetimi: hesap kimliğiyle arama (e-posta/kullanıcı adı araması Auth'ta yoktur), durum değiştirme ve rol
 * verme/alma. Her değişiklik gerekçe ister ve Auth'ta denetim kaydına yazılır; yönetici kendi hesabını değiştiremez.
 */
export function AccountsPanel({ initialAccountId }: { initialAccountId: string | null }) {
  const [input, setInput] = useState(initialAccountId ?? "");
  const [account, setAccount] = useState<AdminAccount | null>(null);
  const [roles, setRoles] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void authApi.listAdminRoles().then(setRoles).catch(() => setRoles([]));
  }, []);

  async function load(accountId: string) {
    setBusy(true);
    setError(null);
    try {
      setAccount(await authApi.getAdminAccount(accountId));
    } catch (failure) {
      setAccount(null);
      setError(adminErrorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!initialAccountId || !UUID.test(initialAccountId)) return;
    let alive = true;
    authApi
      .getAdminAccount(initialAccountId)
      .then((found) => alive && setAccount(found))
      .catch((failure: unknown) => alive && setError(adminErrorMessage(failure)));
    return () => {
      alive = false;
    };
  }, [initialAccountId]);

  function onSearch(event: FormEvent) {
    event.preventDefault();
    const value = input.trim();
    if (!UUID.test(value)) {
      setError("Geçerli bir hesap kimliği (UUID) gir.");
      return;
    }
    void load(value);
  }

  return (
    <div className="flex flex-col gap-5">
      <form onSubmit={onSearch} className="flex flex-col gap-2 sm:flex-row sm:items-end" noValidate>
        <label className="flex flex-1 flex-col gap-1 text-sm">
          <span className={labelClass}>Hesap kimliği (UUID)</span>
          <input className={`${inputClass} font-mono`} value={input} onChange={(event) => setInput(event.target.value)}
            placeholder="00000000-0000-0000-0000-000000000000" spellCheck={false} />
        </label>
        <button type="submit" className={primaryButton} disabled={busy}>Getir</button>
      </form>
      {error && <p role="alert" className={errorClass}>{error}</p>}
      {account && (
        <AccountDetail account={account} roles={roles} onChanged={() => void load(account.id)} />
      )}
    </div>
  );
}

function AccountDetail({
  account,
  roles,
  onChanged
}: {
  account: AdminAccount;
  roles: string[];
  onChanged: () => void;
}) {
  return (
    <div className="flex flex-col gap-5">
      <dl className={`${surfaceClass} grid gap-4 text-sm sm:grid-cols-2`}>
        <Field label="Kullanıcı adı" value={account.username || "-"} />
        <Field label="Durum" value={STATUS_LABEL[account.status] ?? account.status} />
        <Field label="Roller" value={account.roles.join(", ") || "-"} />
        <Field label="İki adımlı doğrulama" value={account.twoFactorEnabled ? "Açık" : "Kapalı"} />
        <Field label="Oluşturulma" value={formatDate(account.createdAt)} />
        {account.suspendedUntil && <Field label="Askı bitişi" value={formatDate(account.suspendedUntil)} />}
        <div className="sm:col-span-2">
          <dt className={labelClass}>Hesap kimliği</dt>
          <dd className="mt-1 break-all font-mono text-sm text-text-primary">{account.id}</dd>
        </div>
      </dl>
      <StatusForm account={account} onChanged={onChanged} />
      <RoleForm account={account} roles={roles} onChanged={onChanged} />
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className={labelClass}>{label}</dt>
      <dd className="mt-1 text-sm text-text-primary">{value}</dd>
    </div>
  );
}

function StatusForm({ account, onChanged }: { account: AdminAccount; onChanged: () => void }) {
  const [status, setStatus] = useState<AdminTargetStatus>("SUSPENDED");
  const [days, setDays] = useState(7);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const suspendedUntil =
        status === "SUSPENDED" && days > 0 ? new Date(Date.now() + days * 86_400_000).toISOString() : undefined;
      await authApi.changeAdminAccountStatus(account.id, {
        status,
        reason: reason.trim(),
        ...(suspendedUntil ? { suspendedUntil } : {})
      });
      setReason("");
      setMessage({ ok: true, text: "Durum güncellendi." });
      onChanged();
    } catch (failure) {
      setMessage({ ok: false, text: adminErrorMessage(failure) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className={`${surfaceClass} flex flex-col gap-4`} noValidate>
      <h3 className="font-display text-sm font-semibold uppercase tracking-[0.12em] text-text-primary">Durum değiştir</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          <span className={labelClass}>Yeni durum</span>
          <select className={inputClass} value={status} onChange={(event) => setStatus(event.target.value as AdminTargetStatus)}>
            {TARGETS.map((target) => <option key={target.value} value={target.value}>{target.label}</option>)}
          </select>
        </label>
        {status === "SUSPENDED" && (
          <label className="flex flex-col gap-1 text-sm">
            <span className={labelClass}>Askı süresi</span>
            <select className={inputClass} value={days} onChange={(event) => setDays(Number(event.target.value))}>
              {SUSPENSION_DAYS.map((value) => (
                <option key={value} value={value}>{value === 0 ? "Süresiz" : `${value} gün`}</option>
              ))}
            </select>
          </label>
        )}
      </div>
      <label className="flex flex-col gap-1 text-sm">
        <span className={labelClass}>Gerekçe (zorunlu, denetim kaydına yazılır)</span>
        <textarea className={inputClass} rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />
      </label>
      {message && (
        <p role={message.ok ? "status" : "alert"} className={message.ok ? okClass : errorClass}>
          {message.text}
        </p>
      )}
      <button type="submit" className={`${primaryButton} self-start`} disabled={busy || reason.trim() === ""}>Uygula</button>
    </form>
  );
}

function RoleForm({ account, roles, onChanged }: { account: AdminAccount; roles: string[]; onChanged: () => void }) {
  const [role, setRole] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const selected = role || roles[0] || "";
  const has = account.roles.includes(selected);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await authApi.changeAdminAccountRole(account.id, has ? "revoke" : "grant", { role: selected, reason: reason.trim() });
      setReason("");
      setMessage({ ok: true, text: has ? "Rol geri alındı." : "Rol verildi." });
      onChanged();
    } catch (failure) {
      setMessage({ ok: false, text: adminErrorMessage(failure) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className={`${surfaceClass} flex flex-col gap-4`} noValidate>
      <h3 className="font-display text-sm font-semibold uppercase tracking-[0.12em] text-text-primary">Rol yönetimi</h3>
      <label className="flex flex-col gap-1 text-sm">
        <span className={labelClass}>Rol</span>
        <select className={inputClass} value={selected} onChange={(event) => setRole(event.target.value)}>
          {roles.map((code) => (
            <option key={code} value={code}>{code}{account.roles.includes(code) ? " (var)" : ""}</option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1 text-sm">
        <span className={labelClass}>Gerekçe (zorunlu, denetim kaydına yazılır)</span>
        <textarea className={inputClass} rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />
      </label>
      {message && (
        <p role={message.ok ? "status" : "alert"} className={message.ok ? okClass : errorClass}>
          {message.text}
        </p>
      )}
      <button type="submit" className={`${primaryButton} self-start`} disabled={busy || selected === "" || reason.trim() === ""}>
        {has ? "Rolü geri al" : "Rolü ver"}
      </button>
    </form>
  );
}
