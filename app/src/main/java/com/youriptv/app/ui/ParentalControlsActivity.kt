package com.youriptv.app.ui

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.lifecycleScope
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.PinKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Independent parental visit. It never creates or inherits a Settings/Admin grant. */
class ParentalControlsActivity : ListActivity() {
    private class Entry(val title: String, val value: () -> String, val action: () -> Unit)
    private lateinit var adapter: TextAdapter<Entry>
    private val s get() = app.settings
    private val visit = SettingsVisitAuthorization()
    private var authenticating = false
    private var rowFocus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = TextAdapter(
            bind = { e -> e.title to e.value() },
            onClick = { e, _ -> if (authorizedToken() != null) e.action() },
        )
        list.adapter = adapter
        list.preserveFocusAfterLayout = false
        rowFocus = savedInstanceState?.getInt(STATE_ROW_FOCUS) ?: 0
        hideProtectedContent()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_ROW_FOCUS, list.focusedChild?.let { list.getChildAdapterPosition(it) }?.coerceAtLeast(0) ?: 0)
        super.onSaveInstanceState(outState)
    }

    private fun authorizedToken(): Long? = if (isFinishing || isDestroyed) null else visit.authorizedToken()
    private fun hasAuthorization(token: Long) = !isFinishing && !isDestroyed && visit.isAuthorized(token)

    private fun hideProtectedContent() {
        list.visibility = View.INVISIBLE
        listTitle.text = "Parental Controls"
        listSubtitle.text = "Enter the current parental PIN when one is configured."
    }

    override fun onResume() {
        super.onResume()
        if (authenticating || isFinishing) return
        val token = visit.beginVisit()
        authenticating = true
        Dialogs.requirePin(this, "open Parental Controls", onCancel = {
            if (visit.isCurrent(token)) { authenticating = false; finish() }
        }) {
            if (isFinishing || isDestroyed || !visit.authorize(token)) return@requirePin
            authenticating = false
            list.visibility = View.VISIBLE
            listSubtitle.text = "Parental controls use their own PIN. Protected changes still require the current parental PIN."
            adapter.submit(entries())
            list.doOnNextLayout { list.findViewHolderForAdapterPosition(rowFocus.coerceIn(0, adapter.itemCount - 1))?.itemView?.requestFocus() }
        }
    }

    override fun onPause() {
        rowFocus = list.focusedChild?.let { list.getChildAdapterPosition(it) }?.coerceAtLeast(0) ?: rowFocus
        hideProtectedContent()
        visit.endVisit()
        authenticating = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        visit.endVisit()
        super.onDestroy()
    }

    private fun refresh() {
        if (authorizedToken() != null) adapter.notifyItemRangeChanged(0, adapter.itemCount, "refresh")
    }

    private fun entries() = listOf(
        Entry("Parental PIN", { if (s.hasPin()) "Set · change or remove" else "Not set" }, { pinAction() }),
        lockToggle("Block adult categories", "hides adult groups in every playlist (live, movies, series); uses the parental PIN", { s.blockAdult }) { s.blockAdult = it },
        Entry("Locked groups", { if (s.lockedGroups.isEmpty()) "None" else "${s.lockedGroups.size} locked" }, { lockedGroups() }),
        Entry("Hide locked groups", { if (s.hideLockedGroups) "On" else "Off" }, entry@ {
            val token = authorizedToken() ?: return@entry
            val value = !s.hideLockedGroups
            Dialogs.requirePin(this, "change visibility") {
                if (hasAuthorization(token)) { s.hideLockedGroups = value; refresh() }
            }
        }),
        lockToggle("Lock playlists", "PIN needed to add, edit, switch, or delete playlists", { s.lockPlaylists }) { s.lockPlaylists = it },
    )

    private fun lockToggle(t: String, note: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        Entry(t, { (if (get() && s.hasPin()) "On" else "Off") + " · $note" }, entry@ {
            val token = authorizedToken() ?: return@entry
            val value = !(get() && s.hasPin())
            val apply = { if (hasAuthorization(token)) { set(value); refresh() } }
            if (!s.hasPin()) {
                if (!value) apply() else { Dialogs.toast(this, "Set a parental PIN first"); newPin { apply() } }
            } else Dialogs.requirePin(this, "change \"$t\"") { apply() }
        })

    private fun pinAction() {
        val token = authorizedToken() ?: return
        if (!s.hasPin()) { newPin(); return }
        Dialogs.choose(this, "Parental PIN", listOf("Change parental PIN", "Remove parental PIN")) { index ->
            if (!hasAuthorization(token)) return@choose
            Dialogs.requirePin(this, "confirm current parental PIN") {
                if (!hasAuthorization(token)) return@requirePin
                if (index == 0) newPin() else {
                    s.pins.set(PinKind.PARENTAL, null)
                    Dialogs.toast(this, "Parental PIN removed; locked groups, adult categories, and playlists are now unlocked")
                    refresh()
                }
            }
        }
    }

    private fun newPin(after: (() -> Unit)? = null) {
        val token = authorizedToken() ?: return
        Dialogs.createPin(this, PinKind.PARENTAL, canSave = { hasAuthorization(token) }) {
            if (hasAuthorization(token)) { refresh(); after?.invoke() }
        }
    }

    private fun lockedGroups() {
        val token = authorizedToken() ?: return
        val playlist = app.repo.playlist(s.activePlaylistId) ?: run { Dialogs.toast(this, "No active playlist"); return }
        if (!s.hasPin()) { Dialogs.toast(this, "Set a parental PIN first"); newPin { lockedGroups() }; return }
        Dialogs.requirePin(this, "edit locked groups") {
            if (!hasAuthorization(token)) return@requirePin
            lifecycleScope.launch {
                val groups = withContext(Dispatchers.IO) {
                    listOf(ItemKind.LIVE, ItemKind.MOVIE, ItemKind.SERIES, ItemKind.EPISODE)
                        .flatMap { app.repo.groups(playlist.id, it) }.map { it.name }.distinct()
                }
                if (!hasAuthorization(token)) return@launch
                if (groups.isEmpty()) { Dialogs.toast(this@ParentalControlsActivity, "The active playlist has no groups yet"); return@launch }
                val checked = BooleanArray(groups.size) { groups[it] in s.lockedGroups }
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this@ParentalControlsActivity).setTitle("Locked groups")
                    .setMultiChoiceItems(groups.toTypedArray(), checked) { _, index, value -> if (hasAuthorization(token)) checked[index] = value }
                    .setPositiveButton("Save") { _, _ ->
                        if (hasAuthorization(token)) {
                            val other = s.lockedGroups - groups.toSet()
                            s.lockedGroups = other + groups.filterIndexed { index, _ -> checked[index] }
                            refresh()
                        }
                    }.setNegativeButton("Cancel", null).show()
                RemoteBack.bind(dialog, this@ParentalControlsActivity)
            }
        }
    }

    private companion object { const val STATE_ROW_FOCUS = "parental_row_focus" }
}
