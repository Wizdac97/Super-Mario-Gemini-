package com.superplumber.odyssey

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.hypot
import kotlin.math.min

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val saveManager = SaveManager(context)
    private val soundEngine = SoundEngine().apply {
        sfxEnabled = saveManager.sfxEnabled
        bgmEnabled = saveManager.bgmEnabled
    }
    private val touchControls = TouchControls().apply {
        autoRun = saveManager.autoRunEnabled
        hapticsEnabled = saveManager.hapticsEnabled
        controlScale = saveManager.controlScale
        controlOpacity = saveManager.controlOpacity
    }
    private val physics = PhysicsEngine(soundEngine)
    private val renderer = SpriteRenderer()

    @Volatile
    private var running = false
    private var gameThread: Thread? = null
    private var screen = GameScreen.TITLE

    private var selectedMissionIdx = 0
    private var heroMapX = 0f
    private var heroMapY = 0f
    private var heroMapInit = false

    private var fadeAlpha = 0f
    private var fadingOut = false
    private var pendingTransition: (() -> Unit)? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        soundEngine.start()
        running = true
        gameThread = Thread(this, "GameLoopThread").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        touchControls.layout(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            gameThread?.join(500)
        } catch (_: InterruptedException) {}
        soundEngine.release()
    }

    fun handleBackPress(): Boolean {
        return when (screen) {
            GameScreen.PLAYING -> {
                screen = GameScreen.PAUSED
                touchControls.resetAll()
                true
            }
            GameScreen.PAUSED, GameScreen.MISSION_COMPLETE, GameScreen.GAME_OVER -> {
                startFadeTo { screen = GameScreen.MISSION_SELECT }
                true
            }
            GameScreen.MISSION_SELECT -> {
                startFadeTo { screen = GameScreen.TITLE }
                true
            }
            GameScreen.TITLE -> false
        }
    }

    fun onPauseActivity() {
        if (screen == GameScreen.PLAYING) {
            screen = GameScreen.PAUSED
        }
        soundEngine.pause()
        touchControls.resetAll()
    }

    fun onResumeActivity() {
        soundEngine.resume()
    }

    private fun startFadeTo(action: () -> Unit) {
        if (fadingOut) return
        fadingOut = true
        pendingTransition = action
    }

    override fun run() {
        var lastNs = System.nanoTime()
        while (running) {
            val nowNs = System.nanoTime()
            val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
            lastNs = nowNs

            val w = width
            val h = height
            if (w <= 0 || h <= 0) continue

            // Fade transition logic
            if (fadingOut) {
                fadeAlpha = (fadeAlpha + dt * 4.5f).coerceAtMost(1f)
                if (fadeAlpha >= 1f) {
                    fadingOut = false
                    pendingTransition?.invoke()
                    pendingTransition = null
                }
            } else if (fadeAlpha > 0f) {
                fadeAlpha = (fadeAlpha - dt * 4.5f).coerceAtLeast(0f)
            }

            // Smooth hero movement on Overworld Map
            if (screen == GameScreen.MISSION_SELECT) {
                val targetPt = renderer.missionNodeCenters[selectedMissionIdx]
                if (!heroMapInit && targetPt.x > 0f) {
                    heroMapX = targetPt.x
                    heroMapY = targetPt.y
                    heroMapInit = true
                } else if (heroMapInit) {
                    heroMapX += (targetPt.x - heroMapX) * min(1f, dt * 8f)
                    heroMapY += (targetPt.y - heroMapY) * min(1f, dt * 8f)
                }
            }

            if (screen == GameScreen.PLAYING && !fadingOut) {
                if (touchControls.pauseJustPressed) {
                    screen = GameScreen.PAUSED
                    touchControls.resetAll()
                } else if (touchControls.muteJustPressed) {
                    val next = !soundEngine.sfxEnabled
                    soundEngine.sfxEnabled = next
                    soundEngine.bgmEnabled = next
                    saveManager.sfxEnabled = next
                    saveManager.bgmEnabled = next
                } else {
                    val tilePx = h.toFloat() / physics.rows.toFloat()
                    val viewWidthTiles = w.toFloat() / tilePx
                    physics.update(dt, touchControls, viewWidthTiles)

                    if (physics.player.won && physics.player.winTimer >= 1.6f) {
                        val stars = physics.computeEarnedStars()
                        saveManager.saveMissionResult(
                            physics.mission.id,
                            stars,
                            physics.timeLeft.toInt(),
                            physics.player.score,
                            LevelRepository.missions.size
                        )
                        screen = GameScreen.MISSION_COMPLETE
                        touchControls.resetAll()
                    } else if (physics.player.dead && physics.player.deathTimer >= 1.4f) {
                        if (physics.player.lives > 0) {
                            physics.loadMission(physics.mission, keepCheckpoint = physics.checkpointReached)
                        } else {
                            physics.player.lives = 3
                            screen = GameScreen.GAME_OVER
                            touchControls.resetAll()
                        }
                    }
                }
                touchControls.clearFrameEdges()
            }

            val canvas = holder.lockCanvas() ?: continue
            try {
                when (screen) {
                    GameScreen.TITLE -> renderer.renderTitle(canvas, w, h, dt, saveManager)
                    GameScreen.MISSION_SELECT -> renderer.renderOverworldMap(canvas, w, h, dt, saveManager, selectedMissionIdx, heroMapX, heroMapY)
                    GameScreen.PLAYING -> renderer.renderGame(canvas, w, h, dt, physics, touchControls, soundEngine)
                    GameScreen.PAUSED, GameScreen.MISSION_COMPLETE, GameScreen.GAME_OVER -> {
                        renderer.renderGame(canvas, w, h, 0f, physics, touchControls, soundEngine)
                        renderer.renderOverlay(canvas, w, h, screen, physics)
                    }
                }
                renderer.renderFadeCurtain(canvas, w, h, fadeAlpha)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (screen == GameScreen.PLAYING) {
            touchControls.onTouchEvent(event, this)
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            when (screen) {
                GameScreen.TITLE -> {
                    if (renderer.playBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        startFadeTo { screen = GameScreen.MISSION_SELECT }
                    }
                }
                GameScreen.MISSION_SELECT -> {
                    handleOverworldTap(x, y)
                }
                GameScreen.PAUSED -> {
                    if (renderer.resumeBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        screen = GameScreen.PLAYING
                    } else if (renderer.restartBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        startFadeTo {
                            physics.player.lives = 3
                            physics.loadMission(physics.mission, keepCheckpoint = false)
                            screen = GameScreen.PLAYING
                        }
                    } else if (renderer.menuBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        startFadeTo { screen = GameScreen.MISSION_SELECT }
                    }
                }
                GameScreen.MISSION_COMPLETE -> {
                    if (physics.mission.id < LevelRepository.missions.size && renderer.nextMissionBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        val next = LevelRepository.missions[physics.mission.id]
                        selectedMissionIdx = physics.mission.id
                        startFadeTo {
                            physics.player.lives = 3
                            physics.loadMission(next, keepCheckpoint = false)
                            screen = GameScreen.PLAYING
                        }
                    } else if (renderer.menuBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        startFadeTo { screen = GameScreen.MISSION_SELECT }
                    }
                }
                GameScreen.GAME_OVER -> {
                    if (renderer.restartBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        startFadeTo {
                            physics.player.lives = 3
                            physics.loadMission(physics.mission, keepCheckpoint = false)
                            screen = GameScreen.PLAYING
                        }
                    } else if (renderer.menuBtnRect.contains(x, y)) {
                        touchControls.haptic(this, false)
                        startFadeTo { screen = GameScreen.MISSION_SELECT }
                    }
                }
                else -> {}
            }
        }
        return true
    }

    private fun handleOverworldTap(x: Float, y: Float) {
        when {
            renderer.toggleSfxRect.contains(x, y) -> {
                saveManager.sfxEnabled = !saveManager.sfxEnabled
                soundEngine.sfxEnabled = saveManager.sfxEnabled
                touchControls.haptic(this, false)
                return
            }
            renderer.toggleBgmRect.contains(x, y) -> {
                saveManager.bgmEnabled = !saveManager.bgmEnabled
                soundEngine.bgmEnabled = saveManager.bgmEnabled
                touchControls.haptic(this, false)
                return
            }
            renderer.toggleHapticRect.contains(x, y) -> {
                saveManager.hapticsEnabled = !saveManager.hapticsEnabled
                touchControls.hapticsEnabled = saveManager.hapticsEnabled
                touchControls.haptic(this, false)
                return
            }
            renderer.toggleAutoRunRect.contains(x, y) -> {
                saveManager.autoRunEnabled = !saveManager.autoRunEnabled
                touchControls.autoRun = saveManager.autoRunEnabled
                touchControls.haptic(this, false)
                return
            }
            renderer.toggleSizeRect.contains(x, y) -> {
                val next = when {
                    saveManager.controlScale < 0.9f -> 1.0f
                    saveManager.controlScale > 1.1f -> 0.82f
                    else -> 1.18f
                }
                saveManager.controlScale = next
                touchControls.controlScale = next
                touchControls.relayout()
                touchControls.haptic(this, false)
                return
            }
            renderer.toggleOpacityRect.contains(x, y) -> {
                val next = when {
                    saveManager.controlOpacity < 0.65f -> 0.85f
                    saveManager.controlOpacity > 0.9f -> 0.50f
                    else -> 1.0f
                }
                saveManager.controlOpacity = next
                touchControls.controlOpacity = next
                touchControls.haptic(this, false)
                return
            }
            renderer.toggleUnlockRect.contains(x, y) -> {
                saveManager.unlockAllEnabled = !saveManager.unlockAllEnabled
                touchControls.haptic(this, false)
                return
            }
            renderer.startMissionBtnRect.contains(x, y) -> {
                val m = LevelRepository.missions[selectedMissionIdx]
                if (saveManager.getMissionSave(m.id).unlocked) {
                    touchControls.haptic(this, true)
                    startFadeTo {
                        physics.player.lives = 3
                        physics.loadMission(m, keepCheckpoint = false)
                        screen = GameScreen.PLAYING
                    }
                }
                return
            }
        }

        for (i in LevelRepository.missions.indices) {
            val pt = renderer.missionNodeCenters[i]
            if (hypot(x - pt.x, y - pt.y) <= renderer.missionNodeRadius * 1.35f) {
                touchControls.haptic(this, false)
                if (selectedMissionIdx == i && saveManager.getMissionSave(LevelRepository.missions[i].id).unlocked) {
                    startFadeTo {
                        physics.player.lives = 3
                        physics.loadMission(LevelRepository.missions[i], keepCheckpoint = false)
                        screen = GameScreen.PLAYING
                    }
                } else {
                    selectedMissionIdx = i
                }
                return
            }
        }
    }
}
