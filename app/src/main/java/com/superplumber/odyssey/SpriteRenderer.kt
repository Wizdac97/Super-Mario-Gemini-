package com.superplumber.odyssey

import android.graphics.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SpriteRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    val playBtnRect = RectF()
    val startMissionBtnRect = RectF()
    val missionNodeCenters = Array(10) { PointF() }
    var missionNodeRadius = 36f

    val toggleSfxRect = RectF()
    val toggleBgmRect = RectF()
    val toggleHapticRect = RectF()
    val toggleAutoRunRect = RectF()
    val toggleUnlockRect = RectF()
    val toggleSizeRect = RectF()
    val toggleOpacityRect = RectF()

    val resumeBtnRect = RectF()
    val restartBtnRect = RectF()
    val menuBtnRect = RectF()
    val nextMissionBtnRect = RectF()

    private var globalTime = 0f

    fun renderGame(
        canvas: Canvas,
        w: Int,
        h: Int,
        dt: Float,
        engine: PhysicsEngine,
        controls: TouchControls,
        sound: SoundEngine
    ) {
        globalTime += dt
        val rows = engine.rows
        val tilePx = h.toFloat() / rows.toFloat()
        val viewWidthTiles = w.toFloat() / tilePx

        canvas.save()
        if (engine.shakeTimer > 0f) {
            val sx = ((Math.random().toFloat() - 0.5f) * 2f) * engine.shakeIntensity
            val sy = ((Math.random().toFloat() - 0.5f) * 2f) * engine.shakeIntensity
            canvas.translate(sx, sy)
        }

        val activeBiome = if (engine.inBonusRoom) Biome.CAVERN else engine.mission.biome
        drawBiomeBackground(canvas, w.toFloat(), h.toFloat(), activeBiome, engine.camX)

        canvas.save()
        canvas.translate(-engine.camX * tilePx, 0f)

        val startCol = (engine.camX - 1f).toInt().coerceAtLeast(0)
        val endCol = (engine.camX + viewWidthTiles + 2f).toInt().coerceAtMost(engine.cols - 1)

        if (!engine.inBonusRoom) {
            drawWorldScenery(canvas, startCol, endCol, engine, tilePx)
        }

        for (r in 0 until rows) {
            for (c in startCol..endCol) {
                val t = engine.grid[r][c]
                if (t == TileType.EMPTY) continue
                var bumpY = 0f
                for (b in engine.blockBumps) {
                    if (b.col == c && b.row == r) {
                        bumpY = -sin((b.timer / 0.18f) * Math.PI).toFloat() * tilePx * 0.28f
                    }
                }
                var shakeX = 0f
                if (t == TileType.CRUMBLE_BRIDGE) {
                    val cb = engine.crumbles.firstOrNull { it.col == c && it.row == r }
                    if (cb != null && cb.timer < 0.55f) {
                        shakeX = sin(globalTime * 55f) * 4f
                    }
                }
                drawTile(canvas, t, c * tilePx + shakeX, r * tilePx + bumpY, tilePx, engine)
            }
        }

        for (mp in engine.movingPlatforms) {
            drawMovingPlatform(canvas, mp.x * tilePx, mp.y * tilePx, mp.width * tilePx, mp.height * tilePx)
        }

        for (item in engine.items) {
            if (!item.active) continue
            drawItem(canvas, item, tilePx)
        }

        for (e in engine.enemies) {
            if (!e.alive) continue
            drawEnemy(canvas, e, tilePx)
        }

        for (fb in engine.fireballs) {
            if (!fb.active) continue
            val fx = fb.x * tilePx
            val fy = fb.y * tilePx
            paint.color = if (fb.fromPlayer) Color.rgb(255, 140, 0) else Color.rgb(255, 50, 20)
            canvas.drawCircle(fx, fy, tilePx * 0.24f, paint)
            paint.color = Color.YELLOW
            canvas.drawCircle(fx, fy, tilePx * 0.12f, paint)
        }

        drawPlayer(canvas, engine.player, tilePx)

        for (p in engine.particles) {
            val alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            paint.color = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            val px = p.x * tilePx
            val py = p.y * tilePx
            val sz = p.size * tilePx
            canvas.save()
            canvas.translate(px, py)
            canvas.rotate(Math.toDegrees(p.rotation.toDouble()).toFloat())
            if (p.shape == 1) {
                canvas.drawCircle(0f, 0f, sz * 0.5f, paint)
            } else {
                canvas.drawRect(-sz * 0.5f, -sz * 0.5f, sz * 0.5f, sz * 0.5f, paint)
            }
            canvas.restore()
        }

        for (ft in engine.floatingTexts) {
            val alpha = ((ft.life / ft.maxLife) * 255).toInt().coerceIn(0, 255)
            textPaint.color = Color.argb(alpha, Color.red(ft.color), Color.green(ft.color), Color.blue(ft.color))
            textPaint.textSize = tilePx * 0.42f * ft.scale
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
            canvas.drawText(ft.text, ft.x * tilePx, ft.y * tilePx, textPaint)
            textPaint.clearShadowLayer()
        }

        canvas.restore()
        canvas.restore()

        val boss = engine.enemies.firstOrNull { it.type == EnemyType.KING_REX && it.alive }
        if (boss != null && abs(boss.x - engine.player.x) < 18f) {
            drawBossHpBar(canvas, w.toFloat(), boss)
        }

        drawHud(canvas, w.toFloat(), h.toFloat(), engine)

        if (engine.introBannerTimer > 0f) {
            drawMissionIntroBanner(canvas, w.toFloat(), h.toFloat(), engine)
        }

        drawTouchControls(canvas, controls, sound, engine.player.powerState == PowerState.FIRE)
    }

    private fun drawWorldScenery(canvas: Canvas, startCol: Int, endCol: Int, engine: PhysicsEngine, s: Float) {
        val biome = engine.mission.biome
        for (c in startCol..endCol) {
            if (c % 9 == 4 && engine.grid[12][c] != TileType.LAVA && engine.grid[12][c] != TileType.EMPTY) {
                val x = c * s
                val groundY = 12 * s
                when (biome) {
                    Biome.PLAINS, Biome.SUNSET -> {
                        paint.color = if (biome == Biome.PLAINS) Color.rgb(34, 155, 62) else Color.rgb(168, 92, 42)
                        canvas.drawCircle(x + s * 0.5f, groundY - s * 0.25f, s * 0.42f, paint)
                        canvas.drawCircle(x + s * 0.1f, groundY - s * 0.15f, s * 0.30f, paint)
                        canvas.drawCircle(x + s * 0.9f, groundY - s * 0.15f, s * 0.30f, paint)
                    }
                    Biome.CAVERN -> {
                        paint.color = Color.argb(140, 56, 189, 248)
                        path.reset()
                        path.moveTo(x + s * 0.2f, groundY)
                        path.lineTo(x + s * 0.45f, groundY - s * 0.75f)
                        path.lineTo(x + s * 0.7f, groundY)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                    Biome.ICE -> {
                        paint.color = Color.rgb(30, 105, 85)
                        path.reset()
                        path.moveTo(x + s * 0.5f, groundY - s * 1.2f)
                        path.lineTo(x + s * 0.05f, groundY)
                        path.lineTo(x + s * 0.95f, groundY)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                    Biome.CASTLE, Biome.VOLCANO -> {
                        paint.color = Color.rgb(90, 60, 40)
                        canvas.drawRect(x + s * 0.44f, groundY - s * 0.7f, x + s * 0.56f, groundY, paint)
                        val flick = sin(globalTime * 16f + c) * 3f
                        paint.color = Color.rgb(255, 140, 20)
                        canvas.drawCircle(x + s * 0.5f + flick * 0.3f, groundY - s * 0.8f, s * 0.2f, paint)
                    }
                    else -> {}
                }
            }
        }

        if (engine.flagCol in (startCol - 5)..(endCol + 5)) {
            val cx = (engine.flagCol + 2) * s
            val cy = 12 * s
            paint.color = Color.rgb(120, 128, 142)
            canvas.drawRect(cx, cy - s * 2.6f, cx + s * 3.2f, cy, paint)
            paint.color = Color.rgb(88, 95, 108)
            for (i in 0..3) {
                canvas.drawRect(cx + i * s * 0.85f, cy - s * 3.0f, cx + i * s * 0.85f + s * 0.5f, cy - s * 2.6f, paint)
            }
            paint.color = Color.rgb(30, 35, 45)
            canvas.drawArc(RectF(cx + s * 1.0f, cy - s * 1.5f, cx + s * 2.2f, cy + s * 0.4f), 180f, 180f, true, paint)
        }
    }

    private fun drawBiomeBackground(canvas: Canvas, w: Float, h: Float, biome: Biome, camX: Float) {
        val (topColor, botColor) = when (biome) {
            Biome.PLAINS -> Color.rgb(56, 152, 255) to Color.rgb(165, 225, 255)
            Biome.CAVERN -> Color.rgb(12, 16, 42) to Color.rgb(30, 41, 82)
            Biome.SKY -> Color.rgb(90, 185, 255) to Color.rgb(225, 245, 255)
            Biome.SUNSET -> Color.rgb(120, 40, 95) to Color.rgb(255, 145, 77)
            Biome.ICE -> Color.rgb(30, 80, 140) to Color.rgb(190, 232, 255)
            Biome.NIGHT -> Color.rgb(10, 14, 38) to Color.rgb(38, 46, 98)
            Biome.VOLCANO -> Color.rgb(45, 12, 14) to Color.rgb(165, 42, 22)
            Biome.CASTLE -> Color.rgb(20, 16, 26) to Color.rgb(64, 24, 34)
        }
        paint.shader = LinearGradient(0f, 0f, 0f, h, topColor, botColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        when (biome) {
            Biome.PLAINS, Biome.SKY -> {
                paint.color = Color.argb(90, 255, 245, 160)
                canvas.drawCircle(w * 0.82f, h * 0.22f, h * 0.14f, paint)
                paint.color = Color.rgb(255, 240, 130)
                canvas.drawCircle(w * 0.82f, h * 0.22f, h * 0.08f, paint)
            }
            Biome.SUNSET -> {
                paint.color = Color.argb(120, 255, 180, 70)
                canvas.drawCircle(w * 0.72f, h * 0.45f, h * 0.18f, paint)
                paint.color = Color.rgb(255, 210, 110)
                canvas.drawCircle(w * 0.72f, h * 0.45f, h * 0.11f, paint)
            }
            Biome.NIGHT, Biome.CAVERN -> {
                paint.color = Color.argb(190, 255, 255, 255)
                for (i in 0 until 24) {
                    val sx = ((i * 197 + 43) % w.toInt()).toFloat()
                    val sy = ((i * 113 + 29) % (h * 0.6f).toInt()).toFloat()
                    val r = if (i % 3 == 0) 3.5f else 2f
                    canvas.drawCircle(sx, sy, r, paint)
                }
                if (biome == Biome.NIGHT) {
                    paint.color = Color.rgb(245, 242, 210)
                    canvas.drawCircle(w * 0.84f, h * 0.2f, h * 0.075f, paint)
                }
            }
            else -> {}
        }

        if (biome != Biome.CAVERN && biome != Biome.CASTLE) {
            paint.color = when (biome) {
                Biome.SUNSET -> Color.argb(160, 255, 210, 190)
                Biome.VOLCANO -> Color.argb(110, 90, 60, 60)
                else -> Color.argb(215, 255, 255, 255)
            }
            val cloudSpan = w * 1.4f
            for (i in 0 until 5) {
                val cx = ((i * 340f - camX * 14f - globalTime * 10f) % cloudSpan + cloudSpan) % cloudSpan - 120f
                val cy = h * (0.14f + (i % 3) * 0.11f)
                canvas.drawCircle(cx, cy, h * 0.055f, paint)
                canvas.drawCircle(cx + h * 0.05f, cy - h * 0.02f, h * 0.068f, paint)
                canvas.drawCircle(cx + h * 0.11f, cy, h * 0.052f, paint)
            }
        }

        val hillColor1 = when (biome) {
            Biome.PLAINS -> Color.rgb(72, 168, 92)
            Biome.CAVERN -> Color.rgb(22, 30, 66)
            Biome.SKY -> Color.rgb(185, 225, 255)
            Biome.SUNSET -> Color.rgb(138, 62, 88)
            Biome.ICE -> Color.rgb(115, 175, 225)
            Biome.NIGHT -> Color.rgb(24, 32, 70)
            Biome.VOLCANO -> Color.rgb(72, 22, 22)
            Biome.CASTLE -> Color.rgb(34, 24, 36)
        }
        paint.color = hillColor1
        val hillWidth = 360f
        val offset1 = (camX * 22f) % hillWidth
        var hx = -offset1 - hillWidth
        while (hx < w + hillWidth) {
            path.reset()
            path.moveTo(hx, h)
            path.quadTo(hx + hillWidth * 0.5f, h * 0.48f, hx + hillWidth, h)
            path.close()
            canvas.drawPath(path, paint)
            hx += hillWidth
        }

        val hillColor2 = when (biome) {
            Biome.PLAINS -> Color.rgb(52, 145, 72)
            Biome.CAVERN -> Color.rgb(16, 22, 50)
            Biome.SKY -> Color.rgb(225, 242, 255)
            Biome.SUNSET -> Color.rgb(104, 42, 70)
            Biome.ICE -> Color.rgb(145, 200, 240)
            Biome.NIGHT -> Color.rgb(18, 22, 52)
            Biome.VOLCANO -> Color.rgb(52, 15, 16)
            Biome.CASTLE -> Color.rgb(24, 18, 28)
        }
        paint.color = hillColor2
        val hillW2 = 280f
        val offset2 = (camX * 42f) % hillW2
        hx = -offset2 - hillW2
        while (hx < w + hillW2) {
            path.reset()
            path.moveTo(hx, h)
            path.quadTo(hx + hillW2 * 0.5f, h * 0.65f, hx + hillW2, h)
            path.close()
            canvas.drawPath(path, paint)
            hx += hillW2
        }

        if (biome == Biome.ICE) {
            paint.color = Color.argb(180, 255, 255, 255)
            for (i in 0 until 20) {
                val sx = ((i * 151 + globalTime * 30f) % w)
                val sy = ((i * 97 + globalTime * 65f) % h)
                canvas.drawCircle(sx, sy, 3.5f, paint)
            }
        } else if (biome == Biome.VOLCANO || biome == Biome.CASTLE) {
            paint.color = Color.argb(170, 255, 140, 40)
            for (i in 0 until 16) {
                val sx = ((i * 173 + sin(globalTime + i) * 20f) % w + w) % w
                val sy = h - ((i * 89 + globalTime * 80f) % h)
                canvas.drawCircle(sx, sy, 3f, paint)
            }
        }
    }

    private fun drawTile(canvas: Canvas, t: TileType, x: Float, y: Float, s: Float, engine: PhysicsEngine) {
        val biome = engine.mission.biome
        when (t) {
            TileType.GROUND -> {
                val body = when (biome) {
                    Biome.CAVERN -> Color.rgb(48, 62, 110)
                    Biome.CASTLE, Biome.VOLCANO -> Color.rgb(66, 52, 58)
                    Biome.NIGHT -> Color.rgb(50, 56, 86)
                    else -> Color.rgb(142, 78, 38)
                }
                val top = when (biome) {
                    Biome.CAVERN -> Color.rgb(72, 115, 185)
                    Biome.SUNSET -> Color.rgb(218, 112, 52)
                    Biome.CASTLE, Biome.VOLCANO -> Color.rgb(108, 86, 94)
                    Biome.NIGHT -> Color.rgb(88, 128, 175)
                    else -> Color.rgb(42, 182, 74)
                }
                paint.color = body
                canvas.drawRect(x, y, x + s + 1f, y + s + 1f, paint)
                paint.color = top
                canvas.drawRect(x, y, x + s + 1f, y + s * 0.24f, paint)
            }

            TileType.ICE_BLOCK -> {
                paint.color = Color.rgb(135, 212, 250)
                canvas.drawRect(x, y, x + s + 1f, y + s + 1f, paint)
                paint.color = Color.rgb(215, 245, 255)
                canvas.drawRect(x, y, x + s + 1f, y + s * 0.22f, paint)
            }

            TileType.BRICK -> {
                paint.color = if (biome == Biome.CAVERN) Color.rgb(74, 96, 156) else Color.rgb(196, 92, 46)
                rect.set(x + 1f, y + 1f, x + s - 1f, y + s - 1f)
                canvas.drawRoundRect(rect, 4f, 4f, paint)
                paint.color = Color.argb(90, 0, 0, 0)
                canvas.drawLine(x, y + s * 0.5f, x + s, y + s * 0.5f, paint)
                canvas.drawLine(x + s * 0.5f, y, x + s * 0.5f, y + s * 0.5f, paint)
            }

            TileType.QUESTION_COIN, TileType.QUESTION_POWERUP, TileType.QUESTION_STAR, TileType.QUESTION_1UP -> {
                val pulse = (sin(globalTime * 5f) * 18f).toInt()
                paint.color = Color.rgb((238 + pulse).coerceIn(0, 255), (182 + pulse).coerceIn(0, 255), 32)
                rect.set(x + 1.5f, y + 1.5f, x + s - 1.5f, y + s - 1.5f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                paint.color = Color.rgb(160, 95, 12)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                paint.style = Paint.Style.FILL

                textPaint.color = Color.WHITE
                textPaint.textSize = s * 0.65f
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("?", x + s * 0.5f, y + s * 0.74f, textPaint)
            }

            TileType.USED_BLOCK, TileType.HARD_BLOCK -> {
                paint.color = if (t == TileType.USED_BLOCK) Color.rgb(112, 92, 76) else Color.rgb(100, 108, 122)
                rect.set(x + 1f, y + 1f, x + s - 1f, y + s - 1f)
                canvas.drawRoundRect(rect, 5f, 5f, paint)
                paint.color = Color.argb(60, 255, 255, 255)
                canvas.drawRect(x + 4f, y + 4f, x + s - 4f, y + 8f, paint)
            }

            TileType.CANNON -> {
                paint.color = Color.rgb(30, 36, 46)
                rect.set(x + 2f, y + 2f, x + s - 2f, y + s - 2f)
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.color = Color.rgb(100, 116, 139)
                canvas.drawCircle(x + s * 0.5f, y + s * 0.5f, s * 0.25f, paint)
            }

            TileType.BRIDGE -> {
                paint.color = Color.rgb(165, 104, 52)
                rect.set(x, y, x + s + 1f, y + s * 0.42f)
                canvas.drawRect(rect, paint)
                paint.color = Color.rgb(212, 148, 84)
                canvas.drawRect(x, y, x + s + 1f, y + s * 0.14f, paint)
            }

            TileType.CRUMBLE_BRIDGE -> {
                paint.color = Color.rgb(194, 120, 62)
                rect.set(x + 2f, y, x + s - 2f, y + s * 0.42f)
                canvas.drawRoundRect(rect, 4f, 4f, paint)
                paint.color = Color.rgb(110, 58, 24)
                canvas.drawLine(x + s * 0.35f, y, x + s * 0.55f, y + s * 0.42f, paint)
            }

            TileType.LAVA -> {
                val wave = sin(globalTime * 4f + x * 0.04f) * s * 0.08f
                paint.color = Color.rgb(235, 65, 20)
                canvas.drawRect(x, y + wave + s * 0.1f, x + s + 1f, y + s + 2f, paint)
                paint.color = Color.rgb(255, 185, 40)
                canvas.drawRect(x, y + wave + s * 0.1f, x + s + 1f, y + wave + s * 0.25f, paint)
            }

            TileType.SPRING -> {
                paint.color = Color.rgb(70, 80, 95)
                canvas.drawRect(x + s * 0.15f, y + s * 0.72f, x + s * 0.85f, y + s, paint)
                paint.color = Color.rgb(240, 60, 60)
                rect.set(x + s * 0.08f, y + s * 0.42f, x + s * 0.92f, y + s * 0.65f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
            }

            TileType.CHECKPOINT -> {
                paint.color = Color.rgb(210, 218, 228)
                canvas.drawRect(x + s * 0.45f, y - s * 0.5f, x + s * 0.55f, y + s, paint)
                paint.color = if (engine.checkpointReached) Color.rgb(34, 211, 238) else Color.rgb(148, 163, 184)
                path.reset()
                path.moveTo(x + s * 0.55f, y - s * 0.48f)
                path.lineTo(x + s * 1.15f, y - s * 0.24f + sin(globalTime * 5f) * 3f)
                path.lineTo(x + s * 0.55f, y)
                path.close()
                canvas.drawPath(path, paint)
            }

            TileType.PIPE_TL, TileType.PIPE_TR, TileType.PIPE_BL, TileType.PIPE_BR,
            TileType.WARP_PIPE_TL, TileType.WARP_PIPE_TR -> {
                val isWarp = t == TileType.WARP_PIPE_TL || t == TileType.WARP_PIPE_TR
                val isTop = t == TileType.PIPE_TL || t == TileType.PIPE_TR || isWarp
                val isLeft = t == TileType.PIPE_TL || t == TileType.PIPE_BL || t == TileType.WARP_PIPE_TL
                val px = if (isTop) x else x + (if (isLeft) s * 0.08f else 0f)
                val pw = if (isTop) s else s * 0.92f
                paint.color = if (isWarp) Color.rgb(16, 185, 129) else Color.rgb(34, 178, 68)
                canvas.drawRect(px, y, px + pw + 1f, y + s + 1f, paint)
                if (isLeft) {
                    paint.color = if (isWarp) Color.rgb(110, 245, 200) else Color.rgb(110, 232, 134)
                    canvas.drawRect(px + s * 0.16f, y, px + s * 0.34f, y + s + 1f, paint)
                }
                if (isTop) {
                    paint.color = Color.rgb(22, 120, 44)
                    canvas.drawRect(px, y + s * 0.85f, px + pw + 1f, y + s, paint)
                }
                if (t == TileType.WARP_PIPE_TL) {
                    val bob = sin(globalTime * 5f) * 4f
                    textPaint.color = Color.rgb(250, 204, 21)
                    textPaint.textSize = s * 0.45f
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("▼", x + s, y - s * 0.25f + bob, textPaint)
                }
            }

            TileType.FLAG_POLE -> {
                paint.color = Color.rgb(230, 235, 240)
                canvas.drawRect(x + s * 0.44f, y, x + s * 0.56f, y + s + 1f, paint)
            }

            TileType.FLAG_TOP -> {
                paint.color = Color.rgb(230, 235, 240)
                canvas.drawRect(x + s * 0.44f, y + s * 0.25f, x + s * 0.56f, y + s + 1f, paint)
                paint.color = Color.rgb(255, 215, 0)
                canvas.drawCircle(x + s * 0.5f, y + s * 0.22f, s * 0.2f, paint)
                paint.color = Color.rgb(239, 68, 68)
                path.reset()
                path.moveTo(x + s * 0.44f, y + s * 0.32f)
                path.lineTo(x - s * 0.65f, y + s * 0.55f + sin(globalTime * 6f) * 5f)
                path.lineTo(x + s * 0.44f, y + s * 0.85f)
                path.close()
                canvas.drawPath(path, paint)
            }

            TileType.AXE -> {
                val bob = sin(globalTime * 5f) * 4f
                paint.color = Color.rgb(160, 100, 45)
                canvas.drawRect(x + s * 0.44f, y + s * 0.25f + bob, x + s * 0.56f, y + s * 0.95f + bob, paint)
                paint.color = Color.rgb(230, 238, 250)
                path.reset()
                path.moveTo(x + s * 0.5f, y + s * 0.15f + bob)
                path.lineTo(x + s * 0.92f, y + s * 0.32f + bob)
                path.lineTo(x + s * 0.5f, y + s * 0.55f + bob)
                path.close()
                canvas.drawPath(path, paint)
            }

            else -> {}
        }
    }

    private fun drawMovingPlatform(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        paint.color = Color.rgb(245, 158, 11)
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, h * 0.4f, h * 0.4f, paint)
        paint.color = Color.rgb(254, 215, 102)
        rect.set(x + 4f, y + 3f, x + w - 4f, y + h * 0.42f)
        canvas.drawRoundRect(rect, h * 0.25f, h * 0.25f, paint)
    }

    private fun drawItem(canvas: Canvas, item: Item, s: Float) {
        val x = item.x * s
        val y = item.y * s
        when (item.type) {
            ItemType.WORLD_COIN, ItemType.POPUP_COIN -> {
                val scaleX = abs(cos(item.animTimer * 5f)).coerceAtLeast(0.25f)
                val cx = x + s * 0.35f
                val cy = y + s * 0.35f
                val r = s * 0.3f
                paint.color = Color.rgb(255, 204, 0)
                rect.set(cx - r * scaleX, cy - r, cx + r * scaleX, cy + r)
                canvas.drawOval(rect, paint)
                paint.color = Color.rgb(255, 240, 140)
                rect.set(cx - r * scaleX * 0.45f, cy - r * 0.55f, cx + r * scaleX * 0.45f, cy + r * 0.55f)
                canvas.drawOval(rect, paint)
            }

            ItemType.STAR_COIN -> {
                val cx = x + s * 0.45f
                val cy = y + s * 0.45f + sin(item.animTimer * 4f) * 4f
                val r = s * 0.42f
                paint.color = Color.rgb(255, 195, 0)
                canvas.drawCircle(cx, cy, r, paint)
                paint.color = Color.rgb(255, 242, 120)
                canvas.drawCircle(cx, cy, r * 0.72f, paint)
                textPaint.color = Color.rgb(180, 110, 0)
                textPaint.textSize = s * 0.48f
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("★", cx, cy + s * 0.16f, textPaint)
            }

            ItemType.MUSHROOM, ItemType.ONE_UP -> {
                val capColor = if (item.type == ItemType.MUSHROOM) Color.rgb(235, 52, 52) else Color.rgb(34, 197, 94)
                paint.color = Color.rgb(254, 232, 198)
                rect.set(x + s * 0.2f, y + s * 0.42f, x + s * 0.65f, y + s * 0.85f)
                canvas.drawRoundRect(rect, 6f, 6f, paint)
                paint.color = capColor
                rect.set(x + s * 0.04f, y + s * 0.05f, x + s * 0.81f, y + s * 0.55f)
                canvas.drawArc(rect, 180f, 180f, true, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x + s * 0.42f, y + s * 0.25f, s * 0.1f, paint)
            }

            ItemType.FIRE_FLOWER -> {
                paint.color = Color.rgb(34, 197, 94)
                canvas.drawRect(x + s * 0.36f, y + s * 0.45f, x + s * 0.48f, y + s * 0.85f, paint)
                paint.color = Color.rgb(255, 95, 20)
                canvas.drawCircle(x + s * 0.42f, y + s * 0.32f, s * 0.28f, paint)
                paint.color = Color.YELLOW
                canvas.drawCircle(x + s * 0.42f, y + s * 0.32f, s * 0.14f, paint)
            }

            ItemType.STARMAN -> {
                val cx = x + s * 0.42f
                val cy = y + s * 0.42f
                paint.color = Color.HSVToColor(floatArrayOf((globalTime * 360f) % 360f, 0.85f, 1f))
                canvas.drawCircle(cx, cy, s * 0.36f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, s * 0.18f, paint)
            }
        }
    }

    private fun drawEnemy(canvas: Canvas, e: Enemy, s: Float) {
        val x = e.x * s
        val y = e.y * s
        val w = e.width * s
        val h = e.height * s

        when (e.type) {
            EnemyType.SHROOMLING, EnemyType.WINGED_SHROOM -> {
                if (e.squashed) {
                    paint.color = Color.rgb(148, 76, 36)
                    rect.set(x, y + h * 0.65f, x + w, y + h)
                    canvas.drawRoundRect(rect, 6f, 6f, paint)
                    return
                }
                if (e.type == EnemyType.WINGED_SHROOM) {
                    val flap = sin(e.animTimer * 14f) * h * 0.15f
                    paint.color = Color.WHITE
                    canvas.drawOval(x - w * 0.2f, y + h * 0.1f + flap, x + w * 0.2f, y + h * 0.5f + flap, paint)
                    canvas.drawOval(x + w * 0.8f, y + h * 0.1f + flap, x + w * 1.2f, y + h * 0.5f + flap, paint)
                }
                paint.color = Color.rgb(156, 78, 36)
                rect.set(x, y, x + w, y + h * 0.68f)
                canvas.drawRoundRect(rect, w * 0.4f, w * 0.4f, paint)
                paint.color = Color.rgb(245, 212, 175)
                canvas.drawRect(x + w * 0.25f, y + h * 0.55f, x + w * 0.75f, y + h * 0.86f, paint)
                val step = sin(e.animTimer * 10f) * w * 0.08f
                paint.color = Color.rgb(45, 30, 20)
                canvas.drawOval(x + w * 0.1f + step, y + h * 0.78f, x + w * 0.45f + step, y + h, paint)
                canvas.drawOval(x + w * 0.55f - step, y + h * 0.78f, x + w * 0.9f - step, y + h, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x + w * 0.34f, y + h * 0.38f, w * 0.1f, paint)
                canvas.drawCircle(x + w * 0.66f, y + h * 0.38f, w * 0.1f, paint)
                paint.color = Color.BLACK
                canvas.drawCircle(x + w * 0.34f, y + h * 0.38f, w * 0.05f, paint)
                canvas.drawCircle(x + w * 0.66f, y + h * 0.38f, w * 0.05f, paint)
            }

            EnemyType.SHELLBACK -> {
                paint.color = Color.rgb(34, 175, 76)
                rect.set(x + w * 0.08f, y + h * 0.25f, x + w * 0.92f, y + h * 0.88f)
                canvas.drawRoundRect(rect, w * 0.35f, w * 0.35f, paint)
                paint.color = Color.rgb(245, 240, 200)
                canvas.drawRect(x + w * 0.14f, y + h * 0.72f, x + w * 0.86f, y + h * 0.86f, paint)
                if (!e.inShell) {
                    paint.color = Color.rgb(250, 215, 115)
                    val hx = if (e.facingRight) x + w * 0.78f else x + w * 0.22f
                    canvas.drawCircle(hx, y + h * 0.25f, w * 0.24f, paint)
                    paint.color = Color.BLACK
                    canvas.drawCircle(hx, y + h * 0.22f, w * 0.06f, paint)
                }
            }

            EnemyType.SPINY -> {
                paint.color = Color.rgb(220, 38, 38)
                rect.set(x + w * 0.08f, y + h * 0.3f, x + w * 0.92f, y + h * 0.9f)
                canvas.drawRoundRect(rect, w * 0.35f, w * 0.35f, paint)
                // White spikes on top
                paint.color = Color.WHITE
                for (i in 0..2) {
                    val sx = x + w * (0.25f + i * 0.25f)
                    path.reset()
                    path.moveTo(sx - w * 0.1f, y + h * 0.32f)
                    path.lineTo(sx, y + h * 0.06f)
                    path.lineTo(sx + w * 0.1f, y + h * 0.32f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }

            EnemyType.BULLET -> {
                paint.color = Color.rgb(25, 30, 40)
                rect.set(x, y, x + w, y + h)
                canvas.drawRoundRect(rect, h * 0.48f, h * 0.48f, paint)
                paint.color = Color.RED
                val eyeX = if (e.facingRight) x + w * 0.72f else x + w * 0.28f
                canvas.drawCircle(eyeX, y + h * 0.42f, h * 0.14f, paint)
            }

            EnemyType.PIPE_CHOMPER -> {
                paint.color = Color.rgb(34, 165, 68)
                canvas.drawRect(x + w * 0.38f, y + h * 0.45f, x + w * 0.62f, y + h, paint)
                paint.color = Color.rgb(228, 42, 42)
                canvas.drawCircle(x + w * 0.5f, y + h * 0.35f, w * 0.38f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x + w * 0.35f, y + h * 0.28f, w * 0.08f, paint)
                canvas.drawCircle(x + w * 0.65f, y + h * 0.28f, w * 0.08f, paint)
            }

            EnemyType.KING_REX -> {
                paint.color = Color.rgb(34, 130, 56)
                rect.set(x + w * 0.1f, y + h * 0.18f, x + w * 0.9f, y + h * 0.92f)
                canvas.drawRoundRect(rect, w * 0.25f, w * 0.25f, paint)
                paint.color = Color.rgb(250, 204, 21)
                canvas.drawRect(x + w * 0.3f, y + h * 0.05f, x + w * 0.7f, y + h * 0.16f, paint)
                paint.color = Color.rgb(245, 240, 210)
                canvas.drawCircle(x + w * 0.22f, y + h * 0.14f, w * 0.09f, paint)
                canvas.drawCircle(x + w * 0.78f, y + h * 0.14f, w * 0.09f, paint)
                paint.color = Color.RED
                val eyeX = if (e.facingRight) x + w * 0.65f else x + w * 0.35f
                canvas.drawCircle(eyeX, y + h * 0.38f, w * 0.1f, paint)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas, p: Player, s: Float) {
        if (p.invincibleTimer > 0f && ((p.invincibleTimer * 18f).toInt() % 2 == 0)) return

        val px = p.x * s
        val py = p.y * s
        val pw = p.width * s
        val ph = p.height * s

        if (p.onGround) {
            paint.color = Color.argb(70, 0, 0, 0)
            canvas.drawOval(px + pw * 0.1f, py + ph - 5f, px + pw * 0.9f, py + ph + 5f, paint)
        }

        canvas.save()
        canvas.translate(px + pw * 0.5f, py + ph)
        canvas.scale(p.squashX * (if (p.facingRight) 1f else -1f), p.squashY)

        val shirtColor = when {
            p.starTimer > 0f -> Color.HSVToColor(floatArrayOf((globalTime * 480f) % 360f, 0.9f, 1f))
            p.powerState == PowerState.FIRE -> Color.WHITE
            else -> Color.rgb(235, 48, 48)
        }
        val overallColor = when {
            p.powerState == PowerState.FIRE -> Color.rgb(220, 42, 42)
            else -> Color.rgb(37, 99, 235)
        }

        val legSwing = if (p.onGround && abs(p.vx) > 0.3f) sin(p.runAnimTimer) * ph * 0.12f else 0f
        paint.color = overallColor
        canvas.drawRoundRect(RectF(-pw * 0.34f + legSwing, -ph * 0.35f, -pw * 0.05f + legSwing, 0f), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(pw * 0.05f - legSwing, -ph * 0.35f, pw * 0.34f - legSwing, 0f), 6f, 6f, paint)

        paint.color = shirtColor
        canvas.drawRoundRect(RectF(-pw * 0.4f, -ph * 0.62f, pw * 0.4f, -ph * 0.25f), 8f, 8f, paint)
        paint.color = overallColor
        canvas.drawRoundRect(RectF(-pw * 0.35f, -ph * 0.46f, pw * 0.35f, -ph * 0.22f), 6f, 6f, paint)
        paint.color = Color.rgb(250, 204, 21)
        canvas.drawCircle(-pw * 0.16f, -ph * 0.40f, pw * 0.06f, paint)
        canvas.drawCircle(pw * 0.16f, -ph * 0.40f, pw * 0.06f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(pw * 0.38f - legSwing * 0.4f, -ph * 0.38f, pw * 0.13f, paint)

        paint.color = Color.rgb(253, 198, 148)
        canvas.drawCircle(0f, -ph * 0.72f, pw * 0.38f, paint)

        paint.color = shirtColor
        canvas.drawArc(RectF(-pw * 0.44f, -ph * 1.02f, pw * 0.44f, -ph * 0.62f), 180f, 180f, true, paint)
        canvas.drawRect(-pw * 0.42f, -ph * 0.84f, pw * 0.56f, -ph * 0.76f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(pw * 0.08f, -ph * 0.89f, pw * 0.12f, paint)

        paint.color = Color.BLACK
        canvas.drawCircle(pw * 0.16f, -ph * 0.71f, pw * 0.07f, paint)
        paint.color = Color.rgb(88, 42, 16)
        canvas.drawRoundRect(RectF(pw * 0.02f, -ph * 0.64f, pw * 0.36f, -ph * 0.56f), 4f, 4f, paint)

        canvas.restore()
    }

    private fun drawBossHpBar(canvas: Canvas, w: Float, boss: Enemy) {
        val bw = w * 0.35f
        val bx = (w - bw) * 0.5f
        val by = 78f
        paint.color = Color.argb(180, 20, 20, 30)
        canvas.drawRoundRect(RectF(bx, by, bx + bw, by + 22f), 11f, 11f, paint)
        val frac = (boss.hp.toFloat() / boss.maxHp.toFloat()).coerceIn(0f, 1f)
        paint.color = Color.rgb(239, 68, 68)
        canvas.drawRoundRect(RectF(bx + 3f, by + 3f, bx + 3f + (bw - 6f) * frac, by + 19f), 8f, 8f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 20f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("KING REX", w * 0.5f, by - 6f, textPaint)
    }

    private fun drawMissionIntroBanner(canvas: Canvas, w: Float, h: Float, engine: PhysicsEngine) {
        val alpha = ((engine.introBannerTimer / 0.6f).coerceIn(0f, 1f) * 230).toInt()
        paint.color = Color.argb(alpha, 15, 23, 42)
        val bw = w * 0.48f
        val bx = (w - bw) * 0.5f
        canvas.drawRoundRect(RectF(bx, h * 0.16f, bx + bw, h * 0.33f), 24f, 24f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.argb(alpha, 250, 204, 21)
        textPaint.textSize = h * 0.048f
        canvas.drawText("MISSION ${engine.mission.id}: ${engine.mission.name.uppercase()}", w * 0.5f, h * 0.23f, textPaint)
        textPaint.color = Color.argb(alpha, 226, 232, 240)
        textPaint.textSize = h * 0.034f
        canvas.drawText("Bonus: ${engine.mission.bonusObjective.description}", w * 0.5f, h * 0.29f, textPaint)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float, engine: PhysicsEngine) {
        val p = engine.player
        paint.color = Color.argb(135, 15, 23, 42)
        canvas.drawRoundRect(RectF(18f, 14f, w * 0.72f, 64f), 20f, 20f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = (h * 0.036f).coerceIn(20f, 34f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.LEFT

        val starIcons = p.starCoinsCollected.joinToString("") { if (it) "★" else "☆" }
        val hudText = "♥ ${p.lives}   🪙 ${p.coins}   $starIcons   SCORE ${p.score}   ⏱ ${engine.timeLeft.toInt()}s"
        canvas.drawText(hudText, 36f, 48f, textPaint)
    }

    private fun drawTouchControls(canvas: Canvas, controls: TouchControls, sound: SoundEngine, isFire: Boolean) {
        val op = controls.controlOpacity
        val baseA = (110 * op).toInt().coerceIn(30, 220)
        val pressA = (220 * op).toInt().coerceIn(80, 255)
        val strokeA = (190 * op).toInt().coerceIn(50, 255)

        paint.color = if (controls.left) Color.argb(pressA, 255, 255, 255) else Color.argb(baseA, 15, 23, 42)
        canvas.drawRoundRect(controls.leftRect, 24f, 24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(strokeA, 255, 255, 255)
        canvas.drawRoundRect(controls.leftRect, 24f, 24f, paint)
        paint.style = Paint.Style.FILL
        textPaint.color = if (controls.left) Color.BLACK else Color.argb(pressA, 255, 255, 255)
        textPaint.textSize = controls.leftRect.height() * 0.48f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("◀", controls.leftRect.centerX(), controls.leftRect.centerY() + textPaint.textSize * 0.34f, textPaint)

        paint.color = if (controls.right) Color.argb(pressA, 255, 255, 255) else Color.argb(baseA, 15, 23, 42)
        canvas.drawRoundRect(controls.rightRect, 24f, 24f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(strokeA, 255, 255, 255)
        canvas.drawRoundRect(controls.rightRect, 24f, 24f, paint)
        paint.style = Paint.Style.FILL
        textPaint.color = if (controls.right) Color.BLACK else Color.argb(pressA, 255, 255, 255)
        canvas.drawText("▶", controls.rightRect.centerX(), controls.rightRect.centerY() + textPaint.textSize * 0.34f, textPaint)

        paint.color = if (controls.down) Color.argb(pressA, 255, 255, 255) else Color.argb(baseA, 15, 23, 42)
        canvas.drawRoundRect(controls.downRect, 16f, 16f, paint)
        textPaint.color = if (controls.down) Color.BLACK else Color.argb(pressA, 255, 255, 255)
        textPaint.textSize = controls.downRect.height() * 0.5f
        canvas.drawText("▼", controls.downRect.centerX(), controls.downRect.centerY() + textPaint.textSize * 0.34f, textPaint)

        paint.color = if (controls.jumpHeld) Color.argb(pressA, 34, 197, 94) else Color.argb((155 * op).toInt(), 22, 163, 74)
        canvas.drawCircle(controls.jumpCenter.x, controls.jumpCenter.y, controls.jumpRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.argb(strokeA, 255, 255, 255)
        canvas.drawCircle(controls.jumpCenter.x, controls.jumpCenter.y, controls.jumpRadius, paint)
        paint.style = Paint.Style.FILL
        textPaint.color = Color.argb(pressA, 255, 255, 255)
        textPaint.textSize = controls.jumpRadius * 0.46f
        canvas.drawText("JUMP", controls.jumpCenter.x, controls.jumpCenter.y + textPaint.textSize * 0.34f, textPaint)

        val fireBase = if (isFire) Color.argb((160 * op).toInt(), 249, 115, 22) else Color.argb((150 * op).toInt(), 239, 68, 68)
        val firePress = if (isFire) Color.argb(pressA, 251, 146, 60) else Color.argb(pressA, 248, 113, 113)
        paint.color = if (controls.runHeld && !controls.autoRun) firePress else fireBase
        canvas.drawCircle(controls.fireCenter.x, controls.fireCenter.y, controls.fireRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(strokeA, 255, 255, 255)
        canvas.drawCircle(controls.fireCenter.x, controls.fireCenter.y, controls.fireRadius, paint)
        paint.style = Paint.Style.FILL
        textPaint.textSize = controls.fireRadius * 0.44f
        canvas.drawText(if (isFire) "FIRE" else "RUN", controls.fireCenter.x, controls.fireCenter.y + textPaint.textSize * 0.34f, textPaint)

        paint.color = Color.argb(125, 15, 23, 42)
        canvas.drawRoundRect(controls.pauseRect, 14f, 14f, paint)
        canvas.drawRoundRect(controls.muteRect, 14f, 14f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = controls.pauseRect.height() * 0.48f
        canvas.drawText("❚❚", controls.pauseRect.centerX(), controls.pauseRect.centerY() + textPaint.textSize * 0.34f, textPaint)
        canvas.drawText(if (sound.sfxEnabled) "🔊" else "🔇", controls.muteRect.centerX(), controls.muteRect.centerY() + textPaint.textSize * 0.34f, textPaint)
    }

    fun renderTitle(canvas: Canvas, w: Int, h: Int, dt: Float, save: SaveManager) {
        globalTime += dt
        drawBiomeBackground(canvas, w.toFloat(), h.toFloat(), Biome.PLAINS, globalTime * 1.4f)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.setShadowLayer(8f, 0f, 6f, Color.argb(160, 0, 0, 0))
        textPaint.color = Color.rgb(255, 224, 66)
        textPaint.textSize = h * 0.11f
        canvas.drawText("SUPER PLUMBER", w * 0.5f, h * 0.30f, textPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = h * 0.075f
        canvas.drawText("ODYSSEY", w * 0.5f, h * 0.41f, textPaint)
        textPaint.clearShadowLayer()

        val totalStars = save.getTotalStars(LevelRepository.missions.size)
        textPaint.textSize = h * 0.042f
        textPaint.color = Color.rgb(254, 240, 138)
        canvas.drawText("★ $totalStars / ${LevelRepository.missions.size * 3} Stars Collected   •   10 Missions", w * 0.5f, h * 0.52f, textPaint)

        val bw = w * 0.28f
        val bh = h * 0.14f
        playBtnRect.set(w * 0.5f - bw * 0.5f, h * 0.62f, w * 0.5f + bw * 0.5f, h * 0.62f + bh)
        paint.color = Color.rgb(22, 163, 74)
        canvas.drawRoundRect(playBtnRect, 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.WHITE
        canvas.drawRoundRect(playBtnRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        textPaint.color = Color.WHITE
        textPaint.textSize = h * 0.06f
        canvas.drawText("PLAY MISSIONS", playBtnRect.centerX(), playBtnRect.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    fun renderOverworldMap(
        canvas: Canvas,
        w: Int,
        h: Int,
        dt: Float,
        save: SaveManager,
        selectedIdx: Int,
        heroX: Float,
        heroY: Float
    ) {
        globalTime += dt
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(15, 32, 58), Color.rgb(22, 52, 72), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = h * 0.055f
        canvas.drawText("WORLD MAP", w * 0.03f, h * 0.09f, textPaint)

        // Top-right settings bar (7 pills)
        val pillW = w * 0.088f
        val pillH = h * 0.068f
        val topY = h * 0.038f
        val gap = w * 0.008f
        val startX = w - (pillW * 7 + gap * 6 + w * 0.025f)

        val sizeLabel = when {
            save.controlScale < 0.9f -> "SZ:S"
            save.controlScale > 1.1f -> "SZ:L"
            else -> "SZ:M"
        }
        val opLabel = "OP:${(save.controlOpacity * 100).toInt()}%"

        val toggles = listOf(
            Triple(toggleSfxRect, "SFX:${if (save.sfxEnabled) "ON" else "OFF"}", save.sfxEnabled),
            Triple(toggleBgmRect, "BGM:${if (save.bgmEnabled) "ON" else "OFF"}", save.bgmEnabled),
            Triple(toggleHapticRect, "VIB:${if (save.hapticsEnabled) "ON" else "OFF"}", save.hapticsEnabled),
            Triple(toggleAutoRunRect, "AUTO:${if (save.autoRunEnabled) "ON" else "OFF"}", save.autoRunEnabled),
            Triple(toggleSizeRect, sizeLabel, true),
            Triple(toggleOpacityRect, opLabel, true),
            Triple(toggleUnlockRect, "ALL:${if (save.unlockAllEnabled) "ON" else "OFF"}", save.unlockAllEnabled)
        )
        for (i in toggles.indices) {
            val (r, label, on) = toggles[i]
            val rx = startX + i * (pillW + gap)
            r.set(rx, topY, rx + pillW, topY + pillH)
            paint.color = if (on) Color.rgb(37, 99, 235) else Color.rgb(51, 65, 85)
            canvas.drawRoundRect(r, 12f, 12f, paint)
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = h * 0.023f
            canvas.drawText(label, r.centerX(), r.centerY() + textPaint.textSize * 0.35f, textPaint)
        }

        // Compute winding 10-node overworld path
        missionNodeRadius = (h * 0.056f).coerceIn(24f, 44f)
        val xs = floatArrayOf(0.08f, 0.19f, 0.31f, 0.43f, 0.55f, 0.67f, 0.78f, 0.89f, 0.76f, 0.90f)
        val ys = floatArrayOf(0.56f, 0.34f, 0.56f, 0.32f, 0.56f, 0.32f, 0.55f, 0.30f, 0.65f, 0.64f)
        for (i in 0 until 10) {
            missionNodeCenters[i].set(w * xs[i], h * ys[i])
        }

        // Draw connecting dotted path
        paint.color = Color.argb(180, 250, 204, 21)
        paint.strokeWidth = 6f
        paint.pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        for (i in 0 until 9) {
            canvas.drawLine(
                missionNodeCenters[i].x, missionNodeCenters[i].y,
                missionNodeCenters[i + 1].x, missionNodeCenters[i + 1].y, paint
            )
        }
        paint.pathEffect = null

        // Draw mission nodes
        for (i in LevelRepository.missions.indices) {
            val m = LevelRepository.missions[i]
            val ms = save.getMissionSave(m.id)
            val pt = missionNodeCenters[i]
            val isSel = i == selectedIdx

            paint.color = when {
                !ms.unlocked -> Color.rgb(51, 65, 85)
                ms.stars == 3 -> Color.rgb(234, 179, 8)
                ms.stars > 0 -> Color.rgb(22, 163, 74)
                else -> Color.rgb(37, 99, 235)
            }
            canvas.drawCircle(pt.x, pt.y, missionNodeRadius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isSel) 6f else 3f
            paint.color = if (isSel) Color.WHITE else Color.argb(180, 203, 213, 225)
            canvas.drawCircle(pt.x, pt.y, missionNodeRadius, paint)
            paint.style = Paint.Style.FILL

            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = missionNodeRadius * 0.72f
            canvas.drawText(if (ms.unlocked) "${m.id}" else "🔒", pt.x, pt.y + textPaint.textSize * 0.35f, textPaint)

            // Stars + Speedrun Medal below node
            if (ms.unlocked && ms.stars > 0) {
                val medal = when {
                    ms.bestTimeSec >= m.timeLimitSec * 0.55f -> "🥇"
                    ms.bestTimeSec >= m.timeLimitSec * 0.35f -> "🥈"
                    else -> "🥉"
                }
                textPaint.textSize = h * 0.025f
                textPaint.color = Color.rgb(250, 204, 21)
                val starStr = "★".repeat(ms.stars) + medal
                canvas.drawText(starStr, pt.x, pt.y + missionNodeRadius + h * 0.032f, textPaint)
            }
        }

        // Draw animated mini hero on overworld
        paint.color = Color.rgb(239, 68, 68)
        canvas.drawCircle(heroX, heroY - missionNodeRadius * 0.9f, missionNodeRadius * 0.42f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(heroX + 4f, heroY - missionNodeRadius * 0.96f, missionNodeRadius * 0.14f, paint)

        // Bottom info card & Start Mission CTA
        val selM = LevelRepository.missions[selectedIdx]
        val selSave = save.getMissionSave(selM.id)
        paint.color = Color.argb(200, 15, 23, 42)
        canvas.drawRoundRect(RectF(w * 0.03f, h * 0.78f, w * 0.65f, h * 0.95f), 20f, 20f, paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.rgb(250, 204, 21)
        textPaint.textSize = h * 0.038f
        canvas.drawText("MISSION ${selM.id}: ${selM.name}", w * 0.05f, h * 0.85f, textPaint)
        textPaint.color = Color.rgb(203, 213, 225)
        textPaint.textSize = h * 0.030f
        canvas.drawText("${selM.subtitle}   •   Bonus: ${selM.bonusObjective.description}", w * 0.05f, h * 0.91f, textPaint)

        startMissionBtnRect.set(w * 0.68f, h * 0.78f, w * 0.97f, h * 0.95f)
        paint.color = if (selSave.unlocked) Color.rgb(22, 163, 74) else Color.rgb(71, 85, 105)
        canvas.drawRoundRect(startMissionBtnRect, 20f, 20f, paint)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = h * 0.045f
        canvas.drawText(if (selSave.unlocked) "START MISSION" else "LOCKED", startMissionBtnRect.centerX(), startMissionBtnRect.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    fun renderOverlay(canvas: Canvas, w: Int, h: Int, screen: GameScreen, engine: PhysicsEngine) {
        paint.color = Color.argb(175, 10, 15, 28)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        val panelW = w * 0.56f
        val panelH = h * 0.66f
        val px = (w - panelW) * 0.5f
        val py = (h - panelH) * 0.5f
        paint.color = Color.rgb(30, 41, 59)
        canvas.drawRoundRect(RectF(px, py, px + panelW, py + panelH), 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.rgb(96, 165, 250)
        canvas.drawRoundRect(RectF(px, py, px + panelW, py + panelH), 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = h * 0.068f

        val title = when (screen) {
            GameScreen.PAUSED -> "PAUSED"
            GameScreen.MISSION_COMPLETE -> "MISSION COMPLETE!"
            GameScreen.GAME_OVER -> "GAME OVER"
            else -> ""
        }
        canvas.drawText(title, w * 0.5f, py + panelH * 0.22f, textPaint)

        if (screen == GameScreen.MISSION_COMPLETE) {
            val stars = engine.computeEarnedStars()
            textPaint.color = Color.rgb(250, 204, 21)
            textPaint.textSize = h * 0.08f
            canvas.drawText((1..3).joinToString("  ") { if (it <= stars) "★" else "☆" }, w * 0.5f, py + panelH * 0.39f, textPaint)

            textPaint.color = Color.rgb(203, 213, 225)
            textPaint.textSize = h * 0.036f
            val bonusStatus = if (engine.isBonusMet()) "✓ ${engine.mission.bonusObjective.description}" else "✗ ${engine.mission.bonusObjective.description}"
            canvas.drawText("Score: ${engine.player.score}   •   $bonusStatus", w * 0.5f, py + panelH * 0.52f, textPaint)
        }

        val btnH = panelH * 0.19f
        val btnY = py + panelH * 0.68f

        when (screen) {
            GameScreen.PAUSED -> {
                val btnW = panelW * 0.27f
                resumeBtnRect.set(px + panelW * 0.06f, btnY, px + panelW * 0.06f + btnW, btnY + btnH)
                restartBtnRect.set(px + panelW * 0.365f, btnY, px + panelW * 0.365f + btnW, btnY + btnH)
                menuBtnRect.set(px + panelW * 0.67f, btnY, px + panelW * 0.67f + btnW, btnY + btnH)
                drawBtn(canvas, resumeBtnRect, "RESUME", Color.rgb(22, 163, 74))
                drawBtn(canvas, restartBtnRect, "RESTART", Color.rgb(217, 119, 6))
                drawBtn(canvas, menuBtnRect, "WORLD MAP", Color.rgb(71, 85, 105))
            }
            GameScreen.MISSION_COMPLETE -> {
                val btnW = panelW * 0.36f
                val hasNext = engine.mission.id < LevelRepository.missions.size
                if (hasNext) {
                    nextMissionBtnRect.set(px + panelW * 0.08f, btnY, px + panelW * 0.08f + btnW, btnY + btnH)
                    menuBtnRect.set(px + panelW * 0.56f, btnY, px + panelW * 0.56f + btnW, btnY + btnH)
                    drawBtn(canvas, nextMissionBtnRect, "NEXT MISSION", Color.rgb(22, 163, 74))
                    drawBtn(canvas, menuBtnRect, "WORLD MAP", Color.rgb(71, 85, 105))
                } else {
                    menuBtnRect.set(w * 0.5f - btnW * 0.5f, btnY, w * 0.5f + btnW * 0.5f, btnY + btnH)
                    drawBtn(canvas, menuBtnRect, "WORLD MAP", Color.rgb(37, 99, 235))
                }
            }
            GameScreen.GAME_OVER -> {
                val btnW = panelW * 0.36f
                restartBtnRect.set(px + panelW * 0.08f, btnY, px + panelW * 0.08f + btnW, btnY + btnH)
                menuBtnRect.set(px + panelW * 0.56f, btnY, px + panelW * 0.56f + btnW, btnY + btnH)
                drawBtn(canvas, restartBtnRect, "RETRY", Color.rgb(220, 38, 38))
                drawBtn(canvas, menuBtnRect, "WORLD MAP", Color.rgb(71, 85, 105))
            }
            else -> {}
        }
    }

    fun renderFadeCurtain(canvas: Canvas, w: Int, h: Int, alpha: Float) {
        if (alpha <= 0.01f) return
        paint.color = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 10, 15, 28)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun drawBtn(canvas: Canvas, r: RectF, label: String, color: Int) {
        paint.color = color
        canvas.drawRoundRect(r, 18f, 18f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = r.height() * 0.34f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, r.centerX(), r.centerY() + textPaint.textSize * 0.35f, textPaint)
    }
}
