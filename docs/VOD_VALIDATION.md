# 1.1.1 / code 52 validation

Validated locally on 21 November 2026. The current shared implementation passed **185 instrumented checks** using the separate `com.youriptv.app.playtest` package: 93 on a Moto g 2025 running Android 16/API 36, and 92 on an onn. Streaming Device 4K pro running Android 14/API 34. Generated loopback media produced actual decoded frames. Production phone and TV installations remained at 1.1.0/code51 with unchanged installation identities during this QA phase; no production app or user data was replaced or cleared.

These are shared-implementation checks through the isolated Play QA build. Customer/Play release assembly, signing, source parity, updater and packaging evidence are recorded separately in the release handoff. This report does not claim that the isolated APK is the production APK or a protected Google Play download.

## Current device results

| Suite | Moto API36 | TV API34 |
| --- | ---: | ---: |
| Settings defaults and migration | 14 passed | 14 passed |
| Native SQLite, history and watch-session policies | 38 passed | 38 passed |
| VOD/details/playback UI | 32 passed | 31 passed |
| Physical Back and guide navigation UI | 9 passed | 9 passed |
| Total | **93 passed** | **92 passed** |

Verified behavior includes:

- Main History keeps individual movies and one parent entry per series. Shows History renders one parent poster; opening it restores series details and episode progress. Grouping occurs before the display limit, preserves distinct same-title identities, isolates accounts and leaves original episode positions intact, including legacy M3U history.
- Episode cards show accurate partial green progress, full completed progress and no unwatched line. Last-watched/continue summaries survive focus changes. Exact resume, explicit restart, saved season/episode focus, rotation, refresh persistence and manual Next selection passed.
- Autoplay defaults ON when no choice exists and preserves saved OFF. Natural completion supports cancellable cross-season continuation, manual Next with autoplay OFF, return after completion when OFF, and final-episode return without wrapping. Settings Lock defaults OFF while preserving explicit choices, prior Settings PIN protection and independent Admin/parental rules.
- Manual watched/unwatched, individual clear and whole-history clear cannot be overwritten by stale stopping checkpoints or END callbacks. Fresh intentional playback can save again. Session/source/store/content identity, signed-URL refresh identity, newer-session ownership and observation ordering passed deterministic native SQLite checks; delayed older episode observations retain the genuinely newer continuation/history entry.
- Timed injected Back-key holds restore the current stream from browsing, ordinary Settings and the public locked-Settings panel. Releasing the hold does not immediately exit fullscreen; short Back retains its normal action. Changed-source/no-current guards passed.
- Cold guide opening selects the requested source/channel even when another source has the same URL. Requested rows retain actual focus after settling on phone and TV. Explicit warm reopening returns from a future slot to the current programme/window; ordinary refresh preserves active past/future browsing. Launcher return restores the remembered live source while explicit guide requests avoid unwanted fullscreen playback.
- Catch-up opens the archive player with its exact archive URL/flag and renders generated video. Account changes block stale archive foreground/retry playback without creating VOD history. This does not establish an external provider's archive availability.

## Exact artifact and suite provenance

Both devices' pulled installed QA base APK bytes matched:

`02afe9ef2c7956258bf7501d53b7da6fc87dc14515ef61c1344ddf7dfcc13fc0`

The base four-suite test APK was:

`df2b97972c51ebd643f3bccdf35681020cc608ff3390fbdca1af7e86f96a761f`

It supplies all TV results and the Moto Settings/native/navigation results. The final Moto VOD result is the focused 32-check run on the **same app binary**, using test APK:

`6f753ce210858ddcc550413d3f837efe9c0ab10b002d6b8c8ca20de2870bc964`

That harness waits for the Player to be RESUMED, window-focused and shown before user pause/seek actions. Before-ready diagnostics captured an unfocused/buffering transition; before-seek diagnostics confirmed a focused READY player and a valid watch-session revision. The cross-season deadline remained 20 seconds and all playback outcomes remained asserted. The background case likewise waits for actual STOP/player disposal before checking restored end choices. No separate production autoplay/background correction is claimed from those harness adjustments.

The authoritative private index is `D:\APK\work\YourIPTV-history-device-qa-20261121T163606Z\watch-guard-final\final-summary.json`. It records per-suite provenance, installed-byte checks, unchanged production identities, representative settled captures and superseded failure paths. Successful Moto VOD captures come from `ui-foreground-final/moto/captures`; navigation and TV captures come from `watch-guard-final`. Earlier diagnostic/failure files remain preserved and are not fresh passing results.

## Limits and historical evidence

Samsung remained unauthorized in ADB and was not tested. The existing TV QA package contained only the two demo playlists; no real-provider or Terra Nova source was available, and no production configuration was copied or new provider configured. Sound was assumed, not explicitly verified. Key/touch actions were injected; physical remote/HDMI-CEC hardware was not separately validated. There was no new protected Play delivery, external-provider catch-up test, long-duration soak, production-device upgrade or publication in this QA phase. Passing checks do not guarantee Google approval or uninterrupted playback on every provider/device.

Historical full-provider HTTP M3U/Xtream source-import evidence remains separate in the prior source-import/release handoff. It is not part of the current 185 checks and was not rerun here. Current generated HTTP playback demonstrates the fixture transport path, not a fresh external-provider account/catalogue import. Both current distributions support user-supplied HTTP and HTTPS sources; the older report's HTTPS-only transport and default-OFF autoplay statements describe its historical candidate only.

The original 1.1.0/code51 report was archived verbatim on 21 November 2026 as [historical validation](history/VOD_VALIDATION-1.1.0.md). Its old test counts, configured-provider observations, reboot/refresh evidence, lint totals and distribution statements remain historical; they must not be presented as new 1.1.1 checks.
