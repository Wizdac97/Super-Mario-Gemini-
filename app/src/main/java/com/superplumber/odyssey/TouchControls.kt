package com.superplumber.odyssey

import android.graphics.PointF
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class TouchControls {
    private val pointers = mutableMapOf<Int, PointF>()

    val leftRect = RectF()
    val rightRect = RectF()
    val slideBridgeRect = RectF()
    val downRect = RectF()
    val jumpCenter = PointF()
    var jumpRadius = 0f
    val fireCenter = PointF()
    var fireRadius = 0f

    val pauseRect = RectF()
    val muteRect = RectF()

    var left = false
    var right = false
    var down = false
    var downJustPressed = false
    var jumpHeld = false
    var jumpJustPressed = false
    var runHeld = false
    var fireJustPressed = false
    var pauseJustPressed = false
    var muteJustPressed = false

    private var prevJumpHeld = false
    private var prevFireHeld = false
    private var prevDownHeld = false

    var autoRun = false
    var hapticsEnabled = true
    var controlScale = 1.0f
    var controlOpacity = 0.85f

    private var lastW = 0
    private var lastH = 0

    fun relayout() {
        if (lastW > 0 && lastH > 0) layout(lastW, lastH)
    }

    fun layout(w: Int, h: Int) {
        lastW = w
        lastH = h
        val wf = w.toFloat()
        val hf = h.toFloat()
        val unit = hf.coerceAtMost(wf * 0.55f) * controlScale

        val margin = hf * 0.055f
        val dpadW = unit * 0.21f
        val dpadH = unit * 0.21f
        val bottomY = hf - margin

        leftRect.set(margin, bottomY - dpadH, margin + dpadW, bottomY)
        val rightX = margin + dpadW + unit * 0.035f
        rightRect.set(rightX, bottomY - dpadH, rightX + dpadW, bottomY)
        slideBridgeRect.set(
            leftRect.left - margin * 0.5f,
            leftRect.top - unit * 0.08f,
            rightRect.right + margin * 0.5f,
            hf
        )

        val downSize = unit * 0.135f
        val midX = (leftRect.right + rightRect.left) * 0.5f
        downRect.set(
            midX - downSize * 0.5f,
            leftRect.top - downSize - unit * 0.03f,
            midX + downSize * 0.5f,
            leftRect.top - unit * 0.03f
        )

        jumpRadius = unit * 0.128f
        jumpCenter.set(wf - margin - jumpRadius, hf - margin - jumpRadius)

        fireRadius = unit * 0.104f
        fireCenter.set(
            jumpCenter.x - jumpRadius * 1.95f,
            jumpCenter.y - jumpRadius * 0.28f
        )

        val hudUnit = hf.coerceAtMost(wf * 0.55f)
        val hudBtn = hudUnit * 0.105f
        val topM = hudUnit * 0.035f
        pauseRect.set(wf - topM - hudBtn, topM, wf - topM, topM + hudBtn)
        muteRect.set(pauseRect.left - topM - hudBtn, topM, pauseRect.left - topM, topM + hudBtn)
    }

    fun onTouchEvent(event: MotionEvent, view: View) {
        val action = event.actionMasked
        val index = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = event.getPointerId(index)
                val px = event.getX(index)
                val py = event.getY(index)
                pointers[id] = PointF(px, py)

                if (pauseRect.contains(px, py)) {
                    pauseJustPressed = true
                    haptic(view, false)
                } else if (muteRect.contains(px, py)) {
                    muteJustPressed = true
                    haptic(view, false)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    pointers[id]?.set(event.getX(i), event.getY(i))
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = event.getPointerId(index)
                pointers.remove(id)
            }
        }

        recomputeStates(view)
    }

    private fun recomputeStates(view: View) {
        var newLeft = false
        var newRight = false
        var newDown = false
        var newJump = false
        var newFire = false

        val dpadMidX = (leftRect.right + rightRect.left) * 0.5f

        for ((_, p) in pointers) {
            if (pauseRect.contains(p.x, p.y) || muteRect.contains(p.x, p.y)) continue

            if (downRect.contains(p.x, p.y)) {
                newDown = true
            } else if (slideBridgeRect.contains(p.x, p.y)) {
                if (p.x < dpadMidX - 6f) newLeft = true
                else if (p.x > dpadMidX + 6f) newRight = true
            }

            if (hypot(p.x - jumpCenter.x, p.y - jumpCenter.y) <= jumpRadius * 1.40f) {
                newJump = true
            }
            if (hypot(p.x - fireCenter.x, p.y - fireCenter.y) <= fireRadius * 1.40f) {
                newFire = true
            }
        }

        if (newJump && !prevJumpHeld) {
            jumpJustPressed = true
            haptic(view, false)
        }
        if (newFire && !prevFireHeld) {
            fireJustPressed = true
            haptic(view, false)
        }
        if (newDown && !prevDownHeld) {
            downJustPressed = true
            haptic(view, false)
        }

        left = newLeft
        right = newRight
        down = newDown
        jumpHeld = newJump
        runHeld = newFire || autoRun

        prevJumpHeld = newJump
        prevFireHeld = newFire
        prevDownHeld = newDown
    }

    fun clearFrameEdges() {
        jumpJustPressed = false
        fireJustPressed = false
        downJustPressed = false
        pauseJustPressed = false
        muteJustPressed = false
    }

    fun resetAll() {
        pointers.clear()
        left = false
        right = false
        down = false
        downJustPressed = false
        jumpHeld = false
        jumpJustPressed = false
        runHeld = false
        fireJustPressed = false
        pauseJustPressed = false
        muteJustPressed = false
        prevJumpHeld = false
        prevFireHeld = false
        prevDownHeld = false
    }

    fun haptic(view: View, strong: Boolean) {
        if (!hapticsEnabled) return
        val flag = if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        view.performHapticFeedback(flag)
    }
}
