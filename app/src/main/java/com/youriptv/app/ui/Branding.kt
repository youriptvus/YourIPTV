package com.youriptv.app.ui

import android.content.Context
import com.youriptv.app.R

/**
 * Centralized branding contract for YourIPTV.
 *
 * This object is the single, stable integration point a separate, authorized rebranding tool targets.
 * All customizable brand surfaces (name, icons, TV banner, About company block) are routed through
 * here or through [res/values/branding.xml], so the tool never has to touch player code.
 *
 * Design rules:
 *  - Branding is READ-ONLY at runtime and is kept separate from player behavior, user settings,
 *    playlists, provider credentials, and security controls. Nothing here reads or writes those.
 *  - The default values are YourIPTV's own branding, so an un-customized build is fully branded.
 *  - Customizing name/icons/banner requires rebuilding and re-signing the APK (resources are compiled
 *    into the APK and protected by the signing-certificate check). See docs/BRANDING.md.
 *  - Mandatory legal text (the "free for everyone" notice and the open-source license
 *    acknowledgements) is appended by [aboutBody] and must survive any rebranding.
 */
object Branding {

    /** Brand display name. Also the value the integrity check pins to for the official build. */
    fun displayName(context: Context): String = context.getString(R.string.app_name)

    /** Phone launcher icon resource. A rebranding tool replaces the mipmap files, keeping the name. */
    const val LAUNCHER_ICON = R.mipmap.ic_launcher
    const val LAUNCHER_ICON_ROUND = R.mipmap.ic_launcher_round

    /** Android TV / Leanback banner (320x180). Replace the drawable file, keep the name. */
    const val TV_BANNER = R.drawable.banner

    private fun Context.optional(resId: Int): String = getString(resId).trim()

    /** The mandatory free-for-everyone notice, worded with the current brand name. */
    fun freeNotice(context: Context): String = context.getString(R.string.free_notice, displayName(context))

    /**
     * Rebrandable company/contact block for the About screen. The default build shows YourIPTV's
     * website; a company name is optional.
     */
    fun companyBlock(context: Context): String {
        val company = context.optional(R.string.brand_company_name)
        val tagline = context.optional(R.string.brand_about_tagline)
        val supportEmail = context.optional(R.string.brand_support_email)
        val supportPhone = context.optional(R.string.brand_support_phone)
        val supportUrl = context.optional(R.string.brand_support_url)
        if (listOf(company, tagline, supportEmail, supportPhone, supportUrl).all(String::isBlank)) return ""
        val lines = buildList {
            if (company.isNotBlank()) {
                add("COMPANY")
                add("• $company")
            }
            tagline.takeIf { it.isNotBlank() }?.let { add("• $it") }
            supportEmail.takeIf { it.isNotBlank() }?.let { add("• Support: $it") }
            supportPhone.takeIf { it.isNotBlank() }?.let { add("• Phone: $it") }
            supportUrl.takeIf { it.isNotBlank() }?.let { add("• Web: $it") }
        }
        return lines.joinToString("\n")
    }

    /**
     * The full About body: the free notice, the standard player notices, an optional company block,
     * and the always-present open-source acknowledgements. The company block is the only rebrandable
     * section; everything else is fixed so required notices cannot be dropped during rebranding.
     */
    fun aboutBody(context: Context): String {
        val free = freeNotice(context)
        val name = displayName(context)
        val company = companyBlock(context)
        val companySection = if (company.isBlank()) "" else "\n$company\n"
        return """$free

FREE FOR EVERYONE
• Every feature in $name is available to every user.
• No $name account, registration, purchase, activation code, licence key, trial, or subscription exists or is required.
• There are no paid feature locks or artificial limits on playlists, favorites, or devices. Settings PIN protection is a configuration safeguard.

WHAT STILL APPLIES (NOT APP LICENSING)
• IPTV provider: $name contains no channels. You need a playlist URL or Xtream Codes login from your own provider, and the provider's account status, expiry date, and maximum simultaneous connections apply. If a provider allows one connection, another app or device using the account can block playback here.
• Device: available codecs, resolution, frame rate, HDR, and picture-in-picture depend on your device and Android version.
• Android: some features (such as picture-in-picture) require Android 8.0 or newer.
• Parental PIN: if you set a PIN, locked groups require it. "Block adult categories" hides adult groups in every playlist, and playlist management can be locked too. Parental and custom Settings PINs are stored as salted hashes on this device.
• Settings: protection is always on. A permanent shared master or an optional custom Settings PIN opens Settings; only the master can manage the custom PIN. The master is built into the app and is not a private owner credential or a parental override. About is available without a PIN from the main menu.

PRIVACY
• Playlists, credentials, favorites, history, and guide data are stored only on this device and sent only to the provider servers you configure.
• $name has no analytics, advertising, or accounts.
$companySection
OPEN-SOURCE LIBRARIES (Apache License 2.0)
• AndroidX (AppCompat, Core, RecyclerView, Lifecycle)
• AndroidX Media3 / ExoPlayer
• Kotlin and kotlinx.coroutines
"""
    }
}
