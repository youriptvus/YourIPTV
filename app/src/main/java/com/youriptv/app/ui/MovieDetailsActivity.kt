package com.youriptv.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.youriptv.app.R
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.MovieDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Optional catalogue information is independent of the playable URL already saved in the playlist. */
class MovieDetailsActivity : RemoteActivity() {
    private val app get() = YourIptvApp.instance
    private lateinit var movie: MediaItem
    private lateinit var content: View
    private lateinit var play: Button
    private lateinit var beginning: Button
    private lateinit var favorite: Button
    private var authorizedStamp = -1
    private var authorizedGroup: String? = null
    private var pinPending = false
    private var foreground = false
    private var loading = false
    private var loaded = false
    private var hasFocused = false
    private var details = MovieDetails()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!app.isTrustedBuild) { finish(); return }
        setContentView(R.layout.activity_movie_details)
        content = findViewById(R.id.movie_content)
        content.visibility = View.INVISIBLE
        hasFocused = savedInstanceState != null
        movie = MediaItem(
            playlistId = intent.getLongExtra(EXTRA_PLAYLIST, 0), kind = ItemKind.MOVIE,
            url = intent.getStringExtra(EXTRA_URL).orEmpty(),
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            group = intent.getStringExtra(EXTRA_GROUP).orEmpty(),
            logo = intent.getStringExtra(EXTRA_LOGO).orEmpty(),
            remoteId = intent.getStringExtra(EXTRA_REMOTE_ID).orEmpty(),
        )
        // A recreated Activity must not turn a stale Intent into a new parental grant.
        if (savedInstanceState == null && intent.getIntExtra(EXTRA_AUTHORIZATION_STAMP, -1) == app.settings.restrictionStamp()) {
            authorizedStamp = app.settings.restrictionStamp()
            authorizedGroup = movie.group.takeIf { app.settings.isGroupLocked(it) }
        }
        play = findViewById<Button>(R.id.movie_play).apply { setOnClickListener { playMovie(false) } }
        beginning = findViewById<Button>(R.id.movie_beginning).apply { setOnClickListener { playMovie(true) } }
        favorite = findViewById<Button>(R.id.movie_favorite).apply { setOnClickListener {
            withAccess {
                app.repo.setFavorite(movie, !app.repo.isFavorite(movie))
                updateActions()
            }
        } }
        findViewById<Button>(R.id.movie_external).setOnClickListener {
            withAccess {
                if (Scheduler.recordingUsesOnlyConnection(movie.playlistId)) {
                    Dialogs.toast(this, "This provider's only connection is being used by a recording.")
                } else Nav.externalPlayer(this, movie.url, movie.name)
            }
        }
        findViewById<Button>(R.id.movie_retry).setOnClickListener { withAccess { loadDetails() } }
        val poster = findViewById<ImageView>(R.id.movie_cover)
        val widthDp = resources.configuration.screenWidthDp
        val coverDp = (widthDp / 4).coerceIn(88, 160)
        poster.layoutParams = poster.layoutParams.apply {
            width = (coverDp * resources.displayMetrics.density).toInt()
            height = (coverDp * 1.5f * resources.displayMetrics.density).toInt()
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (!app.isTrustedBuild || !::movie.isInitialized) return
        withAccess {
            bindDetails()
            updateActions()
            if (!loaded && !loading) loadDetails()
            if (!hasFocused) { play.requestFocus(); hasFocused = true }
        }
    }

    override fun onPause() {
        foreground = false
        if (::content.isInitialized) content.visibility = View.INVISIBLE
        super.onPause()
    }

    private fun withAccess(action: () -> Unit) {
        if (!foreground || isFinishing || isDestroyed) return
        if (!app.isTrustedBuild || movie.url.isBlank() || app.repo.playlist(movie.playlistId) == null) { finish(); return }
        // A refreshed catalogue may have moved this title to a restricted group while details were open.
        app.repo.itemByUrl(movie.playlistId, movie.url)?.let { latest ->
            if (latest.kind != ItemKind.MOVIE) { finish(); return }
            movie = latest
        }
        if (app.settings.isGroupHidden(movie.group)) { finish(); return }
        val stamp = app.settings.restrictionStamp()
        if (app.settings.isGroupLocked(movie.group) && (authorizedStamp != stamp || authorizedGroup != movie.group)) {
            content.visibility = View.INVISIBLE
            if (pinPending) return
            pinPending = true
            val group = movie.group
            Dialogs.requirePin(this, "\"$group\" is locked", onCancel = {
                pinPending = false
                finish()
            }) {
                pinPending = false
                if (app.settings.restrictionStamp() != stamp || app.settings.isGroupHidden(group)) {
                    finish()
                } else {
                    authorizedStamp = stamp
                    authorizedGroup = group
                    withAccess(action)
                }
            }
        } else {
            authorizedStamp = stamp
            content.visibility = View.VISIBLE
            action()
        }
    }

    private fun bindDetails() {
        findViewById<TextView>(R.id.movie_title).text = movie.name
        findViewById<TextView>(R.id.movie_metadata).text = listOf(movie.group, details.year, details.genre,
            details.duration.takeIf { it.isNotBlank() }?.let { "Duration $it" }.orEmpty(),
            details.rating.takeIf { it.isNotBlank() }?.let { "Rating $it" }.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString(" · ")
        findViewById<TextView>(R.id.movie_plot).text = details.plot.ifBlank { "No description supplied by this provider." }
        ImageLoader.load(findViewById(R.id.movie_cover), details.cover.ifBlank { movie.logo }, R.drawable.movie_placeholder, 600)
        ImageLoader.load(findViewById(R.id.movie_backdrop), details.backdrop, android.R.color.transparent, 1280)
    }

    private fun updateActions() {
        val resume = app.repo.resumePosition(movie)
        play.text = if (resume > 0) "Resume ${Format.duration(resume)}" else "Play"
        beginning.visibility = if (resume > 0) View.VISIBLE else View.GONE
        favorite.text = if (app.repo.isFavorite(movie)) "Remove from favorites" else "Add to favorites"
    }

    private fun loadDetails() {
        if (loading) return
        val playlist = app.repo.playlist(movie.playlistId) ?: return
        val requested = movie
        val stamp = authorizedStamp
        loading = true
        findViewById<View>(R.id.movie_progress).visibility = View.VISIBLE
        findViewById<View>(R.id.movie_retry).visibility = View.GONE
        findViewById<TextView>(R.id.movie_status).text = "Loading provider details…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { app.repo.movieDetails(playlist, requested) } }
            loading = false
            findViewById<View>(R.id.movie_progress).visibility = View.GONE
            // Do not display a late response across a parental restriction change.
            if (!foreground || app.settings.restrictionStamp() != stamp || app.settings.isGroupHidden(movie.group)) return@launch
            withAccess {
                loaded = true
                result.onSuccess { details = it; bindDetails() }
                // Network/parse exceptions may carry provider URLs, so only a fixed message reaches this screen.
                findViewById<TextView>(R.id.movie_status).text = if (result.isFailure)
                    "Provider details are unavailable. You can still play this movie or retry." else ""
                findViewById<View>(R.id.movie_retry).visibility = if (result.isFailure) View.VISIBLE else View.GONE
            }
        }
    }

    private fun playMovie(fromBeginning: Boolean) = withAccess {
        if (!fromBeginning) {
            Nav.play(this, listOf(movie), 0, movie.group.takeIf { app.settings.isGroupLocked(it) })
        } else lifecycleScope.launch {
            val requested = movie
            withContext(Dispatchers.IO) { app.repo.recordHistory(requested, 0, 0) }
            withAccess {
                Nav.play(this@MovieDetailsActivity, listOf(movie), 0, movie.group.takeIf { app.settings.isGroupLocked(it) })
            }
        }
    }

    companion object {
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_REMOTE_ID = "remote_id"
        const val EXTRA_GROUP = "group"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_AUTHORIZATION_STAMP = "authorization_stamp"
    }
}
