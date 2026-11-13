package com.superplumber.odyssey

enum class Biome {
    PLAINS,
    CAVERN,
    SKY,
    SUNSET,
    ICE,
    NIGHT,
    VOLCANO,
    CASTLE
}

enum class TileType {
    EMPTY,
    GROUND,
    BRICK,
    QUESTION_COIN,
    QUESTION_POWERUP,
    QUESTION_STAR,
    QUESTION_1UP,
    USED_BLOCK,
    HARD_BLOCK,
    PIPE_TL,
    PIPE_TR,
    PIPE_BL,
    PIPE_BR,
    WARP_PIPE_TL,
    WARP_PIPE_TR,
    ICE_BLOCK,
    BRIDGE,
    CRUMBLE_BRIDGE,
    LAVA,
    SPRING,
    AXE,
    FLAG_POLE,
    FLAG_TOP,
    CHECKPOINT,
    CANNON
}

enum class PowerState {
    SMALL,
    BIG,
    FIRE
}

enum class EnemyType {
    SHROOMLING,
    SHELLBACK,
    WINGED_SHROOM,
    PIPE_CHOMPER,
    SPINY,
    BULLET,
    KING_REX
}

enum class ItemType {
    MUSHROOM,
    FIRE_FLOWER,
    STARMAN,
    ONE_UP,
    POPUP_COIN,
    STAR_COIN,
    WORLD_COIN
}

enum class BonusObjectiveType {
    TIME_UNDER,
    DEFEAT_ENEMIES,
    COLLECT_COINS,
    NO_DAMAGE
}

data class BonusObjective(
    val type: BonusObjectiveType,
    val target: Int,
    val description: String
)

data class MovingPlatformDef(
    val startCol: Float,
    val startRow: Float,
    val endCol: Float,
    val endRow: Float,
    val widthTiles: Int,
    val speed: Float
)

data class MissionDef(
    val id: Int,
    val name: String,
    val subtitle: String,
    val biome: Biome,
    val timeLimitSec: Int,
    val bonusObjective: BonusObjective,
    val rows: List<String>,
    val movingPlatforms: List<MovingPlatformDef> = emptyList()
)

data class MissionSave(
    val unlocked: Boolean,
    val stars: Int,
    val bestTimeSec: Int,
    val highScore: Int
)

data class CrumbleState(
    val col: Int,
    val row: Int,
    var timer: Float = 0.55f,
    var fallen: Boolean = false,
    var respawnTimer: Float = 3.5f
)

data class Player(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var width: Float = 0.78f,
    var height: Float = 0.92f,
    var facingRight: Boolean = true,
    var onGround: Boolean = false,
    var onIce: Boolean = false,
    var onWallDir: Int = 0,
    var isGroundPounding: Boolean = false,
    var coyoteTimer: Float = 0f,
    var jumpBufferTimer: Float = 0f,
    var powerState: PowerState = PowerState.SMALL,
    var invincibleTimer: Float = 0f,
    var starTimer: Float = 0f,
    var runAnimTimer: Float = 0f,
    var squashX: Float = 1f,
    var squashY: Float = 1f,
    var isCrouching: Boolean = false,
    var isSkidding: Boolean = false,
    var lives: Int = 3,
    var coins: Int = 0,
    var score: Int = 0,
    val starCoinsCollected: BooleanArray = booleanArrayOf(false, false, false),
    var enemiesDefeated: Int = 0,
    var tookDamage: Boolean = false,
    var fireballCooldown: Float = 0f,
    var dead: Boolean = false,
    var deathTimer: Float = 0f,
    var won: Boolean = false,
    var winTimer: Float = 0f
)

data class Enemy(
    val id: Int,
    val type: EnemyType,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var width: Float = 0.85f,
    var height: Float = 0.85f,
    var alive: Boolean = true,
    var squashed: Boolean = false,
    var squashTimer: Float = 0f,
    var inShell: Boolean = false,
    var shellMoving: Boolean = false,
    var animTimer: Float = 0f,
    var onGround: Boolean = false,
    val baseY: Float = y,
    var hp: Int = 1,
    var maxHp: Int = 1,
    var bossTimer: Float = 0f,
    var facingRight: Boolean = false
)

data class Item(
    val type: ItemType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var active: Boolean = true,
    var spawnTimer: Float = 0f,
    var animTimer: Float = 0f,
    val starCoinIndex: Int = -1
)

data class Fireball(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var active: Boolean = true,
    val fromPlayer: Boolean = true,
    var bounces: Int = 0,
    var lifeTimer: Float = 3.5f
)

data class MovingPlatform(
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float = 0.45f,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val speed: Float,
    var progress: Float = 0f,
    var forward: Boolean = true,
    var dx: Float = 0f,
    var dy: Float = 0f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    val color: Int,
    var life: Float,
    val maxLife: Float,
    val gravity: Float = 18f,
    var rotation: Float = 0f,
    val rotSpeed: Float = 0f,
    val shape: Int = 0
)

data class FloatingText(
    var x: Float,
    var y: Float,
    var vy: Float = -1.8f,
    val text: String,
    val color: Int,
    var life: Float = 0.9f,
    val maxLife: Float = 0.9f,
    val scale: Float = 1f
)

data class BlockBump(
    val col: Int,
    val row: Int,
    var timer: Float = 0.18f
)

enum class GameScreen {
    TITLE,
    MISSION_SELECT,
    PLAYING,
    PAUSED,
    MISSION_COMPLETE,
    GAME_OVER
}
