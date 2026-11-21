# Current QA scope - 1.1.1 / code 52

The candidate is prepared locally; the confirmed published baseline remains 1.1.0/code51. This document separates actual isolated-device results from host checks and publication. Release APK/AAB hashes, final unit/build/lint totals and archived-updater results are maintained in the matching release's summary, `SHA256SUMS.txt`, `PUBLISH.txt` and verification sidecars rather than embedded as a self-referencing source commit.

## Recorded successful device checks

| Suite | Moto phone / API36 | TV .150 / API34 |
|---|---:|---:|
| Settings defaults/preservation |14|14|
| Native SQLite progress/session/history policy |38|38|
| VOD playback/details/history/episode UI |32|31|
| Actual guide/held-Back/navigation input |9|9|
| Total |93|92|

All185 recorded scoped checks passed. They ran in `com.youriptv.app.playtest` with generated authorized fixtures. Installed isolated base APK bytes were verified on both devices. Moto's final VOD UI result uses the focused foreground-ready fixture rerun on the same base APK; the complete final-summary sidecar records the test-APK/suite provenance. Earlier failures are retained, not erased or counted as passing.

Coverage includes grouped parent-series versus individual movie history, last/unfinished resume selection, per-episode green bars, completion, explicit replay, finite/cross-season autoplay, saved OFF/manual Next, manual watched/unwatched/clear races, stale/signed-URL/session/account identity, observation ordering and restart persistence. Navigation checks cover cold requested source A versus active source B sharing a URL, current-time reopening, active past/future refresh preservation, actual stable row focus on phone/TV, normal Back and held fullscreen return. Existing Settings choices/defaults and source restrictions remain guarded.

The final device evidence is `D:\APK\work\YourIPTV-history-device-qa-20261121T163606Z\watch-guard-final\final-summary.json`, with its referenced raw results/captures. Preserve these private paths/files outside published source archives; this document carries only the non-secret scope/results. See [VOD_VALIDATION.md](VOD_VALIDATION.md) and matching public release sidecars for the maintained release report.

## Preservation and limits

Production packages/data were not cleared or replaced for these isolated tests. The Play-installed production version remains code51 in the recorded phone/TV identity checks. Sound is assumed per owner instruction, not independently confirmed. Generated fixture checks do not establish uninterrupted provider playback, every codec/device, physical tablet coverage, OEM cloud/device-transfer behavior, actual customer automatic installation/success popup, or Google Play-generated protected delivery.

Customer automatic update download/install and its actual receipt remain separate from isolated QA and ADB installation. Keep the official package/signing identity and exact metadata/archived-updater checks.

## Release review checklist

- Bind final source/toolchain/build checks to the exact signed APK/AAB and preserve previous candidates.
- Complete the relevant unit/lint/compiled-artifact checks and source/secret-file scans; do not copy historical totals as fresh results.
- Verify shared behavior and retain distribution-specific signing, SDK, updater, backup/network and security configuration.
- Review current privacy policy/Data safety, reviewer access and all actual Console requirements before intentional submission.
- Verify unused code52 and remote canonical-tag/source provenance; preserve owner repository Git metadata during source synchronization.
- Publish/deploy only by the owner's deliberate action after public APK/source verification; local QA is not Google approval.

## Historical evidence

The [preserved earlier document](history/QA_CHECKLIST-before-1.1.1.md) retains older implementation/QA records with their original dates and limits. It does not override the maintained 1.1.1/code52 guidance above.
