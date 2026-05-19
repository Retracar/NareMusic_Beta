package com.example.naremusic_beta.playercore.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

data class AudioCapabilityResult(
    val supportedSampleRates: IntArray = intArrayOf(),
    val supportedChannelCounts: IntArray = intArrayOf(),
    val suggestedSampleRate: Int,
    val supportsHighRes: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioCapabilityResult) return false

        return supportedSampleRates.contentEquals(other.supportedSampleRates) &&
            supportedChannelCounts.contentEquals(other.supportedChannelCounts) &&
            suggestedSampleRate == other.suggestedSampleRate &&
            supportsHighRes == other.supportsHighRes
    }

    override fun hashCode(): Int {
        var result = supportedSampleRates.contentHashCode()
        result = 31 * result + supportedChannelCounts.contentHashCode()
        result = 31 * result + suggestedSampleRate
        result = 31 * result + supportsHighRes.hashCode()
        return result
    }
}

object AudioCapabilities {
    fun probe(context: Context): AudioCapabilityResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val deviceInfos = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val sampleRates = mutableSetOf<Int>()
        val channelCounts = mutableSetOf<Int>()

        for (d in deviceInfos) {
            try {
                val rates = d.sampleRates
                if (rates != null && rates.isNotEmpty()) sampleRates.addAll(rates.toList())
                val channels = d.channelCounts
                if (channels != null && channels.isNotEmpty()) channelCounts.addAll(channels.toList())
            } catch (_: Throwable) {
            }
        }

        // Fallback to AudioManager properties
        val fallbackSampleRate = try {
            val prop = try {
                am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            } catch (_: SecurityException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }

            try {
                prop?.toInt() ?: 44100
            } catch (_: NumberFormatException) {
                44100
            }
        } catch (_: SecurityException) {
            44100
        } catch (_: UnsupportedOperationException) {
            44100
        }

        val supportedRates = if (sampleRates.isEmpty()) intArrayOf(fallbackSampleRate) else sampleRates.toIntArray()
        val supportedChannels = if (channelCounts.isEmpty()) intArrayOf(2) else channelCounts.toIntArray()

        val maxRate = supportedRates.maxOrNull() ?: fallbackSampleRate
        val supportsHighRes = maxRate >= 96000

        val sortedSupportedRates = supportedRates.sorted()
        val suggested = listOf(96000, 48000, 44100).firstOrNull { preferred ->
            sortedSupportedRates.contains(preferred)
        } ?: sortedSupportedRates.lastOrNull() ?: fallbackSampleRate

        return AudioCapabilityResult(
            supportedSampleRates = supportedRates,
            supportedChannelCounts = supportedChannels,
            suggestedSampleRate = suggested,
            supportsHighRes = supportsHighRes,
        )
    }
}
