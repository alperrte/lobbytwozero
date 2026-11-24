import { apiUrl, refreshSession } from "@/shared/lib/api/client";
import type { StreamEvent, StreamStatus } from "../types/messaging.types";

/**
 * Gerçek zamanlı mesaj akışı (`GET /api/v1/messaging/stream`, SSE; ADR 0001). Kimlik çerezle gider (`withCredentials`).
 *
 * - Ağ kopmasında tarayıcı `EventSource`'u kendisi yeniden bağlar (durum `reconnecting`).
 * - Sunucu 401/429 gibi bir yanıtla akışı reddederse `EventSource` kalıcı kapanır: oturum yenilenir ve üstel bekleme
 *   (1 sn → 30 sn) ile yeniden açılır. Erişim token'ı kısa ömürlü olduğundan bu, akışın olağan yenilenme yoludur.
 * - Her yeniden bağlantıda (`ready` olayı, ilk bağlantı hariç) `onResync` çağrılır: istemci sohbet listesini ve açık sohbeti
 *   sunucudan eşitler; mesajlar DB'de olduğu için kaybolmaz (web-rules §16).
 * - Bozuk JSON taşıyan olay yok sayılır (akış düşmez).
 */
export type StreamHandlers = {
  onEvent: (event: StreamEvent) => void;
  onStatus: (status: StreamStatus) => void;
  onResync: () => void;
};

const STREAM_PATH = "/api/v1/messaging/stream" as const;
const EVENT_TYPES = ["message", "read", "message_deleted", "reaction", "message_hidden"] as const;
const MIN_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 30_000;
/** Bu kadar ardışık başarısız açılıştan sonra durum "bağlantı yok" olur (denemeler sürer). */
const OFFLINE_AFTER_FAILURES = 3;
/** `EventSource.CLOSED` (jsdom'da `EventSource` yoktur; sabit değer kullanılır). */
const READY_STATE_CLOSED = 2;

type EventSourceFactory = (url: string) => EventSource;

const defaultFactory: EventSourceFactory = (url) => new EventSource(url, { withCredentials: true });

export function openMessagingStream(
  handlers: StreamHandlers,
  factory: EventSourceFactory = defaultFactory
): () => void {
  let source: EventSource | null = null;
  let stopped = false;
  let connectedOnce = false;
  let failures = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;

  const statusWhileOpening = (): StreamStatus => {
    if (failures >= OFFLINE_AFTER_FAILURES) return "offline";
    return connectedOnce ? "reconnecting" : "connecting";
  };

  const scheduleReopen = () => {
    if (stopped) return;
    failures += 1;
    handlers.onStatus(failures >= OFFLINE_AFTER_FAILURES ? "offline" : "reconnecting");
    const delay = Math.min(MAX_BACKOFF_MS, MIN_BACKOFF_MS * 2 ** (failures - 1));
    timer = setTimeout(() => {
      timer = null;
      void refreshSession()
        .catch(() => false)
        .then(() => {
          if (!stopped) open();
        });
    }, delay);
  };

  const open = () => {
    const current = factory(apiUrl(STREAM_PATH));
    source = current;
    handlers.onStatus(statusWhileOpening());

    current.addEventListener("ready", () => {
      failures = 0;
      handlers.onStatus("live");
      if (connectedOnce) {
        handlers.onResync();
      }
      connectedOnce = true;
    });

    for (const type of EVENT_TYPES) {
      current.addEventListener(type, (event) => {
        const raw = (event as MessageEvent<string>).data;
        try {
          handlers.onEvent({ type, data: JSON.parse(raw) } as StreamEvent);
        } catch {
          // Bozuk olay: yok say. Eksik kalan durum yeniden eşitlemede düzelir.
        }
      });
    }

    current.onerror = () => {
      if (stopped || source !== current) return;
      if (current.readyState === READY_STATE_CLOSED) {
        current.close();
        source = null;
        scheduleReopen();
      } else {
        handlers.onStatus("reconnecting");
      }
    };
  };

  open();

  return () => {
    stopped = true;
    if (timer) clearTimeout(timer);
    source?.close();
    source = null;
  };
}
