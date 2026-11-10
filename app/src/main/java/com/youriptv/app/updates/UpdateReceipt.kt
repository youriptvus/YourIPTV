package com.youriptv.app.updates

/**
 * A successful self-update is proven by the installed code reaching the durable install target.
 * Keep that target until acknowledgement: displaying or dismissing a dialog never consumes it.
 * The existing key is also written by older updaters, including 0.5.2.
 */
internal class UpdateReceipt(
    private val installedCode: Long,
    private val installedName: String,
    private val store: Store,
) {
    interface Store {
        var pendingCode: Long
    }

    data class Confirmation(
        val installedCode: Long,
        val installedName: String,
        val completedTargetCode: Long,
        val changes: List<String>,
        val credit: String? = null,
    )

    fun current(): Confirmation? {
        val pending = store.pendingCode
        if (pending <= 0 || pending > installedCode) return null
        return Confirmation(installedCode, installedName, pending,
            InstalledChangelog.changes(installedCode, installedName), InstalledChangelog.credit(installedCode, installedName))
    }

    fun acknowledge(confirmation: Confirmation) {
        // A stale dialog must never clear a newer installation attempt's durable target.
        if (confirmation.installedCode == installedCode && confirmation.installedName == installedName &&
            confirmation.completedTargetCode == store.pendingCode && confirmation.completedTargetCode in 1..installedCode) {
            store.pendingCode = 0L
        }
    }
}
