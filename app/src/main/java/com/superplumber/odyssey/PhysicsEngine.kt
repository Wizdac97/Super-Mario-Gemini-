package com.superplumber.odyssey

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class PhysicsEngine(
    private val sound: SoundEngine
) {
    lateinit var mission: MissionDef
    lateinit var grid: Array<Array<TileType>>
    var cols = 0
    var rows = 0

    val player = Player()
    val enemies = mutableListOf<Enemy>()
    val items = mutableListOf<Item>()
    val fireballs = mutableListOf<Fireball>()
    val movingPlatforms = mutableListOf<MovingPlatform>()
    val particles = mutableListOf<Particle>()
    val floatingTexts = mutableListOf<FloatingText>()
    val blockBumps = mutableListOf<BlockBump>()
    val crumbles = mutableListOf<CrumbleState>()

    var timeLeft = 180f
    var camX = 0f
    var shakeTimer = 0f
    var shakeIntensity = 0f
    var stompCombo = 0
    var flagCol = -1
    var bossDefeated = false

    var checkpointReached = false
    var checkpointX = 2f
    var checkpointY = 10f
    var introBannerTimer = 2.6f

    // Secret Warp Pipe Bonus Room state
    var inBonusRoom = false
    private var savedGrid: Array<Array<TileType>>? = null
    private var savedCols = 0
    private var savedEnemies: List<Enemy> = emptyList()
    private var savedItems: List<Item> = emptyList()
    private var savedPlatforms: List<MovingPlatform> = emptyList()
    private var returnX = 2f
    private var returnY = 10f
    private var cannonTimer = 0f

    private var nextEnemyId = 1
    private var starCoinCounter = 0

    fun loadMission(missionDef: MissionDef, keepCheckpoint: Boolean = false) {
        mission = missionDef
        rows = missionDef.rows.size
        cols = missionDef.rows.maxOf { it.length }
        grid = Array(rows) { Array(cols) { TileType.EMPTY } }

        enemies.clear()
        items.clear()
        fireballs.clear()
        movingPlatforms.clear()
        particles.clear()
        floatingTexts.clear()
        blockBumps.clear()
        crumbles.clear()

        inBonusRoom = false
        savedGrid = null
        cannonTimer = 0f

        timeLeft = missionDef.timeLimitSec.toFloat()
        shakeTimer = 0f
        stompCombo = 0
        flagCol = -1
        bossDefeated = false
        nextEnemyId = 1
        starCoinCounter = 0
        introBannerTimer = if (keepCheckpoint) 0f else 2.6f

        if (!keepCheckpoint) {
            checkpointReached = false
        }

        val savedLives = if (player.lives > 0) player.lives else 3
        val savedScore = if (keepCheckpoint) player.score else 0
        val savedCoins = if (keepCheckpoint) player.coins else 0
        val savedStars = if (keepCheckpoint) player.starCoinsCollected.copyOf() else booleanArrayOf(false, false, false)

        player.x = 2f
        player.y = 10f
        player.vx = 0f
        player.vy = 0f
        player.facingRight = true
        player.onGround = false
        player.onIce = false
        player.onWallDir = 0
        player.isGroundPounding = false
        player.coyoteTimer = 0f
        player.jumpBufferTimer = 0f
        player.powerState = PowerState.SMALL
        player.width = 0.76f
        player.height = 0.90f
        player.invincibleTimer = 0f
        player.starTimer = 0f
        player.squashX = 1f
        player.squashY = 1f
        player.isCrouching = false
        player.isSkidding = false
        player.lives = savedLives
        player.coins = savedCoins
        player.score = savedScore
        for (i in 0..2) player.starCoinsCollected[i] = savedStars[i]
        player.enemiesDefeated = 0
        player.tookDamage = false
        player.fireballCooldown = 0f
        player.dead = false
        player.deathTimer = 0f
        player.won = false
        player.winTimer = 0f

        for (r in 0 until rows) {
            val line = missionDef.rows[r]
            for (c in 0 until line.length) {
                when (line[c]) {
                    '#' -> grid[r][c] = TileType.GROUND
                    'B' -> grid[r][c] = TileType.BRICK
                    '?' -> grid[r][c] = TileType.QUESTION_COIN
                    'P' -> grid[r][c] = TileType.QUESTION_POWERUP
                    '*' -> grid[r][c] = TileType.QUESTION_STAR
                    '1' -> grid[r][c] = TileType.QUESTION_1UP
                    'H' -> grid[r][c] = TileType.HARD_BLOCK
                    'I' -> grid[r][c] = TileType.ICE_BLOCK
                    '=' -> grid[r][c] = TileType.BRIDGE
                    '~' -> {
                        grid[r][c] = TileType.CRUMBLE_BRIDGE
                        crumbles.add(CrumbleState(c, r))
                    }
                    'L' -> grid[r][c] = TileType.LAVA
                    'S' -> grid[r][c] = TileType.SPRING
                    'X' -> grid[r][c] = TileType.AXE
                    'M' -> grid[r][c] = TileType.CHECKPOINT
                    'A' -> grid[r][c] = TileType.CANNON
                    '[' -> grid[r][c] = TileType.PIPE_TL
                    ']' -> grid[r][c] = TileType.PIPE_TR
                    'V' -> grid[r][c] = TileType.WARP_PIPE_TL
                    'v' -> grid[r][c] = TileType.WARP_PIPE_TR
                    '.' -> {
                        if (r > 0 && (grid[r - 1][c] == TileType.PIPE_TL || grid[r - 1][c] == TileType.WARP_PIPE_TL)) {
                            grid[r][c] = TileType.PIPE_BL
                        } else if (r > 0 && (grid[r - 1][c] == TileType.PIPE_TR || grid[r - 1][c] == TileType.WARP_PIPE_TR)) {
                            grid[r][c] = TileType.PIPE_BR
                        }
                    }
                    '|' -> {
                        if (flagCol == -1) flagCol = c
                        grid[r][c] = if (r > 0 && missionDef.rows[r - 1].getOrNull(c) != '|') TileType.FLAG_TOP else TileType.FLAG_POLE
                    }
                    '@' -> {
                        if (!keepCheckpoint) {
                            player.x = c.toFloat()
                            player.y = r.toFloat() - player.height + 1f
                        }
                    }
                    'o' -> items.add(Item(ItemType.WORLD_COIN, c + 0.15f, r + 0.15f))
                    'C' -> {
                        val idx = starCoinCounter.coerceAtMost(2)
                        if (!keepCheckpoint || !player.starCoinsCollected[idx]) {
                            items.add(Item(ItemType.STAR_COIN, c + 0.05f, r + 0.05f, starCoinIndex = idx))
                        }
                        starCoinCounter++
                    }
                    'g' -> enemies.add(Enemy(nextEnemyId++, EnemyType.SHROOMLING, c.toFloat(), r.toFloat(), -2.0f, 0f))
                    'k' -> enemies.add(Enemy(nextEnemyId++, EnemyType.SHELLBACK, c.toFloat(), r - 0.15f, -2.1f, 0f, height = 1.0f))
                    's' -> enemies.add(Enemy(nextEnemyId++, EnemyType.SPINY, c.toFloat(), r.toFloat(), -1.9f, 0f))
                    'w' -> enemies.add(Enemy(nextEnemyId++, EnemyType.WINGED_SHROOM, c.toFloat(), r.toFloat(), -1.8f, 0f))
                    'p' -> {
                        if (r > 0 && (grid[r - 1][c] == TileType.PIPE_TL || grid[r - 1][c] == TileType.WARP_PIPE_TL)) grid[r][c] = TileType.PIPE_BL
                        else if (r > 0 && (grid[r - 1][c] == TileType.PIPE_TR || grid[r - 1][c] == TileType.WARP_PIPE_TR)) grid[r][c] = TileType.PIPE_BR
                        enemies.add(Enemy(nextEnemyId++, EnemyType.PIPE_CHOMPER, c.toFloat() - 0.35f, r.toFloat(), 0f, 0f, width = 0.85f, height = 0.95f))
                    }
                    'K' -> {
                        enemies.add(
                            Enemy(
                                nextEnemyId++, EnemyType.KING_REX,
                                c.toFloat(), r.toFloat() - 0.8f, -1.8f, 0f,
                                width = 1.6f, height = 1.8f, hp = 5, maxHp = 5
                            )
                        )
                    }
                }
            }
        }

        if (keepCheckpoint && checkpointReached) {
            player.x = checkpointX
            player.y = checkpointY
        }

        for (r in 1 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] == TileType.EMPTY) {
                    if (grid[r - 1][c] == TileType.PIPE_TL || grid[r - 1][c] == TileType.WARP_PIPE_TL || grid[r - 1][c] == TileType.PIPE_BL) {
                        grid[r][c] = TileType.PIPE_BL
                    } else if (grid[r - 1][c] == TileType.PIPE_TR || grid[r - 1][c] == TileType.WARP_PIPE_TR || grid[r - 1][c] == TileType.PIPE_BR) {
                        grid[r][c] = TileType.PIPE_BR
                    }
                }
            }
        }

        for (mp in missionDef.movingPlatforms) {
            movingPlatforms.add(
                MovingPlatform(
                    x = mp.startCol,
                    y = mp.startRow,
                    width = mp.widthTiles.toFloat(),
                    startX = mp.startCol,
                    startY = mp.startRow,
                    endX = mp.endCol,
                    endY = mp.endRow,
                    speed = mp.speed
                )
            )
        }

        camX = (player.x - 5f).coerceAtLeast(0f)
        sound.currentBiome = missionDef.biome
    }

    private fun enterBonusRoom() {
        if (inBonusRoom) return
        inBonusRoom = true
        savedGrid = grid
        savedCols = cols
        savedEnemies = enemies.toList()
        savedItems = items.toList()
        savedPlatforms = movingPlatforms.toList()
        returnX = player.x + 3.5f
        returnY = 7f

        cols = 24
        grid = Array(rows) { Array(cols) { TileType.EMPTY } }
        enemies.clear()
        items.clear()
        movingPlatforms.clear()
        fireballs.clear()

        for (c in 0 until cols) {
            grid[0][c] = TileType.GROUND
            grid[1][c] = TileType.GROUND
            grid[12][c] = TileType.GROUND
            grid[13][c] = TileType.GROUND
        }
        for (r in 2 until 12) {
            grid[r][0] = TileType.GROUND
            grid[r][cols - 1] = TileType.GROUND
        }
        // Exit warp pipe on right
        grid[10][cols - 4] = TileType.WARP_PIPE_TL
        grid[10][cols - 3] = TileType.WARP_PIPE_TR
        grid[11][cols - 4] = TileType.PIPE_BL
        grid[11][cols - 3] = TileType.PIPE_BR

        // Bonus coins + 1-UP block
        grid[7][11] = TileType.QUESTION_1UP
        for (c in 5..17) {
            items.add(Item(ItemType.WORLD_COIN, c + 0.15f, 9.15f))
            if (c % 2 == 1) items.add(Item(ItemType.WORLD_COIN, c + 0.15f, 8.15f))
        }

        player.x = 2.5f
        player.y = 10f
        player.vx = 0f
        player.vy = 0f
        camX = 0f
        sound.currentBiome = Biome.CAVERN
        sound.playCheckpoint()
        floatingTexts.add(FloatingText(4f, 6f, text = "BONUS COIN ROOM!", color = Color.YELLOW, scale = 1.25f))
    }

    private fun exitBonusRoom() {
        val sg = savedGrid ?: return
        inBonusRoom = false
        grid = sg
        cols = savedCols
        enemies.clear()
        enemies.addAll(savedEnemies)
        items.clear()
        items.addAll(savedItems)
        movingPlatforms.clear()
        movingPlatforms.addAll(savedPlatforms)
        fireballs.clear()
        savedGrid = null

        player.x = returnX.coerceIn(2f, cols - 4f)
        player.y = returnY
        player.vx = 0f
        player.vy = -8f
        sound.currentBiome = mission.biome
        sound.playCheckpoint()
    }

    fun update(dt: Float, controls: TouchControls, viewWidthTiles: Float) {
        if (introBannerTimer > 0f) introBannerTimer = (introBannerTimer - dt).coerceAtLeast(0f)
        if (shakeTimer > 0f) shakeTimer = (shakeTimer - dt).coerceAtLeast(0f)

        updateParticles(dt)
        updateFloatingTexts(dt)
        updateBlockBumps(dt)

        if (player.dead) {
            player.deathTimer += dt
            player.vy += 32f * dt
            player.y += player.vy * dt
            return
        }

        if (player.won) {
            player.winTimer += dt
            if (flagCol >= 0 && !player.onGround) {
                player.vy = 5.5f
                movePlayerVertical(dt)
            } else {
                player.vy += 32f * dt
                movePlayerVertical(dt)
            }
            return
        }

        timeLeft = (timeLeft - dt).coerceAtLeast(0f)
        if (timeLeft <= 0f) {
            killPlayer()
            return
        }

        if (player.invincibleTimer > 0f) player.invincibleTimer = (player.invincibleTimer - dt).coerceAtLeast(0f)
        if (player.starTimer > 0f) player.starTimer = (player.starTimer - dt).coerceAtLeast(0f)
        if (player.fireballCooldown > 0f) player.fireballCooldown = (player.fireballCooldown - dt).coerceAtLeast(0f)

        player.squashX += (1f - player.squashX) * min(1f, dt * 14f)
        player.squashY += (1f - player.squashY) * min(1f, dt * 14f)

        if (!inBonusRoom) {
            updateCrumbleBridges(dt)
            updateCannons(dt)
            updateMovingPlatforms(dt)
        }
        updatePlayer(dt, controls)
        updateEnemies(dt)
        updateItems(dt)
        updateFireballs(dt)

        val lookahead = if (player.facingRight) 2.2f else -1.0f
        val targetCamX = (player.x + lookahead - viewWidthTiles * 0.38f)
            .coerceIn(0f, (cols - viewWidthTiles).coerceAtLeast(0f))
        camX += (targetCamX - camX) * min(1f, dt * 7.5f)
    }

    private fun updateCrumbleBridges(dt: Float) {
        for (cb in crumbles) {
            if (cb.fallen) {
                cb.respawnTimer -= dt
                if (cb.respawnTimer <= 0f) {
                    cb.fallen = false
                    cb.timer = 0.55f
                    grid[cb.row][cb.col] = TileType.CRUMBLE_BRIDGE
                }
            } else if (cb.timer < 0.55f) {
                cb.timer -= dt
                if (cb.timer <= 0f) {
                    cb.fallen = true
                    cb.respawnTimer = 3.5f
                    grid[cb.row][cb.col] = TileType.EMPTY
                    spawnDust(cb.col + 0.5f, cb.row + 0.3f, 0f)
                }
            }
        }
    }

    private fun updateCannons(dt: Float) {
        cannonTimer += dt
        if (cannonTimer < 2.8f) return
        cannonTimer = 0f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] == TileType.CANNON && abs(c - player.x) in 2.5f..16f) {
                    val dir = if (player.x < c) -1f else 1f
                    enemies.add(
                        Enemy(
                            nextEnemyId++, EnemyType.BULLET,
                            c + (if (dir > 0) 1f else -0.8f), r.toFloat() + 0.1f,
                            dir * 6.5f, 0f, width = 0.8f, height = 0.7f
                        )
                    )
                    sound.playBrick()
                }
            }
        }
    }

    private fun updateMovingPlatforms(dt: Float) {
        for (mp in movingPlatforms) {
            val prevX = mp.x
            val dist = kotlin.math.hypot(mp.endX - mp.startX, mp.endY - mp.startY).coerceAtLeast(0.01f)
            val step = (mp.speed * dt) / dist
            if (mp.forward) {
                mp.progress += step
                if (mp.progress >= 1f) {
                    mp.progress = 1f
                    mp.forward = false
                }
            } else {
                mp.progress -= step
                if (mp.progress <= 0f) {
                    mp.progress = 0f
                    mp.forward = true
                }
            }
            val t = mp.progress * mp.progress * (3f - 2f * mp.progress)
            mp.x = mp.startX + (mp.endX - mp.startX) * t
            mp.y = mp.startY + (mp.endY - mp.startY) * t
            mp.dx = mp.x - prevX
            mp.dy = 0f
        }
    }

    private fun updatePlayer(dt: Float, controls: TouchControls) {
        player.isCrouching = controls.down && player.onGround && player.powerState != PowerState.SMALL

        // Check Warp Pipe entry when pressing Down on a Warp Pipe
        if (player.onGround && controls.downJustPressed) {
            val cx = (player.x + player.width * 0.5f).toInt().coerceIn(0, cols - 1)
            val belowRow = (player.y + player.height + 0.1f).toInt().coerceIn(0, rows - 1)
            val belowTile = grid[belowRow][cx]
            if (belowTile == TileType.WARP_PIPE_TL || belowTile == TileType.WARP_PIPE_TR) {
                if (inBonusRoom) exitBonusRoom() else enterBonusRoom()
                return
            }
        }

        if (!player.onGround && controls.downJustPressed && !player.isGroundPounding) {
            player.isGroundPounding = true
            player.vx = 0f
            player.vy = 20.5f
            player.squashX = 0.72f
            player.squashY = 1.32f
        }

        val maxSpeed = if (controls.runHeld) 9.8f else 6.8f
        val accel = if (player.onIce) 11f else if (player.onGround) 34f else 24f
        val friction = if (player.onIce) 5.5f else if (player.onGround) 28f else 8f

        var moveDir = 0f
        if (!player.isCrouching && !player.isGroundPounding) {
            if (controls.left) moveDir -= 1f
            if (controls.right) moveDir += 1f
        }

        if (moveDir != 0f) {
            player.isSkidding = player.onGround && ((moveDir > 0 && player.vx < -1.5f) || (moveDir < 0 && player.vx > 1.5f))
            player.facingRight = moveDir > 0
            val mult = if (player.isSkidding) 1.7f else 1f
            player.vx += moveDir * accel * mult * dt
            if (player.vx > maxSpeed) player.vx = maxSpeed
            if (player.vx < -maxSpeed) player.vx = -maxSpeed

            if (player.isSkidding && Random.nextFloat() < 0.45f) {
                spawnDust(player.x + player.width * 0.5f, player.y + player.height, -moveDir * 1.5f)
            }
        } else {
            player.isSkidding = false
            if (abs(player.vx) <= friction * dt) {
                player.vx = 0f
            } else {
                player.vx -= kotlin.math.sign(player.vx) * friction * dt
            }
        }

        if (player.onGround) {
            player.coyoteTimer = 0.11f
            stompCombo = 0
        } else {
            player.coyoteTimer = (player.coyoteTimer - dt).coerceAtLeast(0f)
        }

        if (controls.jumpJustPressed) {
            player.jumpBufferTimer = 0.12f
        } else {
            player.jumpBufferTimer = (player.jumpBufferTimer - dt).coerceAtLeast(0f)
        }

        if (player.jumpBufferTimer > 0f && player.coyoteTimer > 0f) {
            val speedBoost = abs(player.vx) * 0.12f
            player.vy = -(14.8f + speedBoost)
            player.onGround = false
            player.isGroundPounding = false
            player.coyoteTimer = 0f
            player.jumpBufferTimer = 0f
            player.squashX = 0.78f
            player.squashY = 1.24f
            sound.playJump(player.powerState != PowerState.SMALL)
            spawnDust(player.x + player.width * 0.5f, player.y + player.height, 0f)
        } else if (player.jumpBufferTimer > 0f && !player.onGround && player.onWallDir != 0) {
            player.vy = -14.2f
            player.vx = -player.onWallDir * 8.2f
            player.facingRight = player.onWallDir < 0
            player.onWallDir = 0
            player.jumpBufferTimer = 0f
            player.squashX = 0.75f
            player.squashY = 1.25f
            sound.playWallJump()
            spawnDust(player.x + player.width * 0.5f, player.y + player.height * 0.5f, 0f)
        }

        var gravity = 36f
        if (player.vy < 0f && !controls.jumpHeld) {
            gravity = 64f
        } else if (abs(player.vy) < 2.2f && controls.jumpHeld) {
            gravity = 21f
        }

        player.vy = (player.vy + gravity * dt).coerceAtMost(if (player.isGroundPounding) 26f else 21f)

        if (!player.onGround && player.onWallDir != 0 && player.vy > 3.2f && ((player.onWallDir < 0 && controls.left) || (player.onWallDir > 0 && controls.right))) {
            player.vy = 3.2f
            if (Random.nextFloat() < 0.35f) {
                val wx = if (player.onWallDir > 0) player.x + player.width else player.x
                spawnDust(wx, player.y + player.height * 0.5f, 0f)
            }
        }

        if (controls.fireJustPressed && player.powerState == PowerState.FIRE && player.fireballCooldown <= 0f) {
            if (fireballs.count { it.fromPlayer && it.active } < 3) {
                val dir = if (player.facingRight) 1f else -1f
                fireballs.add(
                    Fireball(
                        x = player.x + (if (player.facingRight) player.width else -0.3f),
                        y = player.y + player.height * 0.45f,
                        vx = dir * 13.5f,
                        vy = 2.0f,
                        fromPlayer = true
                    )
                )
                player.fireballCooldown = 0.22f
                sound.playFireball()
            }
        }

        player.onWallDir = 0
        player.x += player.vx * dt
        if (player.x < 0f) {
            player.x = 0f
            player.vx = 0f
        } else if (player.x + player.width > cols) {
            player.x = cols - player.width
            player.vx = 0f
        }
        resolveHorizontalTiles()

        val wasOnGround = player.onGround
        movePlayerVertical(dt)
        if (!wasOnGround && player.onGround) {
            if (player.isGroundPounding) {
                player.isGroundPounding = false
                shakeTimer = 0.14f
                shakeIntensity = 8f
                player.squashX = 1.35f
                player.squashY = 0.68f
                sound.playBrick()
                spawnDust(player.x, player.y + player.height, -3.5f)
                spawnDust(player.x + player.width, player.y + player.height, 3.5f)
            } else {
                player.squashX = 1.22f
                player.squashY = 0.80f
                spawnDust(player.x + player.width * 0.5f, player.y + player.height, 0f)
            }
        }

        if (player.onGround && abs(player.vx) > 0.4f) {
            player.runAnimTimer += dt * (4f + abs(player.vx) * 1.4f)
        } else {
            player.runAnimTimer = 0f
        }

        if (player.y > rows + 1.5f) {
            killPlayer()
        }
    }

    private fun movePlayerVertical(dt: Float) {
        player.y += player.vy * dt
        player.onGround = false
        player.onIce = false

        val leftCol = player.x.toInt().coerceIn(0, cols - 1)
        val rightCol = (player.x + player.width - 0.01f).toInt().coerceIn(0, cols - 1)
        val topRow = player.y.toInt().coerceIn(0, rows - 1)
        val bottomRow = (player.y + player.height - 0.01f).toInt().coerceIn(0, rows - 1)

        if (player.vy >= 0f) {
            for (c in leftCol..rightCol) {
                val r = bottomRow
                if (r in 0 until rows) {
                    val t = grid[r][c]
                    if (t == TileType.LAVA) {
                        if (player.starTimer <= 0f) {
                            killPlayer()
                            return
                        }
                    } else if (t == TileType.SPRING) {
                        player.y = r.toFloat() - player.height
                        player.vy = -21.5f
                        player.onGround = false
                        player.isGroundPounding = false
                        player.squashX = 0.72f
                        player.squashY = 1.32f
                        sound.playSpring()
                        spawnSparkles(c + 0.5f, r.toFloat(), Color.YELLOW, 8)
                        return
                    } else if (player.isGroundPounding && t == TileType.BRICK && player.powerState != PowerState.SMALL) {
                        grid[r][c] = TileType.EMPTY
                        player.score += 50
                        sound.playBrick()
                        spawnBrickDebris(c + 0.5f, r + 0.5f)
                    } else if (isSolid(t) || t == TileType.BRIDGE || t == TileType.CRUMBLE_BRIDGE) {
                        if (player.y + player.height - player.vy * dt <= r + 0.45f || isSolid(t)) {
                            player.y = r.toFloat() - player.height
                            player.vy = 0f
                            player.onGround = true
                            if (t == TileType.ICE_BLOCK) player.onIce = true
                            if (t == TileType.CRUMBLE_BRIDGE) {
                                crumbles.firstOrNull { it.col == c && it.row == r && !it.fallen }?.let {
                                    if (it.timer == 0.55f) it.timer = 0.54f
                                }
                            }
                        }
                    }
                }
            }

            for (mp in movingPlatforms) {
                val overlapX = player.x + player.width > mp.x + 0.05f && player.x < mp.x + mp.width - 0.05f
                if (overlapX) {
                    val prevBottom = player.y + player.height - player.vy * dt
                    if (prevBottom <= mp.y + 0.35f && player.y + player.height >= mp.y) {
                        player.y = mp.y - player.height
                        player.vy = 0f
                        player.onGround = true
                        player.x += mp.dx
                    }
                }
            }
        } else {
            for (c in leftCol..rightCol) {
                val r = topRow
                if (r in 0 until rows && isSolid(grid[r][c])) {
                    val tileCenter = c + 0.5f
                    val playerCenter = player.x + player.width * 0.5f
                    val overlap = if (playerCenter < tileCenter) (player.x + player.width) - c else (c + 1f) - player.x
                    if (overlap in 0.01f..0.28f) {
                        val nudgedCol = if (playerCenter < tileCenter) c - 1 else c + 1
                        if (nudgedCol in 0 until cols && !isSolid(grid[r][nudgedCol])) {
                            player.x = if (playerCenter < tileCenter) c - player.width - 0.01f else (c + 1f) + 0.01f
                            continue
                        }
                    }

                    player.y = (r + 1).toFloat()
                    player.vy = 0f
                    hitBlockFromBelow(c, r)
                    break
                }
            }
        }

        checkWinTriggers()
    }

    private fun resolveHorizontalTiles() {
        val topRow = (player.y + 0.06f).toInt().coerceIn(0, rows - 1)
        val bottomRow = (player.y + player.height - 0.06f).toInt().coerceIn(0, rows - 1)

        if (player.vx > 0f) {
            val rightCol = (player.x + player.width).toInt().coerceIn(0, cols - 1)
            for (r in topRow..bottomRow) {
                if (isSolid(grid[r][rightCol])) {
                    player.x = rightCol - player.width
                    player.vx = 0f
                    player.onWallDir = 1
                    break
                }
            }
        } else if (player.vx < 0f) {
            val leftCol = player.x.toInt().coerceIn(0, cols - 1)
            for (r in topRow..bottomRow) {
                if (isSolid(grid[r][leftCol])) {
                    player.x = (leftCol + 1).toFloat()
                    player.vx = 0f
                    player.onWallDir = -1
                    break
                }
            }
        }
    }

    private fun checkWinTriggers() {
        if (inBonusRoom) return
        val cx = (player.x + player.width * 0.5f).toInt().coerceIn(0, cols - 1)
        val cy = (player.y + player.height * 0.5f).toInt().coerceIn(0, rows - 1)
        val tile = grid[cy][cx]
        if (tile == TileType.CHECKPOINT && !checkpointReached) {
            checkpointReached = true
            checkpointX = cx.toFloat()
            checkpointY = cy.toFloat() - player.height + 1f
            sound.playCheckpoint()
            spawnSparkles(cx + 0.5f, cy.toFloat(), Color.rgb(34, 211, 238), 16)
            floatingTexts.add(FloatingText(cx.toFloat(), cy - 0.5f, text = "CHECKPOINT!", color = Color.rgb(34, 211, 238), scale = 1.2f))
        } else if (tile == TileType.FLAG_POLE || tile == TileType.FLAG_TOP) {
            triggerWin(bonusPts = ((rows - cy) * 200).coerceAtLeast(200))
        } else if (tile == TileType.AXE) {
            grid[cy][cx] = TileType.EMPTY
            for (e in enemies) {
                if (e.type == EnemyType.KING_REX && e.alive) {
                    e.alive = false
                    bossDefeated = true
                    player.enemiesDefeated++
                    spawnSparkles(e.x + e.width * 0.5f, e.y + e.height * 0.5f, Color.rgb(255, 120, 40), 24)
                }
            }
            triggerWin(bonusPts = 2000)
        }
    }

    private fun triggerWin(bonusPts: Int) {
        if (player.won) return
        player.won = true
        player.vx = 0f
        player.score += bonusPts + timeLeft.toInt() * 10
        floatingTexts.add(FloatingText(player.x, player.y - 0.5f, text = "+$bonusPts CLEAR!", color = Color.YELLOW, scale = 1.25f))
        sound.playVictory()
    }

    private fun hitBlockFromBelow(c: Int, r: Int) {
        val t = grid[r][c]
        when (t) {
            TileType.BRICK -> {
                if (player.powerState != PowerState.SMALL) {
                    grid[r][c] = TileType.EMPTY
                    player.score += 50
                    sound.playBrick()
                    shakeTimer = 0.10f
                    shakeIntensity = 6f
                    spawnBrickDebris(c + 0.5f, r + 0.5f)
                } else {
                    blockBumps.add(BlockBump(c, r))
                    sound.playBrick()
                }
            }
            TileType.QUESTION_COIN -> {
                grid[r][c] = TileType.USED_BLOCK
                blockBumps.add(BlockBump(c, r))
                collectCoin(c + 0.5f, r.toFloat(), popup = true)
            }
            TileType.QUESTION_POWERUP -> {
                grid[r][c] = TileType.USED_BLOCK
                blockBumps.add(BlockBump(c, r))
                val itemType = if (player.powerState == PowerState.SMALL) ItemType.MUSHROOM else ItemType.FIRE_FLOWER
                items.add(Item(itemType, c + 0.1f, r - 0.9f, vx = if (itemType == ItemType.MUSHROOM) 2.8f else 0f, vy = -2.5f))
                sound.playPowerUp()
            }
            TileType.QUESTION_STAR -> {
                grid[r][c] = TileType.USED_BLOCK
                blockBumps.add(BlockBump(c, r))
                items.add(Item(ItemType.STARMAN, c + 0.1f, r - 0.9f, vx = 3.5f, vy = -6.0f))
                sound.playPowerUp()
            }
            TileType.QUESTION_1UP -> {
                grid[r][c] = TileType.USED_BLOCK
                blockBumps.add(BlockBump(c, r))
                items.add(Item(ItemType.ONE_UP, c + 0.1f, r - 0.9f, vx = 2.6f, vy = -2.5f))
                sound.playPowerUp()
            }
            else -> {}
        }
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.alive) continue
            if (e.squashed) {
                e.squashTimer -= dt
                if (e.squashTimer <= 0f) e.alive = false
                continue
            }

            if (abs(e.x - player.x) > 22f && e.type != EnemyType.KING_REX) continue

            e.animTimer += dt

            when (e.type) {
                EnemyType.BULLET -> {
                    e.x += e.vx * dt
                    e.facingRight = e.vx > 0f
                    if (e.x < -1f || e.x > cols + 1f) e.alive = false
                }
                EnemyType.PIPE_CHOMPER -> {
                    val cycle = (e.animTimer * 1.4f) % 4.0f
                    val offset = when {
                        cycle < 1.0f -> -cycle * 0.95f
                        cycle < 2.2f -> -0.95f
                        cycle < 3.2f -> -(3.2f - cycle) * 0.95f
                        else -> 0f
                    }
                    e.y = e.baseY + offset
                }
                EnemyType.KING_REX -> updateBoss(e, dt)
                else -> {
                    if (e.type == EnemyType.WINGED_SHROOM && e.onGround) {
                        e.vy = -8.8f
                        e.onGround = false
                    }

                    e.vy = (e.vy + 32f * dt).coerceAtMost(18f)
                    e.x += e.vx * dt
                    e.facingRight = e.vx > 0f

                    val eTop = (e.y + 0.1f).toInt().coerceIn(0, rows - 1)
                    val eBot = (e.y + e.height - 0.1f).toInt().coerceIn(0, rows - 1)
                    val eCol = if (e.vx > 0f) (e.x + e.width).toInt() else e.x.toInt()
                    if (eCol !in 0 until cols) {
                        e.vx = -e.vx
                    } else {
                        for (r in eTop..eBot) {
                            if (isSolid(grid[r][eCol])) {
                                e.x = if (e.vx > 0f) eCol - e.width else (eCol + 1).toFloat()
                                e.vx = -e.vx
                                break
                            }
                        }
                    }

                    e.y += e.vy * dt
                    e.onGround = false
                    val lCol = e.x.toInt().coerceIn(0, cols - 1)
                    val rCol = (e.x + e.width - 0.02f).toInt().coerceIn(0, cols - 1)
                    val bRow = (e.y + e.height).toInt().coerceIn(0, rows - 1)
                    for (c in lCol..rCol) {
                        val t = grid[bRow][c]
                        if (isSolid(t) || t == TileType.BRIDGE || t == TileType.CRUMBLE_BRIDGE) {
                            e.y = bRow - e.height
                            e.vy = 0f
                            e.onGround = true
                            break
                        }
                    }

                    if (e.y > rows + 2f) e.alive = false

                    if (e.type == EnemyType.SHELLBACK && e.shellMoving) {
                        for (other in enemies) {
                            if (other.id != e.id && other.alive && !other.squashed && rectsOverlap(e.x, e.y, e.width, e.height, other.x, other.y, other.width, other.height)) {
                                defeatEnemy(other, 200)
                                sound.playKick()
                            }
                        }
                    }
                }
            }

            if (e.alive && !e.squashed && rectsOverlap(player.x, player.y, player.width, player.height, e.x, e.y, e.width, e.height)) {
                if (player.starTimer > 0f) {
                    defeatEnemy(e, 200)
                    sound.playKick()
                    continue
                }

                val isStomp = (player.vy > 0f && (player.y + player.height - player.vy * dt) <= (e.y + e.height * 0.55f)) || player.isGroundPounding
                if (isStomp && e.type != EnemyType.PIPE_CHOMPER && e.type != EnemyType.SPINY) {
                    stompCombo++
                    val pts = (100 * stompCombo).coerceAtMost(800)
                    player.vy = -11.5f
                    player.isGroundPounding = false
                    player.squashX = 1.2f
                    player.squashY = 0.8f
                    sound.playStomp()

                    when (e.type) {
                        EnemyType.SHROOMLING, EnemyType.WINGED_SHROOM, EnemyType.BULLET -> {
                            e.squashed = true
                            e.squashTimer = 0.25f
                            e.vx = 0f
                            player.enemiesDefeated++
                            val label = if (stompCombo >= 3) "+$pts (${stompCombo}x!)" else "+$pts"
                            floatingTexts.add(FloatingText(e.x, e.y - 0.3f, text = label, color = if (stompCombo >= 3) Color.YELLOW else Color.WHITE))
                            player.score += pts
                        }
                        EnemyType.SHELLBACK -> {
                            if (!e.inShell) {
                                e.inShell = true
                                e.shellMoving = false
                                e.vx = 0f
                                e.height = 0.78f
                                e.y += 0.22f
                                awardScore(e.x, e.y, pts)
                            } else if (!e.shellMoving) {
                                e.shellMoving = true
                                e.vx = if (player.x < e.x) 10.5f else -10.5f
                                sound.playKick()
                            } else {
                                e.shellMoving = false
                                e.vx = 0f
                            }
                        }
                        EnemyType.KING_REX -> damageBoss(e)
                        else -> {}
                    }
                } else if (e.type == EnemyType.SHELLBACK && e.inShell && !e.shellMoving) {
                    e.shellMoving = true
                    e.vx = if (player.x < e.x) 10.5f else -10.5f
                    sound.playKick()
                } else {
                    damagePlayer()
                }
            }
        }
    }

    private fun updateBoss(boss: Enemy, dt: Float) {
        if (abs(boss.x - player.x) > 16f) return
        boss.bossTimer += dt
        boss.facingRight = player.x > boss.x

        boss.vy = (boss.vy + 28f * dt).coerceAtMost(16f)
        boss.x += boss.vx * dt
        if (boss.x < cols - 19f) {
            boss.x = cols - 19f
            boss.vx = 2.2f
        } else if (boss.x > cols - 5.5f) {
            boss.x = cols - 5.5f
            boss.vx = -2.2f
        }

        boss.y += boss.vy * dt
        boss.onGround = false
        val bRow = (boss.y + boss.height).toInt().coerceIn(0, rows - 1)
        val cMid = (boss.x + boss.width * 0.5f).toInt().coerceIn(0, cols - 1)
        if (isSolid(grid[bRow][cMid]) || grid[bRow][cMid] == TileType.BRIDGE) {
            boss.y = bRow - boss.height
            boss.vy = 0f
            boss.onGround = true
        }

        if (boss.bossTimer >= 2.1f) {
            boss.bossTimer = 0f
            if (boss.onGround && Random.nextFloat() < 0.5f) {
                boss.vy = -10.5f
            }
            val dir = if (boss.facingRight) 1f else -1f
            fireballs.add(
                Fireball(
                    x = boss.x + (if (boss.facingRight) boss.width else -0.4f),
                    y = boss.y + 0.55f,
                    vx = dir * 7.8f,
                    vy = 0f,
                    fromPlayer = false
                )
            )
            sound.playBossRoar()
        }
    }

    private fun damageBoss(boss: Enemy) {
        boss.hp--
        shakeTimer = 0.16f
        shakeIntensity = 9f
        spawnSparkles(boss.x + boss.width * 0.5f, boss.y + 0.5f, Color.rgb(255, 100, 30), 14)
        if (boss.hp <= 0) {
            boss.alive = false
            bossDefeated = true
            player.enemiesDefeated++
            awardScore(boss.x, boss.y, 5000)
            triggerWin(2000)
        }
    }

    private fun defeatEnemy(e: Enemy, pts: Int) {
        e.alive = false
        player.enemiesDefeated++
        awardScore(e.x, e.y, pts)
        spawnSparkles(e.x + e.width * 0.5f, e.y + e.height * 0.5f, Color.WHITE, 8)
    }

    private fun updateItems(dt: Float) {
        for (item in items) {
            if (!item.active) continue
            item.animTimer += dt

            if (item.type == ItemType.POPUP_COIN) {
                item.y += item.vy * dt
                item.vy += 22f * dt
                item.spawnTimer += dt
                if (item.spawnTimer > 0.45f) item.active = false
                continue
            }

            if (item.type == ItemType.MUSHROOM || item.type == ItemType.ONE_UP || item.type == ItemType.STARMAN) {
                item.vy = (item.vy + 26f * dt).coerceAtMost(15f)
                item.x += item.vx * dt

                val cCol = if (item.vx > 0f) (item.x + 0.8f).toInt() else item.x.toInt()
                val rMid = (item.y + 0.4f).toInt().coerceIn(0, rows - 1)
                if (cCol in 0 until cols && isSolid(grid[rMid][cCol])) {
                    item.vx = -item.vx
                }

                item.y += item.vy * dt
                val bRow = (item.y + 0.85f).toInt().coerceIn(0, rows - 1)
                val cMid = (item.x + 0.4f).toInt().coerceIn(0, cols - 1)
                if (isSolid(grid[bRow][cMid]) || grid[bRow][cMid] == TileType.BRIDGE) {
                    item.y = bRow - 0.85f
                    item.vy = if (item.type == ItemType.STARMAN) -9.5f else 0f
                }
                if (item.y > rows + 2f) item.active = false
            }

            if (rectsOverlap(player.x, player.y, player.width, player.height, item.x, item.y, 0.8f, 0.8f)) {
                item.active = false
                when (item.type) {
                    ItemType.WORLD_COIN -> collectCoin(item.x, item.y, popup = false)
                    ItemType.STAR_COIN -> {
                        val idx = item.starCoinIndex.coerceIn(0, 2)
                        player.starCoinsCollected[idx] = true
                        player.score += 1000
                        floatingTexts.add(FloatingText(item.x, item.y - 0.3f, text = "STAR COIN!", color = Color.rgb(255, 215, 0), scale = 1.2f))
                        spawnSparkles(item.x + 0.4f, item.y + 0.4f, Color.rgb(255, 220, 50), 14)
                        sound.playStarCoin()
                    }
                    ItemType.MUSHROOM -> {
                        if (player.powerState == PowerState.SMALL) {
                            player.powerState = PowerState.BIG
                            player.y -= 0.55f
                            player.height = 1.45f
                        }
                        awardScore(item.x, item.y, 1000)
                        sound.playPowerUp()
                    }
                    ItemType.FIRE_FLOWER -> {
                        if (player.powerState == PowerState.SMALL) {
                            player.y -= 0.55f
                            player.height = 1.45f
                        }
                        player.powerState = PowerState.FIRE
                        awardScore(item.x, item.y, 1000)
                        sound.playPowerUp()
                    }
                    ItemType.STARMAN -> {
                        player.starTimer = 9.0f
                        awardScore(item.x, item.y, 1000)
                        sound.playPowerUp()
                    }
                    ItemType.ONE_UP -> {
                        player.lives++
                        floatingTexts.add(FloatingText(item.x, item.y - 0.3f, text = "1-UP!", color = Color.GREEN, scale = 1.2f))
                        sound.play1Up()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun updateFireballs(dt: Float) {
        for (fb in fireballs) {
            if (!fb.active) continue
            fb.lifeTimer -= dt
            if (fb.lifeTimer <= 0f) {
                fb.active = false
                continue
            }

            if (fb.fromPlayer) fb.vy = (fb.vy + 34f * dt).coerceAtMost(14f)
            fb.x += fb.vx * dt
            fb.y += fb.vy * dt

            val c = fb.x.toInt().coerceIn(0, cols - 1)
            val r = fb.y.toInt().coerceIn(0, rows - 1)
            if (isSolid(grid[r][c])) {
                if (fb.fromPlayer && fb.bounces < 4) {
                    fb.y = r - 0.2f
                    fb.vy = -8.5f
                    fb.bounces++
                } else {
                    fb.active = false
                    spawnSparkles(fb.x, fb.y, Color.rgb(255, 120, 0), 5)
                    continue
                }
            }

            if (fb.fromPlayer) {
                for (e in enemies) {
                    if (e.alive && !e.squashed && rectsOverlap(fb.x - 0.2f, fb.y - 0.2f, 0.4f, 0.4f, e.x, e.y, e.width, e.height)) {
                        fb.active = false
                        if (e.type == EnemyType.KING_REX) damageBoss(e) else defeatEnemy(e, 200)
                        sound.playKick()
                        break
                    }
                }
            } else {
                if (rectsOverlap(fb.x - 0.25f, fb.y - 0.25f, 0.5f, 0.5f, player.x, player.y, player.width, player.height)) {
                    fb.active = false
                    if (player.starTimer <= 0f) damagePlayer()
                }
            }
        }
    }

    private fun collectCoin(x: Float, y: Float, popup: Boolean) {
        player.coins++
        player.score += 100
        if (player.coins % 100 == 0) {
            player.lives++
            sound.play1Up()
        } else {
            sound.playCoin()
        }
        if (popup) {
            items.add(Item(ItemType.POPUP_COIN, x - 0.3f, y - 0.9f, vy = -7.5f))
        }
        spawnSparkles(x, y, Color.rgb(255, 215, 0), 6)
        floatingTexts.add(FloatingText(x - 0.3f, y - 0.4f, text = "+100", color = Color.rgb(255, 235, 100)))
    }

    private fun awardScore(x: Float, y: Float, pts: Int) {
        player.score += pts
        floatingTexts.add(FloatingText(x, y - 0.3f, text = "+$pts", color = Color.WHITE))
    }

    private fun damagePlayer() {
        if (player.invincibleTimer > 0f || player.dead || player.won) return
        player.tookDamage = true
        shakeTimer = 0.18f
        shakeIntensity = 10f
        if (player.powerState != PowerState.SMALL) {
            player.powerState = PowerState.SMALL
            player.height = 0.90f
            player.invincibleTimer = 2.0f
            player.vy = -7.0f
            sound.playHurt()
        } else {
            killPlayer()
        }
    }

    private fun killPlayer() {
        if (player.dead) return
        player.dead = true
        player.deathTimer = 0f
        player.vy = -13.5f
        player.vx = 0f
        player.lives = (player.lives - 1).coerceAtLeast(0)
        sound.playHurt()
    }

    fun isBonusMet(): Boolean {
        val obj = mission.bonusObjective
        return when (obj.type) {
            BonusObjectiveType.TIME_UNDER -> timeLeft.toInt() >= obj.target
            BonusObjectiveType.DEFEAT_ENEMIES -> player.enemiesDefeated >= obj.target
            BonusObjectiveType.COLLECT_COINS -> player.coins >= obj.target
            BonusObjectiveType.NO_DAMAGE -> !player.tookDamage
        }
    }

    fun computeEarnedStars(): Int {
        var stars = 1
        if (player.starCoinsCollected.all { it }) stars++
        if (isBonusMet()) stars++
        return stars
    }

    private fun isSolid(t: TileType): Boolean {
        return when (t) {
            TileType.GROUND, TileType.BRICK, TileType.QUESTION_COIN, TileType.QUESTION_POWERUP,
            TileType.QUESTION_STAR, TileType.QUESTION_1UP, TileType.USED_BLOCK, TileType.HARD_BLOCK,
            TileType.PIPE_TL, TileType.PIPE_TR, TileType.PIPE_BL, TileType.PIPE_BR,
            TileType.WARP_PIPE_TL, TileType.WARP_PIPE_TR, TileType.CANNON,
            TileType.ICE_BLOCK -> true
            else -> false
        }
    }

    private fun rectsOverlap(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
    }

    private fun spawnDust(x: Float, y: Float, vxBias: Float) {
        for (i in 0 until 4) {
            particles.add(
                Particle(
                    x = x + (Random.nextFloat() - 0.5f) * 0.3f,
                    y = y - 0.1f,
                    vx = vxBias + (Random.nextFloat() - 0.5f) * 2.2f,
                    vy = -Random.nextFloat() * 1.8f,
                    size = 0.14f + Random.nextFloat() * 0.1f,
                    color = Color.argb(180, 240, 240, 230),
                    life = 0.32f,
                    maxLife = 0.32f,
                    gravity = 2f,
                    shape = 1
                )
            )
        }
    }

    private fun spawnBrickDebris(x: Float, y: Float) {
        val dirs = arrayOf(-3.2f to -8.5f, 3.2f to -8.5f, -2.2f to -5.5f, 2.2f to -5.5f)
        for ((vx, vy) in dirs) {
            particles.add(
                Particle(
                    x = x, y = y, vx = vx, vy = vy,
                    size = 0.28f,
                    color = Color.rgb(190, 88, 42),
                    life = 0.85f, maxLife = 0.85f,
                    gravity = 26f,
                    rotSpeed = (Random.nextFloat() - 0.5f) * 14f,
                    shape = 0
                )
            )
        }
    }

    private fun spawnSparkles(x: Float, y: Float, color: Int, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 6.283f
            val spd = 2.0f + Random.nextFloat() * 4.5f
            particles.add(
                Particle(
                    x = x, y = y,
                    vx = kotlin.math.cos(angle) * spd,
                    vy = kotlin.math.sin(angle) * spd - 1.5f,
                    size = 0.18f,
                    color = color,
                    life = 0.45f,
                    maxLife = 0.45f,
                    gravity = 8f,
                    shape = 2
                )
            )
        }
    }

    private fun updateParticles(dt: Float) {
        var i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particles.removeAt(i)
            } else {
                p.vy += p.gravity * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rotation += p.rotSpeed * dt
            }
            i--
        }
    }

    private fun updateFloatingTexts(dt: Float) {
        var i = floatingTexts.size - 1
        while (i >= 0) {
            val ft = floatingTexts[i]
            ft.life -= dt
            if (ft.life <= 0f) {
                floatingTexts.removeAt(i)
            } else {
                ft.y += ft.vy * dt
            }
            i--
        }
    }

    private fun updateBlockBumps(dt: Float) {
        var i = blockBumps.size - 1
        while (i >= 0) {
            blockBumps[i].timer -= dt
            if (blockBumps[i].timer <= 0f) blockBumps.removeAt(i)
            i--
        }
    }
}
