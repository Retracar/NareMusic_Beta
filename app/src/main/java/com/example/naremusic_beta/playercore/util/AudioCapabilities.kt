package com.example.naremusic_beta.playercore.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

data class AudioCapabilityResult(
    val supportedSampleRates: IntArray = intArrayOf(),
    val supportedChannelCounts: IntArray = intArrayOf(),
    val suggestedSampleRate: Int,
    val supportsHighRes: Boolean,
)

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
            val prop = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            prop?.toInt() ?: 44100
        } catch (_: Throwable) {
            44100
        }

        val supportedRates = if (sampleRates.isEmpty()) intArrayOf(fallbackSampleRate) else sampleRates.toIntArray()
        val supportedChannels = if (channelCounts.isEmpty()) intArrayOf(2) else channelCounts.toIntArray()

        val maxRate = supportedRates.maxOrNull() ?: fallbackSampleRate
        val supportsHighRes = maxRate >= 96000

        val suggested = when {
            supportsHighRes -> 96000
            maxRate >= 48000 -> 48000
            else -> 44100
        }

        return AudioCapabilityResult(
            supportedSampleRates = supportedRates,
            supportedChannelCounts = supportedChannels,
            suggestedSampleRate = suggested,
            supportsHighRes = supportsHighRes,
        )
    }
}
