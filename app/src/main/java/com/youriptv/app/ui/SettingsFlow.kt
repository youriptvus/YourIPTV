package com.youriptv.app.ui

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.youriptv.app.data.Settings

/** Owns RAM grants only. No PIN, credentials, grant or ticket is written to disk or saved state. */
internal class SettingsFlow(app: Application) : Application.ActivityLifecycleCallbacks {
    private val authorization = SettingsFlowAuthorization()
    private val settings = Settings(app)
    private val screens = mutableSetOf<Activity>()
    private val policyListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> synchronizePolicy() }
    private var started = 0
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) authorization.revoke()
        }
    }

    init {
        synchronizePolicy()
        settings.addListener(policyListener)
        app.registerActivityLifecycleCallbacks(this)
        ContextCompat.registerReceiver(app, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun synchronizePolicy() {
        if (authorization.synchronizePolicy(settings.isSettingsLocked())) {
            // Do not let an already visible editor, picker result or dialog keep an old policy's access.
            screens.toList().forEach { screen ->
                when (screen) {
                    is SettingsActivity -> screen.onSettingsPolicyChanged()
                    is PlaylistsActivity -> screen.onSettingsPolicyChanged()
                    is PlaylistEditActivity -> screen.onSettingsPolicyChanged()
                }
            }
        }
    }

    fun policyToken(): Long { synchronizePolicy(); return authorization.policyToken() }
    fun isCurrentPolicy(token: Long): Boolean { synchronizePolicy(); return authorization.isCurrentPolicy(token) }
    fun authorizeWithoutPin(activity: Activity, route: SettingsFlowAuthorization.Route): Boolean {
        synchronizePolicy()
        return authorization.authorizeWithoutPin(activity, route)
    }

    fun enter(activity: Activity, route: SettingsFlowAuthorization.Route): Boolean {
        synchronizePolicy()
        val ticket = activity.intent.getStringExtra(EXTRA_HANDOFF)
        activity.intent.removeExtra(EXTRA_HANDOFF)
        return authorization.enter(activity, route, ticket).also { if (!it) authorization.revoke() }
    }

    fun authenticate(activity: Activity, route: SettingsFlowAuthorization.Route) { synchronizePolicy(); authorization.authenticate(activity, route) }
    fun token(activity: Activity): Long? { synchronizePolicy(); return authorization.token(activity) }
    fun isAuthorized(activity: Activity, token: Long? = token(activity)): Boolean { synchronizePolicy(); return authorization.isAuthorized(activity, token) }
    fun retains(activity: Activity) = authorization.retains(activity)
    fun hasParentalPermission(activity: Activity) = authorization.hasParentalPermission(activity)
    fun allowParental(activity: Activity) = authorization.allowParental(activity)
    fun paused(activity: Activity) = authorization.paused(activity)
    fun returning(activity: Activity) = authorization.returning(activity)

    fun launch(activity: Activity, route: SettingsFlowAuthorization.Route, intent: Intent): Boolean {
        synchronizePolicy()
        val ticket = authorization.forward(activity, route) ?: return false
        try {
            activity.startActivity(intent.putExtra(EXTRA_HANDOFF, ticket))
        } catch (e: RuntimeException) {
            authorization.cancelForward(activity)
            throw e
        }
        return true
    }

    override fun onActivityStarted(activity: Activity) { started++ }
    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) authorization.revoke()
    }
    override fun onActivityResumed(activity: Activity) {
        if (activity !is SettingsActivity && activity !is PlaylistsActivity && activity !is PlaylistEditActivity) authorization.revoke()
    }
    override fun onActivityDestroyed(activity: Activity) { screens.remove(activity); authorization.destroyed(activity) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is SettingsActivity || activity is PlaylistsActivity || activity is PlaylistEditActivity) screens.add(activity)
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object { const val EXTRA_HANDOFF = "settings_flow_handoff" }
}
