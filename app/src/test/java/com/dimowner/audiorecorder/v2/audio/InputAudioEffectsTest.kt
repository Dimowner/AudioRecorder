package com.dimowner.audiorecorder.v2.audio

import com.dimowner.audiorecorder.v2.data.model.InputPreprocessingPolicy
import org.junit.Assert.*
import org.junit.Test

class InputAudioEffectsTest {
    private class Effect : InputEffectHandle {
        override var enabled = true
        override val hasControl = true
        var writes = 0
        var releases = 0
        override fun setEnabledState(value: Boolean): Int { enabled = value; writes++; return 0 }
        override fun release() { releases++ }
    }
    private class Factory : InputEffectFactory {
        val handles = mutableListOf<Effect>()
        var available = true
        var returnsNull = false
        override fun available(kind: InputEffectKind) = available
        override fun create(kind: InputEffectKind, sessionId: Int): InputEffectHandle? =
            if (returnsNull) null else Effect().also { handles.add(it) }
    }

    @Test fun `default inspects without changing effect states`() {
        val factory = Factory()
        val effects = InputAudioEffects(42, InputPreprocessingPolicy.SYSTEM_DEFAULT, factory)
        assertTrue(factory.handles.all { it.writes == 0 })
        assertTrue(effects.status.all { it.actualEnabled == true })
        effects.close(); effects.close()
        assertTrue(factory.handles.all { it.releases == 1 })
    }

    @Test fun `AGC only disables NS and AEC and enables AGC`() {
        val factory = Factory()
        val effects = InputAudioEffects(42, InputPreprocessingPolicy.AGC_ONLY, factory)
        assertEquals(listOf(false, false, true), effects.status.map { it.actualEnabled })
        assertTrue(effects.status.all { it.message == "Applied" })
        effects.close()
    }

    @Test fun `unavailable effects are never created`() {
        val factory = Factory().apply { available = false }
        val effects = InputAudioEffects(42, InputPreprocessingPolicy.DISABLE_NS_AEC_AGC, factory)
        assertTrue(factory.handles.isEmpty())
        assertTrue(effects.status.all { !it.available && it.actualEnabled == null })
    }

    @Test fun `null effect creation is reported`() {
        val factory = Factory().apply { returnsNull = true }
        val effects = InputAudioEffects(42, InputPreprocessingPolicy.DISABLE_NS_AEC_AGC, factory)
        assertTrue(effects.status.all { it.available && it.message.contains("null") })
    }

    @Test fun `control denial reports actual state without attempting enable`() {
        var writes = 0
        val factory = object : InputEffectFactory {
            override fun available(kind: InputEffectKind) = true
            override fun create(kind: InputEffectKind, sessionId: Int) = object : InputEffectHandle {
                override val enabled = true
                override val hasControl = false
                override fun setEnabledState(value: Boolean): Int { writes++; return 0 }
                override fun release() = Unit
            }
        }
        val effects = InputAudioEffects(3, InputPreprocessingPolicy.DISABLE_NS_AEC_AGC, factory)
        assertEquals(0, writes)
        assertTrue(effects.status.all { it.actualEnabled == true && it.message == "Control denied" })
        effects.close()
    }

    @Test fun `setEnabled error and creation exception are independently reported`() {
        var releases = 0
        val factory = object : InputEffectFactory {
            override fun available(kind: InputEffectKind) = true
            override fun create(kind: InputEffectKind, sessionId: Int): InputEffectHandle {
                if (kind == InputEffectKind.AEC) throw IllegalStateException("device rejected AEC")
                return object : InputEffectHandle {
                    override val enabled = true
                    override val hasControl = true
                    override fun setEnabledState(value: Boolean) = -5
                    override fun release() { releases++ }
                }
            }
        }
        val effects = InputAudioEffects(3, InputPreprocessingPolicy.DISABLE_NS_AEC_AGC, factory)
        assertTrue(effects.status[0].message.contains("failed: -5"))
        assertTrue(effects.status[1].message.contains("device rejected AEC"))
        assertTrue(effects.status[2].message.contains("failed: -5"))
        effects.close()
        assertEquals(2, releases)
    }
}
