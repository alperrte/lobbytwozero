export type DeviceLink = {
  id: string;
  deviceId: string;
  label: string;
  status: "ACTIVE" | "DISABLED";
  linkedAt: string;
  lastSyncAt: string | null;
  disabledAt: string | null;
};
