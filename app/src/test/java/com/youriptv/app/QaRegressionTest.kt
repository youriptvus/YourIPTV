package com.youriptv.app

import com.youriptv.app.data.Recorder
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Source-level guards for defects found during the full QA pass (UI behaviour is verified on the device). */
class QaRegressionTest {
    private fun ui(name: String) = listOf(
        File("src/main/java/com/youriptv/app/ui/$name"),
        File("app/src/main/java/com/youriptv/app/ui/$name"),
    ).first { it.exists() }.readText()

    @Test
    fun choosersMarkTheCurrentValue() {
        val util = ui("UiUtil.kt")
        assertTrue(util.contains("checked: Int = -1"))
        assertTrue(util.contains("setSingleChoiceItems"))
        assertTrue(ui("PlayerActivity.kt").contains("ASPECTS, checked = app.settings.aspectMode"))
        assertTrue(ui("SettingsActivity.kt").contains("Dialogs.choose(this, t, labels, checked = index())"))
    }

    @Test
    fun returningFromPlayerRevealsLastChannelWithoutBypassingLocks() {
        val main = ui("MainActivity.kt")
        assertTrue(main.contains("revealChannel(active, lastUrl)"))
        val reveal = main.substringAfter("private fun revealChannel").substringBefore("private fun focusItemAt")
        assertTrue("must skip hidden groups", reveal.contains("isGroupHidden(item.group)"))
        assertTrue("must skip locked groups", reveal.contains("isGroupLocked(item.group)"))
        assertFalse("must not prompt for or bypass a PIN", reveal.contains("requirePin"))
    }

    @Test
    fun onlyOnePlayerRunsAtATimeEvenAfterPictureInPicture() {
        val player = ui("PlayerActivity.kt")
        assertTrue(player.contains("active?.get()?.takeIf { it !== this }?.retire()"))
        val retire = player.substringAfter("private fun retire()").substringBefore("\n    }")
        assertTrue("the old view must detach and an independent player must be released", retire.contains("disposePlayer()"))
        val dispose = player.substringAfter("private fun disposePlayer()").substringBefore("\n    }")
        assertTrue(dispose.contains("app.livePlayback.detach(playerView)"))
        assertTrue(dispose.contains("p.release()"))
        assertTrue(retire.contains("finish()"))
    }

    @Test
    fun previewAndFullscreenRecoverAnEndedLiveStream() {
        val session = ui("LivePlaybackSession.kt")
        assertTrue(session.contains("Player.STATE_ENDED -> { loadingHint.reset(); retryPreview(\"Preview ended\")"))
        assertTrue(session.contains("retryAutomatically(p, item, 1500L * retries)"))
        assertTrue("preview retries must reopen the media source", session.contains("PlaybackFactory.restartLive(p, item)"))
        val player = ui("PlayerActivity.kt")
        val start = player.substringAfter("override fun onStart()").substringBefore("private fun adoptLiveSession()")
        assertTrue(start.contains("p.playbackState == Player.STATE_ENDED || p.playbackState == Player.STATE_IDLE"))
        assertTrue(start.contains("retryAutomatically(p, item, 0L)"))
        assertTrue("entering fullscreen must not clear exhausted recovery", start.contains("if (app.livePlayback.recoveryFailed) recoveryObserver()"))
        val ended = player.substringAfter("private fun onEnded()").substringBefore("private fun savePosition()")
        assertTrue(ended.contains("retries < 3"))
        assertTrue(ended.contains("Live stream ended. Reconnecting"))
        assertTrue(ended.contains("The live stream ended and could not be restarted."))
        assertTrue(ended.contains("OK: retry"))
        assertTrue("ended live inputs must use the shared recovery budget", ended.contains("retryAutomatically(p, item, 1500L)"))
        val retry = player.substringAfter("private fun retry()").substringBefore("// ---------------- Overlays")
        assertTrue(retry.contains("if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()"))
    }

    @Test
    fun previewSlowLoadingHintFollowsThePlayerLifecycle() {
        val session = ui("LivePlaybackSession.kt")
        assertTrue(session.contains("Player.STATE_BUFFERING -> loadingPreviewStatus()"))
        assertTrue(session.contains("Player.STATE_READY -> { loadingHint.reset()"))
        val retry = session.substringAfter("private fun retryPreview(").substringBefore("private val restrictionListener")
        assertTrue(retry.contains("loadingHint.reset()"))
        val attach = session.substringAfter("private fun attach(").substringBefore("fun detach(")
        assertTrue(attach.contains("if (isFullscreen)"))
        assertTrue(attach.contains("loadingHint.reset()"))
        val tune = session.substringAfter("private fun tune(").substringBefore("private fun liveQueue(")
        assertTrue(tune.contains("loadingHint.reset()"))
        val stop = session.substringAfter("fun stop()").substringBefore("private fun notifyObservers()")
        assertTrue(stop.contains("loadingHint.reset()"))
    }

    @Test
    fun externalPlayerReportsMissingPlayerInsteadOfEmptyChooser() {
        val nav = ui("Nav.kt").substringAfter("fun externalPlayer").substringBefore("fun describeError")
        assertTrue(nav.contains("frameworkpackagestubs"))
        assertTrue(nav.contains("EXTRA_EXCLUDE_COMPONENTS"))
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }.readText()
        assertTrue("package visibility must allow the lookup", manifest.contains("<queries>") && manifest.contains("video/*"))
    }

    @Test
    fun emptiedListKeepsFocusOnItsSection() {
        val load = ui("MainActivity.kt").substringAfter("private fun loadItems").substringBefore("private fun refreshRecordingsInPlace")
        assertTrue(load.contains("val hadListFocus = currentFocus?.let { isInside(it, itemsView) } == true"))
        assertTrue(load.contains("navViews[section]?.requestFocus()"))
    }

    @Test
    fun recordingRefreshHandlesListFocusAndSectionChanges() {
        val refresh = ui("MainActivity.kt").substringAfter("private fun refreshRecordingsInPlace()").substringBefore("private fun emptyText()")
        assertTrue(refresh.contains("itemsView.findContainingViewHolder(it)?.bindingAdapterPosition"))
        assertFalse(refresh.contains("getChildAdapterPosition("))
        assertTrue(refresh.contains("if (section != Section.RECORDINGS) return"))
        assertTrue(refresh.contains("if (section != Section.RECORDINGS) return@launch"))
        assertTrue(refresh.contains("val keepListFocus = focus?.let { isInside(it, itemsView) } == true"))
        assertTrue(refresh.contains("if (keepListFocus)"))
        assertTrue(refresh.contains("itemsView.doOnNextLayout"))
        assertTrue(refresh.contains("itemAdapter.items === rows"))
        assertTrue(refresh.contains("userInputGeneration == inputGeneration"))
        assertTrue(refresh.contains("window.decorView.hasWindowFocus()"))
        assertTrue(refresh.contains("itemsView.requestLayout()"))
    }

    @Test
    fun recordingListRefreshFollowsWorkerCompletion() {
        val main = ui("MainActivity.kt")
        val options = main.substringAfter("private fun recordingOptions(").substringBefore("// ---------------- Startup behaviour")
        val completion = ui("Scheduler.kt").substringAfter("Recorder.start(").substringBefore("fun recordingUsesOnlyConnection")
        assertTrue(options.contains("Recorder.stop(r.id)"))
        assertFalse(options.contains("postDelayed({ reload() }"))
        assertTrue(completion.contains("if (a is MainActivity) a.onRecordingFinished()"))
        assertTrue(main.contains("internal fun onRecordingFinished()"))
    }

    @Test
    fun recordingReadHandlesOnlyStopRelatedCloseInterruption() {
        val buffer = ByteArray(1)
        assertEquals(1, Recorder.readRecordingInput(ByteArrayInputStream(byteArrayOf(42)), buffer) { false })
        assertEquals(42, buffer[0].toInt())

        var stopped = false
        val closedDuringRead = object : InputStream() {
            override fun read(): Int {
                stopped = true
                throw NullPointerException("closed during read")
            }
        }
        assertEquals(-1, Recorder.readRecordingInput(closedDuringRead, buffer) { stopped })

        val unexpectedNull = object : InputStream() {
            override fun read(): Int = throw NullPointerException("unexpected")
        }
        assertTrue(runCatching { Recorder.readRecordingInput(unexpectedNull, buffer) { false } }.exceptionOrNull() is NullPointerException)
        val ioFailure = object : InputStream() {
            override fun read(): Int = throw IOException("disk failure")
        }
        assertTrue(runCatching { Recorder.readRecordingInput(ioFailure, buffer) { true } }.exceptionOrNull() is IOException)
    }

    @Test
    fun creatingRecordingsRequiresTheParentalPinForLockedGroups() {
        val scheduler = ui("Scheduler.kt")
        val programme = scheduler.substringAfter("fun toggleProgramRecording(").substringBefore("fun recordNow(")
        val now = scheduler.substringAfter("fun recordNow(").substringBefore("private fun requireRecordingPin(")
        val gate = scheduler.substringAfter("private fun requireRecordingPin(").substringBefore("private fun confirmConnection(")
        assertTrue(programme.contains("requireRecordingPin(a, ch)"))
        assertTrue(now.contains("requireRecordingPin(a, ch)"))
        assertTrue(gate.contains("isGroupLocked(ch.group)"))
        assertTrue(gate.contains("Dialogs.requirePin(a"))
        assertFalse(gate.contains("requireSettingsPin"))
    }

    @Test
    fun cancelingRestoredLockedGroupLoadsASafeFallback() {
        val main = ui("MainActivity.kt")
        val groups = main.substringAfter("private fun loadGroups(").substringBefore("private var lastSection")
        val select = main.substringAfter("private fun selectGroup(").substringBefore("private fun loadItems(")
        assertTrue(groups.contains("if (section != targetSection || playlist?.id != p.id) return@launch"))
        assertTrue(groups.contains("onCancel = {"))
        assertTrue(groups.contains("it.special == SPECIAL_ALL"))
        assertTrue(groups.contains("it.special == SPECIAL_FAV"))
        assertTrue(groups.contains("selectGroup(fallback, focusItems = false)"))
        assertTrue(select.contains("onCancel = onCancel"))
    }

    @Test
    fun guideFromItemOptionsFocusesThatChannel() {
        val main = ui("MainActivity.kt")
        assertTrue(main.contains("opts.add(\"Show in TV guide\" to { openGuide(item.group, item.url) })"))
        val open = main.substringAfter("private fun openGuide(").substringBefore("private fun confirmExit")
        assertTrue(open.contains("selectGroup(pos, focusItems = true, focusUrl = url)"))
        assertTrue(open.contains("pendingGuide = group to url"))
    }

    @Test
    fun guideIsMergedIntoLiveTv() {
        val main = ui("MainActivity.kt")
        assertFalse("the rail must not list a separate guide", main.contains("GUIDE(\"TV guide\")"))
        assertFalse(main.contains("GuideActivity"))
        assertFalse(ui("PlayerActivity.kt").contains("GuideActivity"))
        assertTrue(listOf("src", "app/src").none { java.io.File("$it/main/java/com/youriptv/app/ui/GuideActivity.kt").exists() })
        val back = main.substringAfter("handleOnBackPressed()").substringBefore("})")
        assertTrue(back.contains("if (!liveGrid.isAtNow()) liveGrid.jumpToNow() else showCategories()"))
        assertTrue(back.contains("LiveLevel.CATEGORIES -> showMenu()"))
        // Browsing categories only re-filters; it never tunes a channel or bypasses a locked category.
        val browse = main.substringAfter("private fun showFocusedGroup()").substringBefore("private fun openGroupGuide")
        assertTrue(browse.contains("showLockedGroup(pos, entry)"))
        assertTrue(browse.contains("focusItems = false"))
        assertFalse(browse.contains("livePlayback.select"))
        // Browsing or opening a category starts at its first channel in playlist order, not at the playing channel.
        val open = main.substringAfter("private fun openGroupGuide").substringBefore("private fun guideGroupPos")
        assertFalse(browse.contains("livePlayback.current"))
        assertFalse(open.contains("livePlayback.current"))
        assertTrue(open.contains("focusItemAt(0)"))
        val grid = ui("LiveGuideGrid.kt")
        assertTrue(grid.contains("if (isAtNow()) exitLeft() else moveTime(false)"))
    }

    @Test
    fun liveBackUsesNavigationLevelInsteadOfTouchFocus() {
        val main = ui("MainActivity.kt")
        val back = main.substringAfter("handleOnBackPressed()").substringBefore("})")
        assertTrue(back.contains("section == Section.TV && playlist != null -> when (liveLevel)"))
        assertTrue(back.contains("LiveLevel.GUIDE -> if (!liveGrid.isAtNow()) liveGrid.jumpToNow() else showCategories()"))
        assertTrue(back.contains("LiveLevel.CATEGORIES -> showMenu()"))
        assertTrue(back.contains("LiveLevel.MENU -> confirmExit()"))
        val levels = main.substringAfter("private fun setLiveLevel(").substringBefore("private fun sizeContentColumn")
        assertTrue("portrait must track the logical level without adopting the landscape layout", levels.indexOf("liveLevel = target") < levels.indexOf("if (portrait) return"))
        val groups = main.substringAfter("private fun focusGroups()").substringBefore("\n    }")
        assertTrue(groups.contains("if (section == Section.TV) setLiveLevel(LiveLevel.CATEGORIES)"))
        assertFalse("touch clicks must not require an extra focus-only tap", main.contains("isFocusableInTouchMode = true"))
    }

    @Test
    fun liveGuideUsesCompactTiviMateProportions() {
        val grid = ui("LiveGuideGrid.kt")
        assertTrue("about eight 40dp rows fit under the preview at 1080p", grid.contains("TypedValue.COMPLEX_UNIT_DIP, 40f"))
        assertTrue(grid.contains("if (activity.resources.getBoolean(R.bool.is_portrait)) 180f else 260f"))
        val views = ui("GuideViews.kt")
        assertTrue(views.contains("val span = 120 * 60_000L"))
        assertTrue("guide rows draw channel logos from the shared cache", views.contains("ImageLoader.cached(url"))
        assertFalse("row text is 14sp like TiviMate, not the old 16sp", views.contains("sp(this@GuideRowView, 16f)"))
        val levels = ui("MainActivity.kt").substringAfter("private fun setLiveLevel(").substringBefore("private fun showCategories()")
        assertTrue("categories and guide hide the page header", levels.contains("if (target == LiveLevel.MENU) View.VISIBLE else View.GONE"))
        assertTrue("its heading moves beside the preview", levels.contains("liveLevel != LiveLevel.MENU && heading.text.isNotBlank()"))
        assertTrue("categories and guide keep the full-screen guide size and slide it", levels.contains("val fixed = section == Section.TV && liveLevel != LiveLevel.MENU"))
        val layout = java.io.File(listOf("src", "app/src").first { java.io.File(it).exists() } + "/main/res/layout/activity_main.xml").readText()
        assertTrue("the preview keeps TiviMate's 320dp size at every level", layout.contains("layout=\"@layout/live_preview\" android:layout_width=\"320dp\""))
    }

    @Test
    fun searchHintReturnsAndEnterFocusesLoadedResults() {
        val schedule = ui("MainActivity.kt").substringAfter("private fun scheduleSearch").substringBefore("val (res, favs)")
        assertTrue(schedule.contains("showState(SEARCH_HINT, progress = false)"))
        val main = ui("MainActivity.kt")
        assertTrue("Enter must focus results only after they load", main.contains("if (focusResults) focusItemAt(0)"))
        assertTrue(main.contains("scheduleSearch(searchBox.text.toString(), 0, focusResults = true)"))
        val section = main.substringAfter("Section.SEARCH -> {").substringBefore("else -> Unit")
        assertTrue("re-entering Search must rerun the kept query", section.contains("if (query.trim().length >= 2) scheduleSearch(query, 0) else showState(SEARCH_HINT"))
    }

    @Test
    fun episodesCanBeChangedFromTheControllerAndRemote() {
        val player = ui("PlayerActivity.kt")
        assertTrue(player.contains("playerView.player = EpisodeAwarePlayer(p)"))
        val step = player.substringAfter("private fun stepEpisode").substringBefore("\n    }")
        assertTrue("episode changes must go through switchTo so parental checks in startItem apply", step.contains("switchTo(index + delta)"))
        assertTrue(player.contains("KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> 1"))
        assertTrue(player.contains("private fun canStepEpisode(delta: Int) = current?.kind == ItemKind.EPISODE"))
        val options = player.substringAfter("private fun showOptions()").substringBefore("Dialogs.choose(")
        assertTrue("the onn remote has no CH/media keys, so the menu must offer episode navigation", options.contains("\"Next episode: "))
        assertTrue(options.contains("\"Previous episode: "))
    }

    @Test
    fun invertZapSummaryDescribesTheCurrentDirection() {
        val s = ui("SettingsActivity.kt")
        assertTrue(s.contains("\"Off · in the player, UP selects the previous channel number\""))
        assertTrue(s.contains("\"On · in the player, UP selects the next channel number\""))
        assertFalse(s.contains("{ s.invertZap = it }, \"UP selects the next channel number\")"))
    }

    @Test
    fun launcherRelaunchAfterHomeRestartsTheLiveChannel() {
        val main = ui("MainActivity.kt")
        val onNew = main.substringAfter("override fun onNewIntent(intent: Intent) {").substringBefore("\n    }")
        assertTrue(onNew.contains("intent.action == Intent.ACTION_MAIN"))
        assertTrue(onNew.contains("PlayerActivity.takeLeftWhileWatchingLive(this)"))
        assertTrue("must still honour the setting", onNew.contains("app.settings.resumeLastChannel) pendingResume = true"))
        val player = ui("PlayerActivity.kt")
        val onStop = player.substringAfter("override fun onStop() {").substringBefore("\n    }")
        assertTrue("BACK (finishing) must not count as leaving while watching",
            onStop.contains("setLeftWhileWatchingLive(this, !isFinishing && !isChangingConfigurations && !inPip && isLive)"))
        val onStart = player.substringAfter("override fun onStart() {").substringBefore("\n    }")
        assertTrue(onStart.contains("setLeftWhileWatchingLive(this, false)"))
        // The resume path keeps the parental check, so a locked last channel never restarts on its own.
        val resume = main.substringAfter("private fun maybeResumeLastChannel").substringBefore("private fun maybeAutoRefresh")
        assertTrue(resume.contains("if (app.settings.isGroupLocked(item.group)) return@withContext null"))
    }

    @Test
    fun verticalMovesAtListEndsDoNotJumpIntoTheNavRail() {
        val main = ui("MainActivity.kt")
        val block = main.substringAfter("UP/DOWN at either end of the groups or items list").substringBefore("return super.dispatchKeyEvent(event)")
        assertTrue(block.contains("isInside(f, groupsView) || isInside(f, itemsView)"))
        assertTrue(block.contains("navViews.values.any { it === next }) return true"))
    }

    @Test
    fun externalPlayerCannotBypassParentalLocks() {
        val main = ui("MainActivity.kt")
        val ext = main.substringAfter("opts.add(\"Open in external player\" to {").substringBefore("opts.add((if (item.group in")
        assertTrue(ext.contains("if (app.settings.isGroupLocked(item.group)) Dialogs.requirePin("))
        assertFalse("the unguarded one-liner must not come back",
            main.contains("opts.add(\"Open in external player\" to { Nav.externalPlayer(this, item.url, item.name) })"))
        val series = ui("SeriesActivity.kt")
        assertTrue(series.contains("else externalEpisode(row, index)"))
        val fn = series.substringAfter("private fun externalEpisode").substringBefore("companion object")
        assertTrue(fn.contains("app.settings.restrictionStamp() != openedStamp"))
        assertTrue(fn.contains("authenticateEntry") && fn.contains("withSeriesPermission"))
        val entry = series.substringAfter("private fun authenticateEntry").substringBefore("private fun onAuthorized")
        assertTrue(entry.contains("Dialogs.requirePin("))
        assertTrue(entry.contains("settings.isGroupHidden(group)"))
    }

    @Test
    fun failedNewPlaylistDoesNotReplaceTheWorkingActivePlaylist() {
        val save = ui("PlaylistEditActivity.kt").substringAfter("private fun save()").substringBefore("companion object")
        assertTrue(save.contains("val keepPrevious = previousActive != id && app.repo.playlist(previousActive) != null"))
        assertTrue(save.contains("if (!keepPrevious) app.settings.activePlaylistId = id"))
        val success = save.substringAfter("result.onSuccess").substringBefore("onFailure")
        assertTrue("switch only after a successful load", success.contains("app.settings.activePlaylistId = id"))
        val failure = save.substringAfter("onFailure")
        assertFalse("a failed load must not switch the active playlist", failure.contains("activePlaylistId = "))
        val beforeLoad = save.substringBefore("lifecycleScope.launch")
        assertEquals("only the unchanged-source and no-previous paths may switch before loading",
            2, Regex("activePlaylistId = id").findAll(beforeLoad).count())
    }
}
