/** Oturum kapısının arkasındaki sosyal görünümler (`index.ts` bunları açar). */
import { BlockedView as BlockedScreen } from "./blocked-view";
import { FriendsView as FriendsScreen } from "./friends-view";
import { RequestsView as RequestsScreen, SentRequestsView as SentRequestsScreen } from "./requests-view";
import { SocialSessionGate } from "./social-session-gate";

export function FriendsView() {
  return (
    <SocialSessionGate>
      <FriendsScreen />
    </SocialSessionGate>
  );
}

export function RequestsView() {
  return (
    <SocialSessionGate>
      <RequestsScreen />
    </SocialSessionGate>
  );
}

export function SentRequestsView() {
  return (
    <SocialSessionGate>
      <SentRequestsScreen />
    </SocialSessionGate>
  );
}

export function BlockedView() {
  return (
    <SocialSessionGate>
      <BlockedScreen />
    </SocialSessionGate>
  );
}
