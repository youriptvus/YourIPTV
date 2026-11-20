# Settings security

## Current 1.1.0 Settings default

Ordinary Settings default to unlocked in both distributions. A saved local On or Off choice is preserved. If no choice exists, an already configured Settings PIN or a legacy Settings restriction remains protected; unrelated existing preferences alone do not enable the lock. Initialization persists the resulting choice, so restarts and catalog refreshes cannot reset it. Adding or removing a PIN later does not silently override the local switch.

The default does not disable Admin authorization for lock/PIN management, independent parental restrictions, provider connection limits, or Play app protection. The local switch is excluded from portable backup. Settings visits and pending actions are still revoked when the switch changes or a protected visit ends.

The older policies below are historical and do not override this 1.1.0 behavior.

## Historical 1.0.7 policy — release candidate

Version 1.0.7 supersedes the always-on Settings policy from 1.0.1–1.0.5 with an explicit
**Settings lock** switch. It defaults to **On** on new installations and migration from earlier
releases. An existing or absent custom Settings PIN never determines the switch. Removing a
custom PIN leaves the switch unchanged.

Find the switch in the **Settings Lock** category of full Settings.

Only a successful **master PIN** check can turn Settings lock **Off**, turn it back **On**, or
add, change or remove a custom Settings PIN. A custom Settings PIN can open configuration while
the lock is On, but cannot perform those management operations. The master remains fixed;
master/custom failed-attempt counters and lockouts are retained. Parental PINs, parental
counters, content restrictions and the optional parental playlist guard remain independent.
The Settings master never overrides a parental check. All player features remain free.

| Local Settings lock | Main menu and configuration access |
|---|---|
| **On** (default) | Public **Settings** contains **Playlist Settings** and **Parental Controls**. A separate **Unlock Settings** entry opens all configuration after an accepted master/custom Settings PIN. |
| **Off**, explicitly set by the master | Main shows one **Settings** entry with every settings category, including Playlist Settings, Parental Controls and TV Guide / EPG. A new visit does not ask for a Settings PIN. Independent parental checks still apply. |

The switch is stored only on this installation. It is never exported, imported or accepted from
an Intent, and backup files cannot turn it On or Off. Restoring an old backup does not infer a
switch value from a custom PIN or legacy setting. Normal updates retain an explicit local choice.

Changing the switch revokes existing settings visits, navigation handoffs and pending callbacks.
Every new visit follows the current policy: an accepted Settings PIN while On, or an explicit
local Off choice. Internal manager/editor navigation still uses validated in-memory visits;
leaving or backgrounding ends a visit. Stale visits and callbacks cannot continue under a changed
policy. Guide dialogs check the current visit before showing or changing preferences, dismiss
when the settings activity pauses, and cannot restore authorization from saved state.

**Settings → Playlist Settings** provides Update Playlist, Update EPG, progress and update status
without either PIN in both modes. It does not grant configuration access while the lock is On.
Parental Controls retains its own entry and per-action checks. About remains public.

This is candidate policy, pending fresh APK-bound tests. The historical sections below record
the earlier always-on behavior and its verification; they do not override this current policy.

## Historical policy in 1.0.1–1.0.5

YourIPTV 1.0.1 / versionCode 32 made Settings protection permanent for new installations and
normal in-place upgrades. Watching content and receiving player updates did not require a
Settings PIN. Version 1.0.5 / code 36 kept one authorized Settings visit across its playlist
manager and editor without turning off that lock or creating a persistent/global unlock.

## Historical migration to 1.0.1

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

## Scope of an authorized visit in 1.0.5

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

## Historical 1.0.5 release and verification status

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

## Customer and protected navigation in 1.0.7

With Settings lock **On**, public Settings contains only **Playlist Settings** and **Parental
Controls**. Full configuration requires **Unlock Settings** and an accepted Settings PIN. Guide
preferences are at **Unlock Settings → TV Guide / EPG → Open EPG Settings**; source URLs and
credentials remain in the authorized playlist editor.

With Settings lock explicitly **Off**, **Settings** opens the combined category list, including
**TV Guide / EPG → Open EPG Settings** and **Playlists**, without a Settings PIN. The Settings Lock
category still requires the master PIN for changing the switch in either direction or managing
custom PINs. Opening configuration while Off never grants parental authorization.

In either mode, Playlist Settings keeps PIN-free refresh/status, Parental Controls uses its
independent parental PIN and per-action guards, and About remains public. A paused guide dialog
is dismissed and its visit ends. Re-entry uses a Settings PIN only if the current lock is On.

## Final scoped local verification for 1.0.7

Fresh final validation: **394 tests / 55 classes**, **142 isolated native SQLite/policy checks**, and **43 offline archived-updater checks** passed. The final signed APK retains the official certificate and exact existing permissions. On 192.168.1.150, an in-place ADB retest preserved data and pulled installed bytes matched. Scoped menu/logo, public About/parental, protected EPG wording and 7/7 picker, background relock, master-authorized Off/combined/On and real provider guide-refresh checks passed. The Settings lock was restored **On**. Sound is assumed. No fresh phone, actual automatic download/install/success-popup, seven elapsed days of retention or uninterrupted long-duration playback pass is claimed. Publication remains with the owner.
