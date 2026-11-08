import { hardwareApi } from "./hardware-api";
import { componentChanges, type WorkshopDraft, type WorkshopSlot } from "../schemas/workshop";
import type { Component, Profile } from "../types/hardware";

export type WorkshopSaveInput = {
  profile?: Profile;
  draft: WorkshopDraft;
  saved: Partial<Record<WorkshopSlot, Component>>;
  /** Profil oluşturulur/güncellenir oluşturulmaz çağrılır; sonraki bileşen kaydı başarısız olsa da ekran doğru profili açar. */
  onProfile?: (profile: Profile) => void;
};

/**
 * Atölye kaydı sıralıdır: önce profil (ad ve kasa rengi), sonra değişen her parça. Her yazma isteği bir önceki yanıtın
 * `profileVersion` değeriyle gider (iyimser kilit); çakışmada (409) zincir durur ve hata çağırana iletilir.
 */
export async function saveWorkshop({ profile, draft, saved, onProfile }: WorkshopSaveInput): Promise<{ profile: Profile; version: number; changedComponents: number }> {
  const name = draft.name.trim();
  let current: Profile;
  if (!profile) current = await hardwareApi.createProfile({ name, deviceType: "DESKTOP", caseColor: draft.caseColor });
  else if (profile.name !== name || profile.caseColor !== draft.caseColor) current = await hardwareApi.updateProfile(profile.id, { name, deviceType: profile.deviceType, caseColor: draft.caseColor, profileVersion: profile.version });
  else current = profile;
  onProfile?.(current);
  let version = current.version;
  const changes = componentChanges(draft, profile ? saved : {});
  for (const change of changes) {
    const response = await hardwareApi.saveComponent(current.id, { profileVersion: version, category: change.slot, quantity: change.quantity, productId: change.productId }, change.componentId);
    version = response.profileVersion;
  }
  return { profile: current, version, changedComponents: changes.length };
}
