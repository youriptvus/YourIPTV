package com.youriptv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class OfficialSigningTest {
    private val signer = "test certificate bytes".toByteArray()
    private val digest = MessageDigest.getInstance("SHA-256").digest(signer)

    @Test fun matchingSingleSignerIsAccepted() {
        assertTrue(OfficialSigning.matchesSingleSigner(listOf(signer), digest))
    }

    @Test fun differentOrMissingSignerIsRejected() {
        assertFalse(OfficialSigning.matchesSingleSigner(listOf("other certificate".toByteArray()), digest))
        assertFalse(OfficialSigning.matchesSingleSigner(emptyList(), digest))
        assertFalse(OfficialSigning.matchesSingleSigner(listOf(signer), ByteArray(32)))
    }

    @Test fun multipleSignersAreRejectedOnLegacyAndroid() {
        assertFalse(OfficialSigning.matchesSingleSigner(listOf(signer, signer), digest))
    }

    @Test fun pinnedFingerprintMatchesTheExistingReleaseCertificate() {
        assertEquals(
            "4aef8798d4038bb8b20486b96b50af0870bca9d9b244a541a8973f76cecd0539",
            OfficialSigning.RELEASE_CERTIFICATE_SHA256,
        )
    }
}
