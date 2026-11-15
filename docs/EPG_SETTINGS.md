# EPG Settings

Settings lock defaults to **On** for new installations and migration from earlier releases.
With it On, authenticate with the existing Settings PIN, then open **Unlock Settings → TV Guide
/ EPG → Open EPG Settings** with the TV remote or touch. Public Settings does not expose guide
preferences in this mode.

Only the master PIN can explicitly turn Settings lock **Off** or back **On**, or manage custom
Settings PINs. An absent or removed custom PIN never means Off. When explicitly Off, Main has
one **Settings** entry containing all categories; open **Settings → TV Guide / EPG → Open EPG
Settings** without a Settings PIN. Parental checks remain independent in either mode.

Guide preferences require a visit authorized under the current policy. Pausing or leaving the
settings activity dismisses the dialog and revokes that visit. Returning requires a Settings
PIN while the lock is On; explicit Off allows a new visit without one. Changing the switch
invalidates existing visits and pending callbacks. The local switch is never backed up,
imported, exported or accepted from an Intent.

- **Past guide data to keep:** 0 to 14 days. Existing custom hour values display their exact
  hours until a whole-day value is selected.
- **Future guide coverage:** 1 to 14 days of locally stored listings, when supplied.
- **Guide time offset:** the existing provider-time correction in hours.
- **Program descriptions:** stored when supplied by the provider.
- **Automatic interval:** information from the active playlist's saved interval. Configure
  that existing interval under **Playlists** in full Settings; 16 hours remains the default.
- **Update on app start:** enabled information. A fresh foreground process launch requests
  background guide work while cached information remains usable. This is not a new switch.
- **Update EPG:** uses the shared coordinator, progress, attempt/error and successful timestamps.

New defaults are 7 past and 7 future days. A one-time migration raises shorter existing values
to 7 days and preserves longer values. It does not repeatedly overwrite later choices.
Backup keys retain their existing units: `epg_past_h` in hours and `epg_future_d` in days.
Selecting retention does not clear the guide or download another feed by itself; a successful
EPG update applies the selected storage window atomically. Provider catch-up can require a
longer supported past window. The supplied feed determines actual coverage and descriptions;
missing provider listings and history already deleted cannot be recreated.
Changing the window during an import rejects that outdated publication and preserves the old
guide; a later manual or scheduled retry uses the current window.

Valid partial updates merge with same-source useful cached entries. Incoming overlapping
schedules replace outdated entries, and expired data is pruned. A provider cancellation omitted
from a partial feed can remain until a later correction overlaps it or it expires. Unknown
legacy cache remains visible until a successful replacement establishes its source. Failure,
invalid data and cancellation preserve the previous committed guide. Time-window queries and
bounded caching avoid truncating future listings behind the oldest 500 programs.

With Settings lock On, public **Settings** contains only **Playlist Settings** and **Parental
Controls**. With it Off, Settings combines every category. In either mode,
**Settings → Playlist Settings** provides **Update Playlist**, **Update EPG**, progress and update
status without either PIN; refresh actions do not change guide preferences or source details.
**Settings → Parental Controls** uses the existing independent parental PIN and per-action checks.
Provider credentials and EPG source editing remain in the playlist editor in full Settings,
which requires a Settings PIN while its lock is On. About remains public on Main.

This is the locally verified 1.0.7/code 38 candidate. Exact evidence and limits are summarized
below; publication remains with the owner.

## Final scoped local verification for 1.0.7

Fresh final validation: **394 tests / 55 classes**, **142 isolated native SQLite/policy checks**, and **43 offline archived-updater checks** passed. The final signed APK retains the official certificate and exact existing permissions. On 192.168.1.150, an in-place ADB retest preserved data and pulled installed bytes matched. Scoped menu/logo, public About/parental, protected EPG wording and 7/7 picker, background relock, master-authorized Off/combined/On and real provider guide-refresh checks passed. The Settings lock was restored **On**. Sound is assumed. No fresh phone, actual automatic download/install/success-popup, seven elapsed days of retention or uninterrupted long-duration playback pass is claimed. Publication remains with the owner.
