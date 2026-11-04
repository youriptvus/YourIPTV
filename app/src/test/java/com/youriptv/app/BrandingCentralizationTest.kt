package com.youriptv.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

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
