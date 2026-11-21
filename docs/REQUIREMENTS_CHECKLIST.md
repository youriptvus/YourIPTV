# Current requirements - 1.1.1 / code 52

These checkmarks describe implemented behavior with scoped isolated device coverage. They are not Google approval or a claim that every provider/device combination passed. The confirmed published baseline is 1.1.0/code51; 1.1.1/code52 remains locally prepared. Final build/host/archived-updater results and exact hashes belong to the matching release sidecars.

## Current application behavior

- [x] One production application supports Android phones and Android TV with touch and remote navigation.
- [x] Generic authorized M3U/Xtream/XMLTV sources retain explicit HTTP/HTTPS schemes; HTTPS validation/downgrade protection remains.
- [x] Main history groups stable, routable episode parents into one series entry; movies remain individual.
- [x] Seasons/episodes retain independent positions, green partial/completed bars, Resume, Play From Start and manual selection.
- [x] Continue prioritizes the latest unfinished episode, then an available later episode after completion; ambiguous legacy identities are not guessed by title.
- [x] Autoplay defaults ON only when unset, preserves saved OFF, is available in ordinary Playback without Admin, and stops at the finite series end; manual Next remains available.
- [x] Manual watched/unwatched/clear actions invalidate stale playback writes; fresh intentional sessions can save; same-session older observations cannot regress newer progress.
- [x] Existing history/data are preserved; this amendment adds RAM ownership/order guards without a destructive migration.
- [x] Explicit guide reopening selects the requested source/channel at now; committed refresh preserves actively browsed guide time.
- [x] Cold guide focus waits for valid layout/window readiness and cancels stale focus ownership on later user navigation.
- [x] Held physical Back restores fullscreen without a duplicate short Back; ordinary Back still navigates.
- [x] Launcher re-entry can resume the remembered live source after any app screen, respecting restrictions and explicit guide requests.
- [x] Settings Lock defaults OFF when unconfigured while saved choices/prior protection remain; Admin/parental policy stays independent.
- [x] Standard player features have no purchase/activation/paywall requirement; provider accounts/limits and Android restrictions remain applicable.

## Distribution and publication gates

Customer: minimum API23, target36, existing official signing identity and APK updater; complete direct upgrades where installed-client/platform support permits. PreviousVersionCode51 controls optional/required prompts only.

Preserve production data during QA; use isolated generated fixtures. Keep keys/configuration outside source archives. Verify code52 is unused in all relevant release/Console histories before publication. Owner commits/pushes/uploads/deploys deliberately; no automatic publication is authorized by a checklist. Privacy and Data safety must reflect HTTP transfers. Remote canonical customer tag/source provenance must be verified before publication; do not push the preserved older development tag or force-update an existing remote tag.

## Evidence

The successful final isolated device record contains185 scoped checks:93 on Moto/API36 and92 on TV/API34. Native progress/defaults, VOD UI and navigation suites are distinguished in [QA_CHECKLIST.md](QA_CHECKLIST.md) and final sidecars. Host/build/parser checks are separate evidence, not inferred from device success. Historical checklists/counts remain available below with their original scope.

## Historical evidence

The [preserved earlier document](history/REQUIREMENTS_CHECKLIST-before-1.1.1.md) retains older implementation/QA records with their original dates and limits. It does not override the maintained 1.1.1/code52 guidance above.
