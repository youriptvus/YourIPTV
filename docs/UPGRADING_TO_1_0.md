# Upgrading directly to YourIPTV 1.0.0

This is a stable promotion of the accepted clean 0.5.6 player, with Android version
code 31. It adds the bundled 1.0.0 release notes but changes no playback, provider,
PIN, updater, permission or rebranding behavior. All player features remain free.

## Existing installations

| Installed official version | Direct update to 1.0.0 |
| --- | --- |
| 0.4.1 through 0.5.6, code below 31, using the current GitHub update address | Supported by the updater's version and package policies. No intermediate install needed. |
| 0.4.0 | One-time manual in-place install; its installed updater uses a retired address. |
| Versions before 0.4.0 | One-time manual in-place install; the current updater and its permissions were introduced later. |
| Different package, signing certificate or app label; debug builds; custom test update address | Outside the official update path. Inspect individually; do not replace keys or uninstall to force an update. |
| Installed version code 31 or higher | Code 31 is not an update. A genuinely higher code is required. |

The same official package (`com.youriptv.app`), signing certificate, app label,
minimum Android version and requested permission set must be retained. A normal
in-place update preserves playlists, settings and app data. Never uninstall or
clear data to upgrade. Android installation permissions and confirmation rules
still apply; an update cannot bypass device restrictions.

Powered-off boxes do not miss a push: the app checks the latest manifest when it
is opened again with Automatic updates enabled. The release stays available until
replaced by a newer release. This is the existing check-and-download workflow.

## Manual publication

The owner confirmed the currently published release is **0.5.6 / code 30** on
November 8, 2026. The 1.0.0 GitHub manifest therefore uses `previousVersionCode: 30`.
Code 30 customers can Install or Ignore; earlier supported versions receive the
existing required-update prompt. This threshold does not require sequential updates.

After the matching signed APK has passed verification, the owner publishes a
normal release tagged `v1.0.0`, marks it Latest, and attaches exactly:

- `YourIPTV.apk`: the verified signed 1.0.0 / code 31 APK.
- `update.json`: generated from those exact APK bytes.

Keep the GitHub manifest's `apk` value as
`https://github.com/youriptvus/YourIPTV/releases/download/v1.0.0/YourIPTV.apk`.
Clients before 0.5.6 reject website APK URLs, so publishing only website metadata
will not reach them. Attach the APK before making the matching manifest available.

The website mirror is optional and separate: its versioned APK must contain the
same bytes, with its own matching website manifest. Local preparation authorizes
no publication or deployment. A stale website manifest cannot downgrade a newer
known release on 0.5.6+ clients.

## Verification scope

Local evidence is held outside this public source checkout at
`D:\APK\work\YourIPTV-1.0.0-verification`:

- Native APK audit of retained official APKs: package, certificate, permissions,
  app label and Android requirements.
- Offline tests of archived updater implementations against the actual generated
  1.0.0 metadata and verified candidate identity.
- Full player unit suite and native verification of the signed 1.0.0 APK.

These checks establish local compatibility. They do not claim a newly performed
device installation, data-migration test, live GitHub check or publication.
Historical 0.5.6 device results remain historical. Pending buffer and companion
work is excluded and has no assigned release version.
