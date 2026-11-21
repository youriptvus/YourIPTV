# VOD history and progress - 1.1.1 / code 52

Movies and episodes use the shared Media3 player and existing progress database. This amendment adds no destructive schema migration and does not erase existing history. Live TV, catch-up and local files do not enter VOD resume storage.

## History and continuation

Main History shows one parent-series entry for episodes with a stable routable parent identity. It retains the series poster, last watched season/episode and progress. Opening it returns to seasons/episodes. Movies stay individual. Continue Watching retains exact episode context; the series Continue action prioritizes the most recently watched unfinished episode, then an available episode after completion. A completed final episode never causes an automatic wrap to the beginning.

Identity is scoped to playlist/provider account and stable content identifiers, not title or catalogue row position. Xtream IDs and meaningful M3U content locators can preserve progress across supported URL/catalogue changes. Ambiguous legacy entries without a recoverable parent remain available rather than being assigned to another same-title series. Missing/changed identities cannot safely be invented.

## Per-episode state

Thin green bars show partial progress, full green shows completion, and unwatched episodes have no line. Resume, Play From Start, watched/unwatched, individual clear and manual selection operate on that episode only. Completion requires a natural END near a known duration: at most two seconds tolerance, bounded to one percent for short clips. Stopping during credits remains resumable; an unknown/truncated ending is not guessed complete.

Snapshots are captured on the player looper and handed to the existing serialized writer. Pending snapshots are visible to details immediately. Periodic/pause/seek/stop/navigation saves avoid writing every frame. Explicit watched/unwatched/clear actions invalidate earlier playback ownership in RAM; stale checkpoints and END events cannot undo them. New intentional playback can save again. Ownership uses the source and stable item key, so an automatic signed-URL refresh does not revive an invalidated session.

Captured observation timestamps are retained. Within one current playback revision, strictly older observations are ignored; starting a fresh session resets that RAM ordering barrier rather than comparing against an older database timestamp. Playlist history clearing invalidates its playback ownership. Existing source/account guards and FIFO disk writes remain. Tokens/revisions are not persisted or exported.

## Autoplay

Autoplay Next Episode defaults ON only when no choice exists; explicit saved OFF survives restarts/upgrades. It is in ordinary Playback settings without an Admin PIN. Confirmed completion can start a cancellable next-episode countdown across the available numeric season/episode queue. OFF and the final available episode return to the existing caller. Manual Next/Previous remain available within finite same-series bounds. Backgrounding cancels the countdown; failure or early END does not enter a live-style reconnect/advance loop.

External players maintain their own progress. Provider metadata, stream availability and codec support still constrain playback. See [navigation](VOD_NAVIGATION.md), [current QA scope](QA_CHECKLIST.md) and the matching release sidecars for exact evidence.

## Historical evidence

The [preserved earlier document](history/VOD_PROGRESS-before-1.1.1.md) retains older implementation/QA records with their original dates and limits. It does not override the maintained 1.1.1/code52 guidance above.
