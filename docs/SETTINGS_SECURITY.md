# Settings security

YourIPTV 1.0.1 / versionCode 32 makes Settings protection permanent. This policy applies to new
installations and normal in-place upgrades. It protects configuration; watching content and
receiving normal player updates do not require a Settings PIN. All player features remain free.
Version 1.0.5 / code 36 keeps one authorized Settings visit across its playlist manager and
editor. It does not turn the lock off or create a persistent/global unlock.

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

In 1.0.5, **Settings → Playlists → Manage playlists** and **Edit current playlist** can carry
that same visit into authorized internal manager/editor screens and back. Refreshing, editing
and confirming deletion in this flow do not ask for a second Settings PIN. The independent
parental playlist PIN still applies when configured. Opening the editor directly outside an
unlocked Settings flow requires Settings authentication; main-menu viewing, selecting and
refreshing retain their existing permission rules. Direct sensitive deletion also requires
Settings authorization.

Internal navigation uses in-memory, one-use handoffs tied to the authenticated visit and intended
screen/playlist. Missing, stale, replayed and wrong-target handoffs are rejected. Boolean Intent
extras, saved state and stored preferences cannot grant the visit. Protected content/credentials
stay hidden until authorized. Leaving the flow, Home/background, screen-off or process death
revokes it. Activity/process recreation does not restore authority from saved state. No timer
or permanent global Settings unlock is introduced.

A backup import/export launched from the authorized Settings visit retains its existing narrow
system-document-picker exception: its result must still belong to that original visit and cannot
grant a later one. Android permission pages, About and other apps do not retain the Settings
visit; returning requires authentication again.

## Playlist deletion and password visibility

**Delete playlist** is visible in the manager and opens a chooser followed by a named confirmation.
Cancel is the safe default. Context-menu deletion uses the same protected handler. Refreshing
disables deletion, and the current authorization and stable playlist ID are checked again before
committing. A stale/missing selection must not remove a different playlist.

The existing cleanup removes that playlist's catalog, guide, favorites, history, reminders and
pending recording entries. Finished recording files are preserved while retained credential-
bearing stream URLs are cleared. Other playlists remain intact; deleting the active playlist
selects a remaining one or shows the empty state. Do not delete real owner playlists to test it.

The editor's **Show password / Hide password** control starts masked. Toggling preserves exact
password text, spaces and cursor position. Leaving/backgrounding or changing setup mode masks
it again, and revealed state is never restored after recreation. Showing the password does not
change the saved value or authorize anything. Keep shown credentials out of screenshots/logs.

## Public About

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

The always-on master/custom Settings policy originated in 1.0.1. The approved 1.0.5 change extends
one protected visit only across validated playlist-management navigation. Fresh **324 tests in
44 classes**, the signed release/native checks, isolated deletion SQL fixtures and scoped TV
verification passed. The TV required one Settings PIN across the authorized manager/editor/Back
flow, relocked after Home/background, and required a PIN for direct editing/deletion. Password
Show/Hide/remask and named Delete confirmation/Cancel passed without saving synthetic input or
deleting owner data. Independent parental protections have offline coverage; no fresh parental
configuration or destructive owner-data test was performed. Earlier 1.0.4/1.0.3 results remain
historical. See [the QA checklist](QA_CHECKLIST.md) for exact evidence and limits. Actual automatic
download/install and the successful-update popup were not device-tested; publication/staging
status belongs in the matching publication instructions and final audit.

GitHub publication is performed manually by the owner. The last recorded website backup is
1.0.2/code 33; no server state was queried for 1.0.5. This work includes only authorized local
preparation and TV checks; it includes no uploads/deployment/publication, key changes, rebranding
or buffer implementation.
