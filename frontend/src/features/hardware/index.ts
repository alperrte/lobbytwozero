/** Public consumer surface for the Performance dashboard. */
export { HardwareSnapshot } from "./components/hardware-snapshot";
export { HardwareProfileReference } from "./components/hardware-profile-reference";
/** Recommendation (Sana Özel → Donanım) için: kullanıcının kendi bilgisayar profilleri listesi. */
export { hardwareApi } from "./services/hardware-api";
export type { Profile as HardwareProfile } from "./types/hardware";
