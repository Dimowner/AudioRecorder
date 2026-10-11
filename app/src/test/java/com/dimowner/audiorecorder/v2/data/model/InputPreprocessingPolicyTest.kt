package com.dimowner.audiorecorder.v2.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InputPreprocessingPolicyTest {
    @Test
    fun `system default leaves all effects untouched`() {
        assertRequests(InputPreprocessingPolicy.SYSTEM_DEFAULT, null, null, null)
    }

    @Test
    fun `disable NS leaves other effects untouched`() {
        assertRequests(InputPreprocessingPolicy.DISABLE_NS, false, null, null)
    }

    @Test
    fun `disable NS AEC leaves AGC untouched`() {
        assertRequests(InputPreprocessingPolicy.DISABLE_NS_AEC, false, false, null)
    }

    @Test
    fun `disable all explicitly disables every effect`() {
        assertRequests(InputPreprocessingPolicy.DISABLE_NS_AEC_AGC, false, false, false)
    }

    @Test
    fun `AGC only disables NS and AEC and enables AGC`() {
        assertRequests(InputPreprocessingPolicy.AGC_ONLY, false, false, true)
    }

    private fun assertRequests(policy: InputPreprocessingPolicy, ns: Boolean?, aec: Boolean?, agc: Boolean?) {
        assertEquals(ns, policy.requestedNs)
        assertEquals(aec, policy.requestedAec)
        assertEquals(agc, policy.requestedAgc)
    }
}
