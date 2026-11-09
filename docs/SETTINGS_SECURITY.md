# Settings security in 1.0.1

YourIPTV 1.0.1 / versionCode 32 makes Settings protection permanent. This policy applies to new
installations and normal in-place upgrades. It protects configuration; watching content and
receiving normal player updates do not require a Settings PIN. All player features remain free.

## Migration from earlier versions

| Previous state | Behavior after upgrading |
|---|---|
| Settings protection was off | Protection becomes enabled; the permanent master opens Settings. Any previously stored custom Settings PIN is retained. |
| A custom Settings PIN was enabled | The existing custom PIN still opens Settings. The permanent master also opens Settings. |
| Settings used the parental PIN through a legacy shared mode | Settings uses the permanent master or a retained custom Settings PIN. The parental PIN no longer grants Settings access through the legacy shared mode. |
| A Settings failed-attempt lockout was active | The lockout still applies to both master and custom attempts. Restarting does not bypass it. |
| Parental controls were configured | Parental PINs, locked content, optional playlist protection and parental failed-attempt counters remain unchanged. |

There is no Settings protection off switch. The master cannot be changed or removed through the
app. An existing custom PIN is preserved rather than silently replaced. Only a successful master
check can authorize adding, changing or removing an optional custom Settings PIN. A custom PIN
alone cannot authorize those management operations. Removing it leaves the master and Settings
protection active. New custom Settings PINs must differ from the parental PIN.

Both master and custom attempts use the same Settings failed-attempt counter and persistent
lockout. The master is subject to that lockout, including during custom PIN management. Parental
authentication and its counter are separate; a Settings master does not unlock parental content.

Backups do not provide a way to turn off this Settings policy or export a custom PIN. Normal
in-place app updates retain local settings and existing custom PINs. Never uninstall or clear app
data as part of a normal upgrade; doing so removes playlists and other local data.

## Scope of an authorized visit

Either accepted Settings PIN can open protected Settings for one visit. Existing playlist
connection details require Settings authorization before they are displayed and again when the
app checks permission to save. If parental playlist protection is enabled, its independent check
still applies. Leaving the protected visit ends its authorization; it is not a permanent global
unlock. Editing existing connections remains available without disabling Settings protection.

The app's system document picker is the only narrow exception: a backup import or export started
from an authorized Settings visit can retain that same visit while the picker is open. The result
must still belong to the original authorized visit. It cannot grant access to a later visit.
Activity or process recreation starts locked and does not restore authorization from saved state.
Android permission pages, About, other apps, Home and screen-off do not retain the Settings visit;
returning requires authentication again.

About is directly available from the main menu on phones and Android TV. It shows the app version,
free-use notices and third-party credits without asking for a Settings PIN. Visiting About does
not grant access to Settings or playlist credentials.

## Limits of a permanent shared master

The master value is supplied privately to the responsible owner or technician. It is shared
across installations using this policy and can be learned, copied or discovered in the distributed
app. Anyone who knows it can open Settings, subject to the same failed-attempt lockout. A custom
Settings PIN does not revoke the master. This is an intentional maintenance policy, not a private
owner identity, a per-device secret, a licensing system or a guarantee against APK copying.

Keep provider credentials private and use independent parental protection where appropriate.
The Settings master is not accepted as an override for parental checks. No billing, feature
paywall, rebranding integration or playback-buffer change is introduced by this release.

## Release and verification status

This document describes the approved 1.0.1 behavior. Local preparation does not publish the
release or certify device behavior. Record completed tests and APK verification with the matching
release artifacts. Earlier 1.0.0 and 0.5.6 test results remain historical evidence only.

GitHub publication is performed manually by the owner. The website backup currently serves
1.0.0 / code 31; preparation of 1.0.1 does not update that server. Website deployment and device
installation are separate actions requiring authorization.
