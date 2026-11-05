package com.youriptv.app.ui

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.PinCheck
import com.youriptv.app.data.PinKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    fun time(ms: Long): String {
        val pattern = if (YourIptvApp.instance.settings.use24h) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

    fun dayTime(ms: Long): String {
        val pattern = if (YourIptvApp.instance.settings.use24h) "EEE d MMM HH:mm" else "EEE d MMM h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

    fun clock(): String {
        val pattern = if (YourIptvApp.instance.settings.use24h) "EEE, MMM d  HH:mm" else "EEE, MMM d  h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

object Dialogs {
    fun toast(a: Activity, msg: String) = Toast.makeText(a, msg, Toast.LENGTH_LONG).show()

    fun choose(a: Activity, title: String, options: List<String>, checked: Int = -1, onPick: (Int) -> Unit) {
        val b = AlertDialog.Builder(a).setTitle(title).setNegativeButton("Cancel", null)
        if (checked in options.indices) {
            b.setSingleChoiceItems(options.toTypedArray(), checked) { d, i -> d.dismiss(); onPick(i) }
        } else {
            b.setItems(options.toTypedArray()) { _, i -> onPick(i) }
        }
        val d = b.show()
        if (checked in options.indices) d.listView?.let { lv ->
            lv.setSelection(checked)
            lv.post { lv.requestFocus(); lv.setSelection(checked) }
        }
    }

    /** Information dialog whose OK button has focus, so one remote OK press closes it. */
    fun message(a: Activity, title: String, message: String) {
        val d = AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    fun confirm(a: Activity, title: String, message: String, onYes: () -> Unit) {
        val d = AlertDialog.Builder(a).setTitle(title).setMessage(message)
            .setPositiveButton("OK") { _, _ -> onYes() }.setNegativeButton("Cancel", null).show()
        // On TV nothing is focused by default, so OK on the remote would do nothing; default to the safe choice.
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }

    fun input(
        a: Activity, title: String, initial: String, numeric: Boolean = false, password: Boolean = false,
        onCancel: (() -> Unit)? = null, onDone: (String) -> Unit,
    ) {
        val et = EditText(a).apply {
            setText(initial)
            inputType = when {
                numeric && password -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                else -> InputType.TYPE_CLASS_TEXT
            }
            setSingleLine()
        }
        var done = false
        AlertDialog.Builder(a).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> done = true; onDone(et.text.toString()) }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!done) onCancel?.invoke() }
            .show()
        et.requestFocus()
    }

    /** Runs [onOk] if no parental PIN is set, otherwise only after the correct parental PIN is entered. */
    fun requirePin(a: Activity, reason: String, onCancel: (() -> Unit)? = null, onOk: () -> Unit) =
        requireCode(a, PinKind.PARENTAL, "Enter parental PIN — $reason", onCancel, onOk)

    /** Like [requirePin], but for the separate Settings PIN. The parental PIN is never accepted here. */
    fun requireSettingsPin(a: Activity, reason: String, onCancel: (() -> Unit)? = null, onOk: () -> Unit) =
        requireCode(a, PinKind.SETTINGS, "Enter Settings PIN — $reason", onCancel, onOk)

    private fun requireCode(a: Activity, kind: PinKind, title: String, onCancel: (() -> Unit)?, onOk: () -> Unit) {
        val vault = YourIptvApp.instance.settings.pins
        if (!vault.has(kind)) { onOk(); return }
        val wait = vault.lockoutRemaining(kind)
        if (wait > 0) { toast(a, lockoutText(kind, wait)); onCancel?.invoke(); return }
        input(a, title, "", numeric = true, password = true, onCancel = onCancel) { pin ->
            if (pin.isEmpty()) { toast(a, "No PIN entered"); onCancel?.invoke(); return@input }
            when (val r = vault.check(kind, pin)) {
                PinCheck.Ok -> onOk()
                is PinCheck.Wrong -> { toast(a, "Incorrect ${kind.label} · ${r.attemptsLeft} attempt${if (r.attemptsLeft == 1) "" else "s"} left"); onCancel?.invoke() }
                is PinCheck.LockedOut -> { toast(a, lockoutText(kind, r.remainingMs)); onCancel?.invoke() }
            }
        }
    }

    private fun lockoutText(kind: PinKind, ms: Long) =
        "Too many incorrect attempts. The ${kind.label} can be tried again in ${(ms + 999) / 1000} s"

    /**
     * Creates or changes the [kind] PIN: asks for 4–8 digits twice and refuses a value equal to the other, independent PIN.
     * Callers must already have authorised the change. [onSaved] runs only after both entries match and the PIN is stored.
     */
    fun createPin(a: Activity, kind: PinKind, onCancel: (() -> Unit)? = null, onSaved: () -> Unit) {
        val vault = YourIptvApp.instance.settings.pins
        val action = if (vault.has(kind)) "New" else "Create"
        input(a, "$action ${kind.label} (4–8 digits)", "", numeric = true, password = true, onCancel = onCancel) { pin ->
            vault.screenNew(kind, pin)?.let { toast(a, it); onCancel?.invoke(); return@input }
            input(a, "Repeat the new ${kind.label}", "", numeric = true, password = true, onCancel = onCancel) { again ->
                if (again != pin) { toast(a, "PINs do not match. Nothing was changed."); onCancel?.invoke(); return@input }
                vault.set(kind, pin)
                toast(a, "${kind.label.replaceFirstChar { it.uppercase() }} saved")
                onSaved()
            }
        }
    }
}
