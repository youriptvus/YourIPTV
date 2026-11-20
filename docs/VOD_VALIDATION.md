# 1.1.0 / code 51 validation

This is the next independently prepared release candidate. Customer baseline was 1.0.9/code44; the submitted Play closed-test baseline was 1.0.9/code50. No submitted Play artifact was replaced, no Console release changed, and no APK distributed by this preparation. The owner will push and publish manually.

## Automated checks

| Check | Result |
| --- | --- |
| Customer release unit tests | 445 tests, 58 classes; no failures, errors or skips |
| Play release unit tests | 431 tests, 58 classes; no failures, errors or skips |
| Customer APK and Play APK/AAB | Signed release assembly succeeded |
| Release lint | No errors or fatal findings; 302 customer and 298 Play warnings remain |
| Shared implementation parity | All 36 checks passed |
| Native SQLite/progress checks | 18 on Android TV and 18 on a physical Moto phone passed |
| Media3/details/navigation checks | 18 on Android TV and 19 on the physical phone passed |
| Existing Play regression checks | All 19 passed on the phone, including local Admin, Settings Lock, parental separation, transport policy, touch, rotation and offline privacy |
| Packaged Play candidate | Bundletool, signatures, expected certificate, permissions, backup/network policy and known-secret-file/pattern audit passed |

Device instrumentation used the separate QA package and a private loopback fixture with one movie and two seasons, never the submitted production phone installation. It verified actual decoded frames, ordered periodic saves, exact resume/restart, watched/reset, account isolation and stale-source revocation, refresh persistence, cross-season next, cancellable autoplay, background handling, D-pad focus and real phone touch/rotation. No loopback cleartext exception is in the production Play manifest. A settled landscape screenshot was inspected after rotation.

An intermediate TV run missed the short-clip timing checks for periodic save and subsequent resume; its unchanged production build passed the subsequent complete TV run. Earlier harness failures were retained in the private evidence. The existing broad TV Play smoke harness refused unknown pre-existing QA playlist data, which was preserved rather than erased. The phone ran that complete suite successfully. Tests do not establish uninterrupted playback on every provider or physical remote/audio behavior.

## Configured customer TV

The officially signed APK upgraded the existing code44 installation in place to code51 without clearing data or uninstalling another app. Pulled installed APK bytes matched the candidate. Existing catalog, account, PIN and TiviMate installation were retained. No customer credentials or app database were extracted.

A configured movie played, was sought to a later position, resumed, and restarted from the beginning. Its saved position remained after a full application process restart and appeared once in Continue Watching. A configured five-season series played S1E7, saved and resumed that episode, restarted it, then reached the normal END choices after a user seek to the end. Next episode opened S2E1 and returning to details focused S2E1, with S1E7 marked watched. Up/down restored the remembered episode in each season. These are scoped functional observations, not long-duration endurance tests.

One other configured movie returned HTTP406; its unavailability is not counted as a playback pass. Some playback windows did not expose an idle accessibility hierarchy, so private screenshots were used for the visible picture and timer checks. Sound is assumed, not newly confirmed.

## Distribution differences

One application supports TV and phones in both repositories. Both versions use the same shared Media3 player, progress storage, details, episode queues and navigation. Customer keeps its existing updater, HTTP source compatibility, minSdk23 and approved security initialization. Play keeps minSdk24, HTTPS-only transport, external signing and no self-installer/overlay permission. Protection was not disabled. Autoplay next defaults off; no player feature paywall was added.

Private device screenshots, raw instrumentation evidence and signing properties remain outside committed source. A local release handoff records final artifact hashes, installed-byte verification, offline archived-updater results and any additional reboot/refresh checks. Neither this report nor a passing build guarantees Google approval.
