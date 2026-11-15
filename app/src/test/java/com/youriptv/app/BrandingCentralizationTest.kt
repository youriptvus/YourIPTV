package com.youriptv.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BrandingCentralizationTest {
    private fun res(path: String) = listOf(File("src/main/res/$path"), File("app/src/main/res/$path")).first { it.exists() }

    @Test fun layoutsDoNotHardcodeTheBrandName() {
        val offenders = res("layout").listFiles()!!.filter { it.readText().contains("YourIPTV") }.map { it.name }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun defaultBrandNameMatchesTheIntegrityPin() {
        val branding = res("values/branding.xml").readText()
        assertTrue(branding.contains("<string name=\"app_name\">${BrandIntegrity.EXPECTED_BRAND_NAME}</string>"))
    }

    @Test fun mainMenuShowsTheBundledOfficialLogoWithoutAddingARemoteFocusTarget() {
        val android = "http://schemas.android.com/apk/res/android"
        for (layout in listOf("layout/activity_main.xml", "layout-port/activity_main.xml")) {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = res(layout).inputStream().use { factory.newDocumentBuilder().parse(it) }
            val images = document.getElementsByTagName("ImageView")
            val logo = (0 until images.length).map { images.item(it) as org.w3c.dom.Element }
                .single { it.getAttributeNS(android, "id") == "@+id/brand" }
            assertEquals("@drawable/banner", logo.getAttributeNS(android, "src"))
            assertEquals("@string/app_name", logo.getAttributeNS(android, "contentDescription"))
            assertEquals("fitCenter", logo.getAttributeNS(android, "scaleType"))
            assertEquals("false", logo.getAttributeNS(android, "focusable"))
            assertEquals("false", logo.getAttributeNS(android, "clickable"))
        }
        assertTrue(res("drawable-xhdpi/banner.png").isFile)
        assertTrue(res("drawable-xxxhdpi/banner.png").isFile)
    }

    @Test fun defaultAboutWebsiteIsYourIPTVUs() {
        val branding = res("values/branding.xml").readText()
        assertTrue(branding.contains("<string name=\"brand_support_url\">https://youriptv.us</string>"))
    }

    @Test fun aboutBodyTemplateDoesNotHardcodeTheBrandName() {
        val src = listOf(File("src/main/java/com/youriptv/app/ui/Branding.kt"), File("app/src/main/java/com/youriptv/app/ui/Branding.kt")).first { it.exists() }
        val body = src.readText().substringAfter("fun aboutBody(").substringBefore("\"\"\"\n    }")
        assertFalse(body.contains("YourIPTV"))
        assertTrue(body.contains("OPEN-SOURCE LIBRARIES"))
    }

    @Test fun freeNoticeIsWordedWithTheBrandName() {
        val notice = Regex("<string name=\"free_notice\">(.*?)</string>").find(res("values/strings.xml").readText())!!.groupValues[1]
        assertTrue(notice.startsWith("%1\$s is free."))
        assertFalse(notice.contains("YourIPTV"))
    }
}
