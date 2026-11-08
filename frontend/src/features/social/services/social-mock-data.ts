import type { UserId } from "../types/social.types";
import type { ProfileCard } from "./user-profiles";

/**
 * TEST ÇİFTESİ VERİSİ — `social-mock-store.ts` ile birlikte yalnız testlerde kullanılır (üretim kodu gerçek servise gider).
 * Kimlikler okunur UUID'lerdir; zamanlar "şimdi"ye göre üretilir. Adlar user-service toplu kartının yerine geçer.
 */

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** Deterministik, okunur UUID: `00000000-0000-4000-8000-0000000000NN`. */
export function mockUserId(index: number): UserId {
  return `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`;
}

export const MOCK_ME: UserId = mockUserId(1);
export const MOCK_MY_INVITE_CODE = "7KX4M9QBTR";

type Person = readonly [displayName: string, username: string];

/** Kimlik 2–25: arkadaşlar (24). */
const friendPeople: readonly Person[] = [
  ["Deniz", "denizplays"], ["Ece", "ece"], ["Mert", "mertfps"], ["Selin", "selin"], ["Can", "can"],
  ["Arda", "ardacoop"], ["Bora", "borakaan"], ["Elif", "elifgg"], ["Kerem", "keremtac"], ["Defne", "defnex"],
  ["Emre", "emrevolt"], ["Zeynep", "zeyneprush"], ["Burak", "burakhs"], ["İrem", "iremnova"], ["Onur", "onurbyte"],
  ["Melis", "melisaim"], ["Tolga", "tolgatank"], ["Aslı", "asliwave"], ["Kaan", "kaanexe"], ["Nehir", "nehirlight"],
  ["Efe", "efesnipe"], ["Ceren", "cerenpixel"], ["Ozan", "ozanforge"], ["Gizem", "gizemrogue"]
];
/** Kimlik 30–32: bana istek gönderenler. */
const incomingPeople: readonly Person[] = [["Nova", "novafps"], ["Tuna", "tunacore"], ["Lara", "laraquest"]];
/** Kimlik 40–41: benim istek gönderdiklerim. */
const outgoingPeople: readonly Person[] = [["Barış", "bariscoop"], ["Ada", "adaplays"]];
/** Kimlik 50–52: engellediklerim (user-service engelli hesapları atladığından adları YOKTUR). */
const blockedCount = 3;
/** Kimlik 60–62: henüz ilişkim olmayan, davet koduyla bulunabilen oyuncular. */
const strangerPeople: readonly Person[] = [["Sarp", "sarpvanguard"], ["Yağmur", "yagmurace"], ["Rüzgar", "ruzgarzero"]];

function range(start: number, count: number): UserId[] {
  return Array.from({ length: count }, (_, offset) => mockUserId(start + offset));
}

export const mockIds = {
  friends: range(2, friendPeople.length),
  incoming: range(30, incomingPeople.length),
  outgoing: range(40, outgoingPeople.length),
  blocked: range(50, blockedCount),
  strangers: range(60, strangerPeople.length)
};

/** Başka oyuncuların davet kodları (davet koduyla ekleme akışı için). */
export const mockInviteCodes: Readonly<Record<string, UserId>> = {
  [MOCK_MY_INVITE_CODE]: MOCK_ME,
  SARP7Q2M4K: mockIds.strangers[0],
  YGMR8ACE2P: mockIds.strangers[1],
  RZGR0Z3R9V: mockIds.strangers[2],
  /** Zaten arkadaş olan biri (ALREADY_FRIENDS senaryosu). */
  DENZ1PKYS2: mockIds.friends[0],
  /** Engellenmiş biri (USER_BLOCKED senaryosu). */
  GHST0RNNR1: mockIds.blocked[0]
};

const directory = new Map<UserId, Person>([
  [MOCK_ME, ["Umut", "umuxt"]],
  ...zip(mockIds.friends, friendPeople),
  ...zip(mockIds.incoming, incomingPeople),
  ...zip(mockIds.outgoing, outgoingPeople),
  ...zip(mockIds.strangers, strangerPeople)
]);

function zip(ids: readonly UserId[], people: readonly Person[]): Array<[UserId, Person]> {
  return ids.map((id, index) => [id, people[index]]);
}

/** user-service `profiles/batch` taklidi: dizinde olmayan (ör. engelli) kimlikler atlanır. */
export function mockProfileCards(userIds: readonly UserId[]): Map<UserId, ProfileCard> {
  const cards = new Map<UserId, ProfileCard>();
  for (const userId of userIds) {
    const person = directory.get(userId);
    if (person) {
      cards.set(userId, { userId, displayName: person[0], username: person[1], avatarPath: null });
    }
  }
  return cards;
}

/** Kayıtların oluşturulma zamanları: "şimdi"den geriye, gerçekçi aralıklarla. */
export const mockAges = {
  friendSinceDays: (index: number) => 3 + index * 11,
  incomingMinutes: [12, 60, 26 * 60],
  outgoingMinutes: [3 * 24 * 60, 5 * 24 * 60],
  blockedDays: [14, 14, 30]
};

export const MOCK_TIME = { MINUTE, HOUR, DAY };
