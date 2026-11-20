# VOD usability reference

The owner's installed TiviMate was inspected through ADB on the authorized TV box before implementation. Private screenshots and UI hierarchies remain outside both repositories. No proprietary code, application configuration, credentials or graphics were extracted or incorporated.

Observed movie navigation used a left category rail, History and All movies, a focused poster grid with titles and ratings, and a details screen with backdrop, synopsis and available year/runtime/genre/cast metadata. The inspected unfinished movie offered separate Resume and Play from beginning actions. Back returned through details, posters and categories.

Observed series navigation used a separate Shows section and category/history posters. Selecting the inspected series opened metadata and a prominent action identifying the last selected season/episode, followed by a horizontal episode rail under its season heading. Episode focus had a clear border and moved through the rail; the episode menu exposed external playback. The inspected series had one season. Multi-season transitions, natural completion, watched-state thresholds and return-from-playback behavior were not exercised in the reference application; no claims about its internal implementation are made.

YourIPTV already had a shared Media3 player, poster browsing, on-demand metadata and recycled season rails. The redesign extends those components with explicit durable completion state, source/account identities, exact-episode Continue Watching, cross-season playback queues, resume/restart actions and optional cancellable next-episode playback. It retains YourIPTV artwork, source policies and parental protection rather than copying the reference application's assets or implementation.

Actual YourIPTV validation results are recorded separately from these reference observations. Synthetic two-season tests verify behavior that was not observed directly in TiviMate; configured provider playback is a separate device check.
