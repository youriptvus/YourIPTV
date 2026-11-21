# Settings, Admin and parental security - 1.1.1 / code 52

## Current default and migration

Settings Lock defaults OFF for fresh/unconfigured state in both distributions. Explicit saved On/Off choices remain. Without a saved choice, a configured Settings PIN or legacy Settings restriction retains protection; unrelated old preferences alone do not enable the lock. Initialization persists the choice, so restart/catalogue refresh cannot reset it. Adding/removing a PIN later does not silently change the switch.

The switch is device-local and excluded from portable backup/Intent configuration. Changing policy revokes existing visits, handoffs and pending callbacks. Compatible upgrades retain owner data; do not clear production storage to force migration.

## Ordinary and protected access

With Lock OFF, normal Settings exposes configuration without a Settings PIN. With Lock ON, ordinary Settings still provides public playlist refresh/status, parental navigation and Playback preferences; Unlock Settings requires the current Settings policy. Autoplay Next Episode is an ordinary viewing preference without an Admin PIN, defaults ON only when unset and preserves saved OFF.

The customer distribution retains its approved existing master/custom Settings authorization policy. Only the authorized Admin/master management flow changes the local lock choice or manages Settings PINs; a custom Settings PIN can open a permitted visit but does not become unrestricted Admin authority. No PIN value is documented here.

Settings visit authorization is temporary, target/visit-bound where applicable and revoked on leaving/backgrounding or policy change. A restored screen does not grant a protected visit. PIN attempts retain their throttling/lockouts; do not treat an Admin/master path as a parental bypass. About remains public.

## Independent protections and data

Parental PINs, locked/adult groups, optional playlist-management gates and provider connection limits remain independent of Settings Lock and autoplay. An unlocked Settings page does not authorize restricted playback. Source/account identity checks apply to retained metadata, archive playback and progress; changed accounts cannot receive stale progress writes.

PINs use existing local salted verification storage, not database encryption or a remote identity. Provider credentials can exist in readable app-private data and explicit backups. Backups exclude PIN secrets and the local Settings Lock switch; protect exported files. No private key/PIN/credential values belong in source or reports. Play automatic protection/signing is separate and remains enabled under its existing configuration.

See [current QA scope](QA_CHECKLIST.md) and matching release sidecars for actual tests and limits. Historical always-on/default-ON policies below do not override this current1.1.1 guidance.

## Historical evidence

The [preserved earlier document](history/SETTINGS_SECURITY-before-1.1.1.md) retains older implementation/QA records with their original dates and limits. It does not override the maintained 1.1.1/code52 guidance above.
