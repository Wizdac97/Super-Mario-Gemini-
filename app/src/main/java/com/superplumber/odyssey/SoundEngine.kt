package com.superplumber.odyssey

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.abs
import kotlin.random.Random

class SoundEngine {
    private enum class Wave { SQUARE, TRIANGLE, NOISE }

    private data class Voice(
        val wave: Wave,
        val startFreq: Float,
        val endFreq: Float,
        val duration: Float,
        val volume: Float,
        var elapsed: Float = 0f,
        var phase: Float = 0f
    )

    var sfxEnabled: Boolean = true
    var bgmEnabled: Boolean = true
    var currentBiome: Biome = Biome.PLAINS

    private val sampleRate = 22050
    private val voices = mutableListOf<Voice>()
    private val voiceLock = Any()

    @Volatile
    private var running = false
    @Volatile
    private var paused = false
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private var bgmStep = 0
    private var bgmTimer = 0f

    private val overworldLead = floatArrayOf(
        659.25f, 659.25f, 0f, 659.25f, 0f, 523.25f, 659.25f, 0f,
        783.99f, 0f, 0f, 0f, 392.00f, 0f, 0f, 0f,
        523.25f, 0f, 0f, 392.00f, 0f, 0f, 329.63f, 0f,
        0f, 440.00f, 0f, 493.88f, 0f, 466.16f, 440.00f, 0f
    )
    private val overworldBass = floatArrayOf(
        130.81f, 0f, 196.00f, 0f, 130.81f, 0f, 196.00f, 0f,
        196.00f, 0f, 246.94f, 0f, 196.00f, 0f, 146.83f, 0f,
        130.81f, 0f, 196.00f, 0f, 164.81f, 0f, 130.81f, 0f,
        174.61f, 0f, 196.00f, 0f, 174.61f, 0f, 146.83f, 0f
    )

    private val cavernLead = floatArrayOf(
        261.63f, 523.25f, 220.00f, 440.00f, 233.08f, 466.16f, 0f, 0f,
        261.63f, 523.25f, 220.00f, 440.00f, 233.08f, 466.16f, 0f, 0f,
        174.61f, 349.23f, 146.83f, 293.66f, 155.56f, 311.13f, 0f, 0f,
        155.56f, 146.83f, 130.81f, 0f, 196.00f, 0f, 130.81f, 0f
    )
    private val cavernBass = floatArrayOf(
        65.41f, 0f, 65.41f, 0f, 98.00f, 0f, 65.41f, 0f,
        65.41f, 0f, 65.41f, 0f, 98.00f, 0f, 65.41f, 0f,
        87.31f, 0f, 87.31f, 0f, 73.42f, 0f, 87.31f, 0f,
        77.78f, 0f, 73.42f, 0f, 65.41f, 0f, 98.00f, 0f
    )

    private val castleLead = floatArrayOf(
        196.00f, 293.66f, 311.13f, 349.23f, 311.13f, 293.66f, 261.63f, 246.94f,
        196.00f, 293.66f, 311.13f, 392.00f, 349.23f, 311.13f, 293.66f, 246.94f,
        220.00f, 329.63f, 349.23f, 392.00f, 349.23f, 329.63f, 293.66f, 261.63f,
        246.94f, 293.66f, 349.23f, 440.00f, 392.00f, 349.23f, 293.66f, 246.94f
    )
    private val castleBass = floatArrayOf(
        98.00f, 98.00f, 116.54f, 130.81f, 116.54f, 98.00f, 87.31f, 98.00f,
        98.00f, 98.00f, 116.54f, 146.83f, 130.81f, 116.54f, 98.00f, 87.31f,
        110.00f, 110.00f, 130.81f, 146.83f, 130.81f, 110.00f, 98.00f, 87.31f,
        123.47f, 123.47f, 146.83f, 164.81f, 146.83f, 130.81f, 110.00f, 98.00f
    )

    fun start() {
        if (running) return
        running = true
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        audioThread = Thread {
            val chunkSamples = 512
            val buffer = ShortArray(chunkSamples)
            val dt = 1f / sampleRate
            var leadPhase = 0f
            var bassPhase = 0f

            while (running) {
                if (paused) {
                    Thread.sleep(30)
                    continue
                }

                for (i in 0 until chunkSamples) {
                    var mix = 0f

                    if (bgmEnabled) {
                        val (lead, bass) = when (currentBiome) {
                            Biome.CAVERN, Biome.NIGHT, Biome.ICE -> cavernLead to cavernBass
                            Biome.VOLCANO, Biome.CASTLE -> castleLead to castleBass
                            else -> overworldLead to overworldBass
                        }
                        val stepDur = if (currentBiome == Biome.CASTLE || currentBiome == Biome.VOLCANO) 0.14f else 0.16f
                        bgmTimer += dt
                        if (bgmTimer >= stepDur) {
                            bgmTimer -= stepDur
                            bgmStep = (bgmStep + 1) % lead.size
                        }
                        val fLead = lead[bgmStep]
                        if (fLead > 0f) {
                            leadPhase += fLead * dt
                            if (leadPhase >= 1f) leadPhase -= 1f
                            val env = (1f - (bgmTimer / stepDur) * 0.65f).coerceIn(0.1f, 1f)
                            val sq = if (leadPhase < 0.35f) 1f else -1f
                            mix += sq * 0.045f * env
                        }
                        val fBass = bass[bgmStep]
                        if (fBass > 0f) {
                            bassPhase += fBass * dt
                            if (bassPhase >= 1f) bassPhase -= 1f
                            val tri = 1f - 4f * abs(bassPhase - 0.5f)
                            mix += tri * 0.065f
                        }
                    }

                    synchronized(voiceLock) {
                        var idx = voices.size - 1
                        while (idx >= 0) {
                            val v = voices[idx]
                            v.elapsed += dt
                            if (v.elapsed >= v.duration) {
                                voices.removeAt(idx)
                            } else {
                                val t = v.elapsed / v.duration
                                val freq = v.startFreq + (v.endFreq - v.startFreq) * t
                                v.phase += freq * dt
                                if (v.phase >= 1f) v.phase -= 1f
                                val raw = when (v.wave) {
                                    Wave.SQUARE -> if (v.phase < 0.5f) 1f else -1f
                                    Wave.TRIANGLE -> 1f - 4f * abs(v.phase - 0.5f)
                                    Wave.NOISE -> Random.nextFloat() * 2f - 1f
                                }
                                mix += raw * v.volume * (1f - t)
                            }
                            idx--
                        }
                    }

                    buffer[i] = (mix.coerceIn(-0.95f, 0.95f) * 32767f).toInt().toShort()
                }

                try {
                    audioTrack?.write(buffer, 0, chunkSamples)
                } catch (_: Throwable) {
                    break
                }
            }
        }.apply {
            name = "ChiptuneAudioThread"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun pause() {
        paused = true
        try { audioTrack?.pause() } catch (_: Throwable) {}
    }

    fun resume() {
        paused = false
        try { audioTrack?.play() } catch (_: Throwable) {}
    }

    fun release() {
        running = false
        try { audioThread?.join(200) } catch (_: Throwable) {}
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) {}
        audioTrack = null
        audioThread = null
    }

    private fun addVoice(wave: Wave, startF: Float, endF: Float, dur: Float, vol: Float) {
        if (!sfxEnabled) return
        synchronized(voiceLock) {
            if (voices.size < 16) {
                voices.add(Voice(wave, startF, endF, dur, vol))
            }
        }
    }

    fun playJump(big: Boolean) {
        if (big) addVoice(Wave.SQUARE, 220f, 540f, 0.16f, 0.22f)
        else addVoice(Wave.SQUARE, 310f, 680f, 0.13f, 0.20f)
    }

    fun playWallJump() {
        addVoice(Wave.SQUARE, 380f, 820f, 0.11f, 0.20f)
    }

    fun playCheckpoint() {
        if (!sfxEnabled) return
        Thread {
            for (f in floatArrayOf(587.33f, 739.99f, 880f, 1174.66f)) {
                addVoice(Wave.TRIANGLE, f, f, 0.10f, 0.26f)
                Thread.sleep(60)
            }
        }.start()
    }

    fun playCoin() {
        if (!sfxEnabled) return
        addVoice(Wave.SQUARE, 987.77f, 987.77f, 0.06f, 0.20f)
        Thread {
            Thread.sleep(55)
            addVoice(Wave.SQUARE, 1318.51f, 1318.51f, 0.18f, 0.20f)
        }.start()
    }

    fun playStarCoin() {
        if (!sfxEnabled) return
        Thread {
            for (f in floatArrayOf(784f, 988f, 1175f, 1568f)) {
                addVoice(Wave.SQUARE, f, f, 0.09f, 0.22f)
                Thread.sleep(65)
            }
        }.start()
    }

    fun playPowerUp() {
        if (!sfxEnabled) return
        Thread {
            for (f in floatArrayOf(440f, 554f, 659f, 880f, 1108f, 1318f)) {
                addVoice(Wave.TRIANGLE, f, f * 1.05f, 0.08f, 0.28f)
                Thread.sleep(55)
            }
        }.start()
    }

    fun play1Up() {
        if (!sfxEnabled) return
        Thread {
            for (f in floatArrayOf(330f, 392f, 659f, 523f, 587f, 784f)) {
                addVoice(Wave.SQUARE, f, f, 0.09f, 0.22f)
                Thread.sleep(75)
            }
        }.start()
    }

    fun playBrick() {
        addVoice(Wave.NOISE, 220f, 60f, 0.14f, 0.25f)
        addVoice(Wave.SQUARE, 160f, 70f, 0.11f, 0.18f)
    }

    fun playStomp() {
        addVoice(Wave.TRIANGLE, 480f, 120f, 0.12f, 0.30f)
    }

    fun playKick() {
        addVoice(Wave.SQUARE, 260f, 520f, 0.10f, 0.22f)
    }

    fun playFireball() {
        addVoice(Wave.SQUARE, 880f, 220f, 0.10f, 0.18f)
    }

    fun playSpring() {
        addVoice(Wave.TRIANGLE, 240f, 880f, 0.22f, 0.26f)
    }

    fun playHurt() {
        addVoice(Wave.SQUARE, 440f, 110f, 0.28f, 0.25f)
        addVoice(Wave.NOISE, 180f, 80f, 0.22f, 0.20f)
    }

    fun playBossRoar() {
        addVoice(Wave.NOISE, 140f, 55f, 0.35f, 0.30f)
        addVoice(Wave.SQUARE, 110f, 65f, 0.35f, 0.24f)
    }

    fun playVictory() {
        if (!sfxEnabled) return
        val melody = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f, 783.99f, 1046.50f)
        val delays = longArrayOf(110L, 110L, 110L, 220L, 110L, 380L)
        Thread {
            for (i in melody.indices) {
                addVoice(Wave.SQUARE, melody[i], melody[i], delays[i] / 1000f + 0.04f, 0.25f)
                Thread.sleep(delays[i])
            }
        }.start()
    }
}
