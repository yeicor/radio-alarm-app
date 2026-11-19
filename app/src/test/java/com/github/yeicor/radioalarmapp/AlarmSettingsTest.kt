package com.github.yeicor.radioalarmapp

import com.github.yeicor.radioalarmapp.audio.AudioSignal
import com.github.yeicor.radioalarmapp.data.Alarm
import com.github.yeicor.radioalarmapp.data.AppSettings
import com.github.yeicor.radioalarmapp.data.withDefaults
import com.github.yeicor.radioalarmapp.update.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSettingsTest {

    @Test
    fun alarm_volumePercent_defaultsTo100() {
        val alarm = Alarm()
        assertEquals(100, alarm.volumePercent)
    }

    @Test
    fun alarm_volumePercent_roundTripsThroughJson() {
        val original = Alarm(volumePercent = 45)
        val json = original.toJson()
        val restored = Alarm.fromJson(json)
        assertEquals(45, restored.volumePercent)
    }

    @Test
    fun alarm_withDefaults_inheritsSoftwareVolumeFromSettings() {
        val settings = AppSettings(softwareVolumePercent = 70)
        val alarm = Alarm().withDefaults(settings)
        assertEquals(70, alarm.volumePercent)
    }

    @Test
    fun appSettings_defaults_areValid() {
        val settings = AppSettings()
        assertEquals(100, settings.softwareVolumePercent)
        assertEquals(50, settings.silenceThresholdPercent)
        assertTrue(settings.autoBalance)
        assertEquals(UpdateChannel.STABLE, settings.updateChannel)
    }

    @Test
    fun audioSignal_balanceGain_boostsQuietAudioAndLeavesLoudAudioAlone() {
        assertEquals(1f, AudioSignal.balanceGain(0.2f), 0.001f)
        assertEquals(2f, AudioSignal.balanceGain(0.04f), 0.001f)
        assertEquals(AudioSignal.BALANCE_MAX_GAIN, AudioSignal.balanceGain(0.001f), 0.001f)
    }

    @Test
    fun audioSignal_balanceGain_respectsPeakLimit() {
        assertEquals(1f, AudioSignal.balanceGain(0.01f, peak = 0.95f), 0.001f)
    }

    @Test
    fun audioSignal_isAudible_respectsConfigurableThreshold() {
        val baseThreshold = AudioSignal.SILENCE_RMS // 0.003f
        assertTrue(AudioSignal.isAudible(0.005f, baseThreshold))
        assertFalse(AudioSignal.isAudible(0.001f, baseThreshold))

        // Custom threshold
        val lowerThreshold = 0.001f
        assertTrue(AudioSignal.isAudible(0.0015f, lowerThreshold))
        assertFalse(AudioSignal.isAudible(0.0005f, lowerThreshold))
    }
}
