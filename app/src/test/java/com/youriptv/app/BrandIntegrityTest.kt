package com.youriptv.app

import org.junit.Assert.*
import org.junit.Test

class BrandIntegrityTest {
    @Test fun onlyTheOfficialPackageIsAllowedByDefault() {
        assertTrue(BrandIntegrity.isAllowedPackage("com.youriptv.app"))
        assertFalse(BrandIntegrity.isAllowedPackage("com.rebrander.iptv"))
        assertFalse(BrandIntegrity.isAllowedPackage("com.youriptv.app.pirate"))
        assertEquals("YourIPTV", BrandIntegrity.EXPECTED_BRAND_NAME)
    }

    @Test fun theCustomToolExceptionIsClosedUntilConfigured() {
        // The one sanctioned bypass seam must ship closed, so no stray package is trusted by accident.
        assertNull(BrandIntegrity.CUSTOM_TOOL_EXCEPTION)
    }

    @Test fun rootIsDetectedFromSuBinariesOrTestKeys() {
        val su = "/system/xbin/su"
        assertTrue(BrandIntegrity.isDeviceRooted({ it == su }, "release-keys"))
        assertTrue(BrandIntegrity.isDeviceRooted({ false }, "test-keys"))
        assertFalse(BrandIntegrity.isDeviceRooted({ false }, "release-keys"))
    }

    @Test fun tracerPidZeroMeansNoDebuggerAttached() {
        val clean = "Name:\tapp\nState:\tS\nTracerPid:\t0\nUid:\t10538\n"
        val traced = "Name:\tapp\nTracerPid:\t4321\nUid:\t10538\n"
        assertEquals(0, BrandIntegrity.tracerPidFromStatus(clean))
        assertEquals(4321, BrandIntegrity.tracerPidFromStatus(traced))
        assertEquals(0, BrandIntegrity.tracerPidFromStatus("no status line here"))
    }
}
