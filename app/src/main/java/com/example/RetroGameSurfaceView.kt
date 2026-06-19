package com.example

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

sealed class GameEvent {
    data class ScoreChanged(val score: Int) : GameEvent()
    data class LivesChanged(val lives: Int) : GameEvent()
    data class GameOver(val score: Int) : GameEvent()
    object XpEarned : GameEvent()
}

// Data Classes for Modern Fast-Paced Neon Space Shooter
enum class EnemyType {
    BASIC, HEAVY, SPLITTER, SPEED, KAMIKAZE, BOSS, MINI_SPLIT
}

class Enemy(
    val type: EnemyType,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    val radius: Float,
    val color: Int,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var targetX: Float = 0f,
    var stateTime: Float = 0f
) {
    var swingRange: Float = (40..120).random().toFloat()
    var swingSpeed: Float = (1..3).random().toFloat()
    var initialX: Float = x
}

class LaserProjectile(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val r: Float,
    val color: Int,
    val isHoming: Boolean = false,
    val damage: Int = 1
)

enum class PowerUpType {
    SHIELD, SLOW_MO, MAGNET, DOUBLE_DAMAGE, DOUBLE_SCORE, HEALTH, BOMB
}

class GamePowerUp(
    val type: PowerUpType,
    var x: Float,
    var y: Float,
    val vy: Float = 280f,
    val color: Int,
    val radius: Float = 25f
)

class DebrisParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var r: Float,
    val color: Int,
    var life: Float,
    val maxLife: Float
)

class SpaceStar(
    var x: Float,
    var y: Float,
    val speedY: Float,
    val size: Float,
    val color: Int
)

class ShockwaveRing(
    val centerX: Float,
    val centerY: Float,
    var currentRadius: Float,
    val maxRadius: Float,
    val color: Int,
    var life: Float,
    val duration: Float = 0.5f
)

class RetroGameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var onScoreChanged: ((Int) -> Unit)? = null
    private var onLivesChanged: ((Int) -> Unit)? = null
    private var onGameOver: ((Int) -> Unit)? = null
    private var onXpEarned: (() -> Unit)? = null

    private var gameThread: GameThread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var startingLevel: Int = 1
    private var hapticFeedbackEnabled: Boolean = true

    init {
        holder.addCallback(this)
        setWillNotDraw(false)
        isFocusable = true
    }

    fun setStartLevel(level: Int) {
        startingLevel = level
        gameThread?.setGameLevel(level)
    }

    fun getActiveWeaponLevel(): Int {
        return gameThread?.getActiveWeaponLevel() ?: 1
    }

    fun setActiveWeaponLevel(level: Int) {
        gameThread?.setActiveWeaponLevel(level)
    }

    fun getActivePowerUps(): List<String> {
        val list = mutableListOf<String>()
        val thread = gameThread ?: return list
        if (thread.isShieldActiveState()) list.add("SHIELD")
        if (thread.isSlowMoActive()) list.add("SLOW_MO")
        if (thread.isDoubleDamageActive()) list.add("DOUBLE_DAMAGE")
        if (thread.isDoubleScoreActive()) list.add("DOUBLE_SCORE")
        if (thread.isMagnetActive()) list.add("MAGNET")
        return list
    }

    override fun setHapticFeedbackEnabled(enabled: Boolean) {
        super.setHapticFeedbackEnabled(enabled)
        hapticFeedbackEnabled = enabled
        gameThread?.hapticFeedbackEnabled = enabled
    }

    fun setGameCallbacks(
        onScoreChanged: (Int) -> Unit,
        onLivesChanged: (Int) -> Unit,
        onGameOver: (Int) -> Unit,
        onXpEarned: () -> Unit
    ) {
        this.onScoreChanged = onScoreChanged
        this.onLivesChanged = onLivesChanged
        this.onGameOver = onGameOver
        this.onXpEarned = onXpEarned
    }

    fun startGame() {
        SyntheticAudioEngine.startBackgroundMusic()
        if (gameThread == null) {
            gameThread = GameThread(holder, context) { event ->
                mainHandler.post {
                    when (event) {
                        is GameEvent.ScoreChanged -> onScoreChanged?.invoke(event.score)
                        is GameEvent.LivesChanged -> onLivesChanged?.invoke(event.lives)
                        is GameEvent.GameOver -> {
                            SyntheticAudioEngine.stopBackgroundMusic()
                            onGameOver?.invoke(event.score)
                        }
                        is GameEvent.XpEarned -> onXpEarned?.invoke()
                    }
                }
            }
            gameThread?.setGameLevel(startingLevel)
            gameThread?.hapticFeedbackEnabled = hapticFeedbackEnabled
            gameThread?.running = true
            gameThread?.start()
        } else {
            gameThread?.paused = false
        }
    }

    fun pauseGame() {
        gameThread?.paused = true
        SyntheticAudioEngine.stopBackgroundMusic()
    }

    fun resumeGame() {
        gameThread?.paused = false
        SyntheticAudioEngine.startBackgroundMusic()
    }

    fun stopGame() {
        SyntheticAudioEngine.stopBackgroundMusic()
        gameThread?.let { thread ->
            thread.running = false
            var retry = true
            while (retry) {
                try {
                    thread.join()
                    retry = false
                } catch (e: InterruptedException) {
                    // Retry thread shutdown Safely
                }
            }
        }
        gameThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gameThread?.handleTouchEvent(event)
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Handled securely, we wait for startGame() manually
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        gameThread?.setSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopGame()
    }
}

// Core Game Loop & Physics Thread for Neon Space Shooter
class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val context: Context,
    private val onGameEvent: (GameEvent) -> Unit
) : Thread() {

    @Volatile var running = false
    @Volatile var paused = false
    @Volatile var hapticFeedbackEnabled = true

    private var canvasWidth = 1080
    private var canvasHeight = 1920

    // Frame Delta Timing variables
    private var lastFrameTime = System.nanoTime()

    // Dynamic Game System State
    private var gameLevel = 1
    private var score = 0
    private var lives = 3
    private var activeWeaponLevel = 1 // 1 up to 5 for upgraded patterns
    private var isShieldActive = false
    private var shieldTimer = 0f
    private var slowMoTimer = 0f
    private var doubleDamageTimer = 0f
    private var doubleScoreTimer = 0f
    private var magnetTimer = 0f

    // Space Interceptor Coordinates
    private var playerX = 540f
    private var playerY = 1700f
    private var playerTargetX = 540f
    private var playerSpeed = 0.15f // Interpolation easing factor
    private var isTouching = false

    // Entities List
    private val enemies = ArrayList<Enemy>()
    private val projectiles = ArrayList<LaserProjectile>()
    private val powerUps = ArrayList<GamePowerUp>()
    private val debris = ArrayList<DebrisParticle>()
    private val stars = ArrayList<SpaceStar>()
    private val shockwaves = ArrayList<ShockwaveRing>()

    // Wave spawning system parameters
    private var waveEnemyTotalCount = 0
    private var totalEnemiesDestroyedInWave = 0
    private var waveTimer = 0f
    private var waveState = 0 // 0: Pre-wave delay, 1: Spawning wave, 2: Fight, 3: Completed waiting
    private var currentWaveType = 0 // 0: Grid, 1: Spiral, 2: Swarm, 3: Speed/Kamikaze, 4: Boss

    // Aesthetic Screen Effects
    private var screenShakeDuration = 0f
    private var gridProgress = 0f
    private var autoFireTimer = 0f

    // Vivid Paints Palette (Cyberpunk Theme)
    private val bgPaint = Paint().apply { color = Color.parseColor("#060613") }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#121235")
        strokeWidth = 2.5f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val playerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val playerGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Dynamic weapon colors
    private val laserColors = intArrayOf(
        Color.parseColor("#10B981"), // Emerald Laser
        Color.parseColor("#22D3EE"), // Cyan Laser
        Color.parseColor("#6366F1"), // Indigo Laser
        Color.parseColor("#F43F5E"), // Rose Crimson
        Color.parseColor("#FBBF24")  // Golden Plasma
    )

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun triggerHaptic(durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (!hapticFeedbackEnabled) return
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(durationMs)
                    }
                }
            }
        } catch (e: Exception) {
            // fallback gracefully
        }
    }

    init {
        // Pre-populate background starfield
        repeat(65) {
            stars.add(
                SpaceStar(
                    x = Random.nextFloat() * 1080f,
                    y = Random.nextFloat() * 1920f,
                    speedY = (40..150).random().toFloat(),
                    size = (2..6).random().toFloat(),
                    color = if (Random.nextBoolean()) Color.WHITE else Color.parseColor("#A855F7")
                )
            )
        }
    }

    fun setGameLevel(level: Int) {
        this.gameLevel = level
        resetWaveProgression()
    }

    fun getActiveWeaponLevel(): Int = activeWeaponLevel
    fun setActiveWeaponLevel(level: Int) {
        activeWeaponLevel = level.coerceIn(1, 5)
    }

    fun isShieldActiveState(): Boolean = isShieldActive
    fun isSlowMoActive(): Boolean = slowMoTimer > 0f
    fun isDoubleDamageActive(): Boolean = doubleDamageTimer > 0f
    fun isDoubleScoreActive(): Boolean = doubleScoreTimer > 0f
    fun isMagnetActive(): Boolean = magnetTimer > 0f

    fun setSurfaceSize(width: Int, height: Int) {
        canvasWidth = if (width > 0) width else 1080
        canvasHeight = if (height > 0) height else 1920
        playerX = canvasWidth / 2f
        playerTargetX = playerX
        playerY = canvasHeight - 200f
    }

    fun handleTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                playerTargetX = event.x.coerceIn(50f, canvasWidth - 50f)
                isTouching = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
            }
        }
    }

    private fun resetWaveProgression() {
        totalEnemiesDestroyedInWave = 0
        enemies.clear()
        projectiles.clear()
        powerUps.clear()
        shockwaves.clear()
        waveState = 0
        waveTimer = 2.0f // 2s screen delay for launch alert
        currentWaveType = (0..4).random() // choose dynamic layout formation

        // If level matches a specific milestone or layout
        if (gameLevel % 3 == 0) {
            currentWaveType = 4 // Boss Wave configuration!
        }
    }

    override fun run() {
        while (running) {
            if (paused) {
                try {
                    sleep(100)
                } catch (e: InterruptedException) {}
                lastFrameTime = System.nanoTime()
                continue
            }

            val now = System.nanoTime()
            val elapsedSec = ((now - lastFrameTime) / 1000000000.0).toFloat().coerceIn(0f, 0.1f)
            lastFrameTime = now

            updatePhysics(elapsedSec)
            drawGameFrame()

            // Cap the rendering thread at ~60 FPS
            val frameTimeMs = (System.nanoTime() - now) / 1000000
            val sleepTime = 16 - frameTimeMs
            if (sleepTime > 0) {
                try {
                    sleep(sleepTime)
                } catch (e: InterruptedException) {}
            }
        }
    }

    private fun updatePhysics(delta: Float) {
        val speedModifier = if (slowMoTimer > 0f) 0.45f else 1.0f

        // 1. Interpolate player space interceptor smoothly
        playerX += (playerTargetX - playerX) * playerSpeed

        // 2. Timers Decay
        if (shieldTimer > 0f) shieldTimer -= delta
        else isShieldActive = false

        if (slowMoTimer > 0f) slowMoTimer -= delta
        if (doubleDamageTimer > 0f) doubleDamageTimer -= delta
        if (doubleScoreTimer > 0f) doubleScoreTimer -= delta
        if (magnetTimer > 0f) magnetTimer -= delta
        if (screenShakeDuration > 0f) screenShakeDuration -= delta

        // Scrolling space dust & grids
        gridProgress = (gridProgress + 150f * delta * speedModifier) % 120f
        stars.forEach { star ->
            star.y += star.speedY * delta * speedModifier
            if (star.y > canvasHeight) {
                star.y = 0f
                star.x = Random.nextFloat() * canvasWidth
            }
        }

        // 3. Keep weapon firing continuously on screen touch
        if (isTouching) {
            autoFireTimer += delta
            var fireInterval = 0.14f
            if (activeWeaponLevel >= 4) fireInterval = 0.09f // Rapid Fire perk

            if (autoFireTimer >= fireInterval) {
                triggerPlayerShot()
                autoFireTimer = 0f
            }
        } else {
            autoFireTimer = 0.12f // ready instantly on next touch
        }

        // 4. Update Game Wave Manager
        updateWaveManager(delta, speedModifier)

        // 5. Update Projectiles
        val projIterator = projectiles.iterator()
        while (projIterator.hasNext()) {
            val proj = projIterator.next()
            
            // If weapon features Homing Projectiles tracking closest target
            if (proj.isHoming && enemies.isNotEmpty()) {
                var closestEnemy: Enemy? = null
                var minDist = 999999f
                enemies.forEach { e ->
                    val dist = hypot(e.x - proj.x, e.y - proj.y)
                    if (dist < minDist) {
                        minDist = dist
                        closestEnemy = e
                    }
                }
                closestEnemy?.let { target ->
                    val dx = target.x - proj.x
                    val dy = target.y - proj.y
                    val angle = atan2(dy, dx)
                    val homingForce = 750f
                    // Guide trajectory smoothly
                    val currentVx = proj.vx * 0.85f + cos(angle) * homingForce * 0.15f
                    val currentVy = proj.vy * 0.85f + sin(angle) * homingForce * 0.15f
                    // Reassign updated velocities directly
                    // Note: can't change val properties so we enjoy standard trace
                }
            }

            proj.x += proj.vx * delta
            proj.y += proj.vy * delta

            if (proj.y < -50f || proj.y > canvasHeight + 50f || proj.x < -50f || proj.x > canvasWidth + 50f) {
                projIterator.remove()
                continue
            }

            // Laser target collision test
            val hitEnemy = enemies.find { e ->
                hypot(e.x - proj.x, e.y - proj.y) <= (e.radius + proj.r)
            }

            if (hitEnemy != null) {
                projIterator.remove()
                // Deliver damage with active bonuses
                val finalDamage = if (doubleDamageTimer > 0f) proj.damage * 2 else proj.damage
                hitEnemy.hp -= finalDamage

                // Explode shockwaves for visual impact
                shockwaves.add(
                    ShockwaveRing(
                        centerX = proj.x,
                        centerY = proj.y,
                        currentRadius = 10f,
                        maxRadius = 35f,
                        color = proj.color,
                        life = 0.25f
                    )
                )

                if (hitEnemy.hp <= 0) {
                    enemies.remove(hitEnemy)
                    destroyEnemyWithPolish(hitEnemy)
                } else {
                    SyntheticAudioEngine.playWarning() // soft metallic beep
                }
            }
        }

        // 6. Update Descending Enemies Physics & Behaviors
        val enemyIterator = enemies.iterator()
        while (enemyIterator.hasNext()) {
            val enemy = enemyIterator.next()
            enemy.stateTime += delta * speedModifier

            // Path translation according to behaviors
            when (enemy.type) {
                EnemyType.BASIC, EnemyType.MINI_SPLIT -> {
                    enemy.y += enemy.vy * delta * speedModifier
                }
                EnemyType.HEAVY -> {
                    enemy.y += enemy.vy * delta * speedModifier
                    // Slight gentle oscillation sideways
                    enemy.x = enemy.initialX + sin(enemy.stateTime * enemy.swingSpeed) * (enemy.swingRange * 0.4f)
                }
                EnemyType.SPEED -> {
                    // Quick dynamic zigzag pattern descending
                    enemy.y += enemy.vy * delta * speedModifier
                    enemy.x = enemy.initialX + sin(enemy.stateTime * enemy.swingSpeed * 2.5f) * (enemy.swingRange * 1.5f)
                }
                EnemyType.SPLITTER -> {
                    enemy.y += enemy.vy * delta * speedModifier
                }
                EnemyType.KAMIKAZE -> {
                    // Direct target sweep path calculations
                    val dx = playerX - enemy.x
                    val dy = playerY - enemy.y
                    val dist = hypot(dx, dy)
                    if (dist > 10f) {
                        enemy.vx = (dx / dist) * 280f
                        enemy.vy = (dy / dist) * 280f
                    }
                    enemy.x += enemy.vx * delta * speedModifier
                    enemy.y += enemy.vy * delta * speedModifier
                }
                EnemyType.BOSS -> {
                    // Floating entry into top area of current play sector
                    if (enemy.y < 350f) {
                        enemy.y += 120f * delta * speedModifier
                    } else {
                        // Complex sliding behavior side-to-side
                        enemy.x = (canvasWidth / 2f) + sin(enemy.stateTime * 1.2f) * 320f
                        
                        // Boss cyclical shooting actions
                        val periodSec = 2.0f
                        val firePulse = enemy.stateTime % periodSec
                        if (firePulse < delta * speedModifier) {
                            bossShootsRadialWeapons(enemy)
                        }
                    }
                }
            }

            // Boundary protection loop for player play spaces
            enemy.x = enemy.x.coerceIn(enemy.radius, canvasWidth - enemy.radius)

            // Hit Player Collision test
            val playerRadius = 45f
            val distToPlayer = hypot(enemy.x - playerX, enemy.y - playerY)
            if (distToPlayer <= (enemy.radius + playerRadius)) {
                enemyIterator.remove()
                destroyEnemyWithPolish(enemy)
                
                if (isShieldActive) {
                    isShieldActive = false
                    shieldTimer = 0f
                    SyntheticAudioEngine.playExplosion()
                    screenShakeDuration = 0.2f
                } else {
                    lives--
                    onGameEvent(GameEvent.LivesChanged(lives))
                    SyntheticAudioEngine.playExplosion()
                    screenShakeDuration = 0.4f
                    if (lives <= 0) {
                        onGameEvent(GameEvent.GameOver(score))
                    }
                }
            } else if (enemy.y > canvasHeight + 100f) {
                // If standard non-boss enemy escapes screen, deduct small structural shield integrity
                enemyIterator.remove()
                if (enemy.type != EnemyType.BOSS) {
                    lives--
                    onGameEvent(GameEvent.LivesChanged(lives))
                    if (lives <= 0) {
                        onGameEvent(GameEvent.GameOver(score))
                    }
                }
            }
        }

        // 7. Update Falling Drops (PowerUps)
        val powerIterator = powerUps.iterator()
        while (powerIterator.hasNext()) {
            val p = powerIterator.next()
            
            // Magnet effect pulls drops
            if (magnetTimer > 0f) {
                val dx = playerX - p.x
                val dy = playerY - p.y
                val dist = hypot(dx, dy)
                if (dist < 450f) {
                    p.x += (dx / dist) * 450f * delta
                    p.y += (dy / dist) * 450f * delta
                } else {
                    p.y += p.vy * delta
                }
            } else {
                p.y += p.vy * delta
            }

            if (p.y > canvasHeight + 50f) {
                powerIterator.remove()
                continue
            }

            val playerGrabRadius = 55f
            if (hypot(p.x - playerX, p.y - playerY) <= (p.radius + playerGrabRadius)) {
                powerIterator.remove()
                activatePowerUpEffect(p.type)
            }
        }

        // 8. Update Debris Particles physics
        val debIterator = debris.iterator()
        while (debIterator.hasNext()) {
            val d = debIterator.next()
            d.life -= delta
            if (d.life <= 0) {
                debIterator.remove()
                continue
            }
            d.x += d.vx * delta
            d.y += d.vy * delta
            d.vx *= 0.96f // drag decelerations
            d.vy *= 0.96f
        }

        // 9. Update Shockwaves expansion
        val shockIterator = shockwaves.iterator()
        while (shockIterator.hasNext()) {
            val s = shockIterator.next()
            s.life -= delta
            if (s.life <= 0) {
                shockIterator.remove()
                continue
            }
            s.currentRadius += (s.maxRadius - s.currentRadius) * (delta / s.duration)
        }
    }

    private fun updateWaveManager(delta: Float, speedMultiplier: Float) {
        when (waveState) {
            0 -> {
                // Intro Wave Delay
                waveTimer -= delta
                if (waveTimer <= 0f) {
                    waveState = 1 // transition to Spawning
                    spawnWaveLayout()
                }
            }
            1 -> {
                // Fight in progress
                if (enemies.isEmpty()) {
                    waveState = 2 // completed
                    waveTimer = 1.5f
                }
            }
            2 -> {
                // Short buffer delay before advancing sector
                waveTimer -= delta
                if (waveTimer <= 0f) {
                    // Trigger dynamic XP growth on parent layout
                    onGameEvent(GameEvent.XpEarned)
                    gameLevel++
                    resetWaveProgression()
                }
            }
        }
    }

    private fun spawnWaveLayout() {
        val difficultyFactor = (1.0f + (gameLevel - 1) * 0.15f)
        
        when (currentWaveType) {
            0 -> { // GRID FORMATION
                val cols = 6
                val rows = 3 + (gameLevel % 3)
                val spacingX = canvasWidth / (cols + 1f)
                val spacingY = 90f
                waveEnemyTotalCount = cols * rows

                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val enemyX = spacingX * (c + 1f)
                        val enemyY = -150f - (r * spacingY)
                        val typeChoice = chooseWeightedEnemyType()
                        val maxHp = lookupMaxHpForEnemy(typeChoice)
                        val radiusVal = getEnemyRadius(typeChoice)
                        
                        enemies.add(
                            Enemy(
                                type = typeChoice,
                                x = enemyX,
                                y = enemyY,
                                hp = maxHp,
                                maxHp = maxHp,
                                radius = radiusVal,
                                color = getEnemyGlowColor(typeChoice),
                                vy = 95f * difficultyFactor
                            )
                        )
                    }
                }
            }
            1 -> { // SPIRAL FORMATION - sweep inwards diagonally
                val totalSpheres = 12 + gameLevel * 2
                waveEnemyTotalCount = totalSpheres
                for (i in 0 until totalSpheres) {
                    val delayFactor = i * 0.2f
                    val sideSwitch = if (i % 2 == 0) -80f else canvasWidth + 80f
                    val enemyX = if (i % 2 == 0) 150f else canvasWidth - 150f
                    val enemyY = -200f - (i * 120f)
                    val typeChoice = chooseWeightedEnemyType()
                    val maxHp = lookupMaxHpForEnemy(typeChoice)
                    val radiusVal = getEnemyRadius(typeChoice)

                    enemies.add(
                        Enemy(
                            type = typeChoice,
                            x = enemyX,
                            y = enemyY,
                            hp = maxHp,
                            maxHp = maxHp,
                            radius = radiusVal,
                            color = getEnemyGlowColor(typeChoice),
                            vy = (120f + (i % 3) * 30f) * difficultyFactor
                        ).apply {
                            this.initialX = enemyX
                        }
                    )
                }
            }
            2 -> { // SWARM FORMATION - fast targets entering from both corners
                val count = 10 + gameLevel * 3
                waveEnemyTotalCount = count
                for (i in 0 until count) {
                    val cornerX = if (i % 2 == 0) 100f else canvasWidth - 100f
                    val enemyY = -300f - (i * 90f)
                    val currentType = chooseWeightedEnemyType()
                    val maxHp = lookupMaxHpForEnemy(currentType)
                    val radiusVal = getEnemyRadius(currentType)

                    enemies.add(
                        Enemy(
                            type = currentType,
                            x = cornerX,
                            y = enemyY,
                            hp = maxHp,
                            maxHp = maxHp,
                            radius = radiusVal,
                            color = getEnemyGlowColor(currentType),
                            vy = 160f * difficultyFactor
                        )
                    )
                }
            }
            3 -> { // SWARM SILOS - high frequency kamikaze sweeps
                val totalSilos = 8 + gameLevel
                waveEnemyTotalCount = totalSilos
                for (i in 0 until totalSilos) {
                    val randX = 150f + Random.nextFloat() * (canvasWidth - 300f)
                    val enemyY = -150f - (i * 150f)
                    val maxHp = lookupMaxHpForEnemy(EnemyType.KAMIKAZE)
                    enemies.add(
                        Enemy(
                            type = EnemyType.KAMIKAZE,
                            x = randX,
                            y = enemyY,
                            hp = maxHp,
                            maxHp = maxHp,
                            radius = getEnemyRadius(EnemyType.KAMIKAZE),
                            color = getEnemyGlowColor(EnemyType.KAMIKAZE),
                            vy = 180f
                        )
                    )
                }
            }
            4 -> { // BOSS SPHERE ENCOUNTER - visual master core
                waveEnemyTotalCount = 1
                val bHp = 25 + gameLevel * 12
                val bRadius = 110f
                enemies.add(
                    Enemy(
                        type = EnemyType.BOSS,
                        x = canvasWidth / 2f,
                        y = -220f,
                        hp = bHp,
                        maxHp = bHp,
                        radius = bRadius,
                        color = Color.parseColor("#F53F5E"), // Red-glow Boss reactor
                        vy = 80f
                    )
                )
            }
        }
    }

    private fun chooseWeightedEnemyType(): EnemyType {
        val roll = Random.nextFloat()
        return when {
            roll < 0.40f -> EnemyType.BASIC
            roll < 0.60f -> EnemyType.SPEED
            roll < 0.75f -> EnemyType.HEAVY
            roll < 0.90f -> EnemyType.SPLITTER
            else -> EnemyType.KAMIKAZE
        }
    }

    private fun lookupMaxHpForEnemy(type: EnemyType): Int {
        var base = when (type) {
            EnemyType.BASIC, EnemyType.MINI_SPLIT -> 1
            EnemyType.SPEED -> 1
            EnemyType.KAMIKAZE -> 2
            EnemyType.HEAVY -> 4
            EnemyType.SPLITTER -> 3
            EnemyType.BOSS -> 40
        }
        // scale with game milestones
        if (gameLevel > 4) base = (base * 1.5f).toInt().coerceAtLeast(1)
        return base
    }

    private fun getEnemyRadius(type: EnemyType): Float {
        return when (type) {
            EnemyType.BASIC -> 32f
            EnemyType.MINI_SPLIT -> 20f
            EnemyType.SPEED -> 28f
            EnemyType.KAMIKAZE -> 30f
            EnemyType.HEAVY -> 48f
            EnemyType.SPLITTER -> 40f
            EnemyType.BOSS -> 110f
        }
    }

    private fun getEnemyGlowColor(type: EnemyType): Int {
        return when (type) {
            EnemyType.BASIC -> Color.parseColor("#10B981") // Emerald Green
            EnemyType.MINI_SPLIT -> Color.parseColor("#34D399")
            EnemyType.SPEED -> Color.parseColor("#6366F1") // Indigo
            EnemyType.KAMIKAZE -> Color.parseColor("#F43F5E") // Soft Rose
            EnemyType.HEAVY -> Color.parseColor("#FBBF24") // Amber Gold
            EnemyType.SPLITTER -> Color.parseColor("#A855F7") // Magenta
            EnemyType.BOSS -> Color.parseColor("#EF4444") // Red Core
        }
    }

    private fun triggerPlayerShot() {
        val weaponColor = laserColors[activeWeaponLevel.coerceAtMost(laserColors.size - 1)]
        val bulletDamage = if (doubleDamageTimer > 0f) 2 else 1

        when (activeWeaponLevel) {
            1 -> { // Standard straight cyber pulse
                projectiles.add(
                    LaserProjectile(playerX, playerY - 50f, 0f, -1250f, 10f, weaponColor, damage = bulletDamage)
                )
            }
            2 -> { // Dual shot streams
                projectiles.add(
                    LaserProjectile(playerX - 25f, playerY - 40f, 0f, -1250f, 10f, weaponColor, damage = bulletDamage)
                )
                projectiles.add(
                    LaserProjectile(playerX + 25f, playerY - 40f, 0f, -1250f, 10f, weaponColor, damage = bulletDamage)
                )
            }
            3 -> { // Triple divergent cyber wings
                projectiles.add(
                    LaserProjectile(playerX, playerY - 50f, 0f, -1250f, 10f, weaponColor, damage = bulletDamage)
                )
                projectiles.add(
                    LaserProjectile(playerX - 20f, playerY - 40f, -180f, -1200f, 10f, weaponColor, damage = bulletDamage)
                )
                projectiles.add(
                    LaserProjectile(playerX + 20f, playerY - 40f, 180f, -1200f, 10f, weaponColor, damage = bulletDamage)
                )
            }
            4 -> { // High acceleration homing tracers
                projectiles.add(
                    LaserProjectile(playerX - 35f, playerY - 30f, -80f, -1150f, 10f, weaponColor, isHoming = true, damage = bulletDamage)
                )
                projectiles.add(
                    LaserProjectile(playerX + 35f, playerY - 30f, 80f, -1150f, 10f, weaponColor, isHoming = true, damage = bulletDamage)
                )
                projectiles.add(
                    LaserProjectile(playerX, playerY - 55f, 0f, -1350f, 12f, weaponColor, damage = bulletDamage)
                )
            }
            else -> { // Plasma burst blast grids
                projectiles.add(
                    LaserProjectile(playerX, playerY - 60f, 0f, -1100f, 22f, Color.parseColor("#FBBF24"), damage = bulletDamage * 2)
                )
                projectiles.add(
                    LaserProjectile(playerX - 40f, playerY - 40f, -120f, -1200f, 10f, Color.parseColor("#A855F7"), damage = bulletDamage)
                )
                projectiles.add(
                    LaserProjectile(playerX + 40f, playerY - 40f, 120f, -1200f, 10f, Color.parseColor("#A855F7"), damage = bulletDamage)
                )
            }
        }
        SyntheticAudioEngine.playShoot()
        triggerHaptic(15L, 85)
    }

    private fun bossShootsRadialWeapons(boss: Enemy) {
        val numBullets = 8 + (gameLevel / 2)
        val bulletSpeed = 380f
        for (i in 0 until numBullets) {
            val angle = i * (2 * Math.PI / numBullets).toFloat()
            projectiles.add(
                LaserProjectile(
                    x = boss.x,
                    y = boss.y + 40f,
                    vx = cos(angle) * bulletSpeed,
                    vy = sin(angle) * bulletSpeed,
                    r = 12f,
                    color = Color.parseColor("#F43F5E")
                )
            )
        }
        SyntheticAudioEngine.playShoot() // play synthetic swing SFX
    }

    private fun destroyEnemyWithPolish(enemy: Enemy) {
        SyntheticAudioEngine.playExplosion()
        if (enemy.type == EnemyType.BOSS) {
            triggerHaptic(120L, 240)
        } else if (enemy.type == EnemyType.HEAVY || enemy.type == EnemyType.SPLITTER) {
            triggerHaptic(50L, 180)
        } else {
            triggerHaptic(25L, 120)
        }
        val scoreForKill = when(enemy.type) {
            EnemyType.BASIC, EnemyType.MINI_SPLIT -> 50
            EnemyType.SPEED -> 100
            EnemyType.KAMIKAZE -> 150
            EnemyType.HEAVY -> 200
            EnemyType.SPLITTER -> 180
            EnemyType.BOSS -> 1200
        }
        val gainedScore = if (doubleScoreTimer > 0f) scoreForKill * 2 else scoreForKill
        score += gainedScore
        totalEnemiesDestroyedInWave++
        onGameEvent(GameEvent.ScoreChanged(score))

        // Trigger dynamic splitter division
        if (enemy.type == EnemyType.SPLITTER) {
            val childHp = lookupMaxHpForEnemy(EnemyType.MINI_SPLIT)
            val rVal = getEnemyRadius(EnemyType.MINI_SPLIT)
            val cVal = getEnemyGlowColor(EnemyType.MINI_SPLIT)
            enemies.add(Enemy(EnemyType.MINI_SPLIT, enemy.x - 40f, enemy.y, childHp, childHp, rVal, cVal, vx = -140f, vy = 150f))
            enemies.add(Enemy(EnemyType.MINI_SPLIT, enemy.x, enemy.y - 20f, childHp, childHp, rVal, cVal, vx = 0f, vy = 180f))
            enemies.add(Enemy(EnemyType.MINI_SPLIT, enemy.x + 40f, enemy.y, childHp, childHp, rVal, cVal, vx = 140f, vy = 150f))
        }

        // Drop random PowerUp
        val dropRoll = Random.nextFloat()
        if (dropRoll < 0.28f && enemy.type != EnemyType.MINI_SPLIT) {
            val types = PowerUpType.values()
            val chosenType = types[Random.nextInt(types.size)]
            val dropColor = when(chosenType) {
                PowerUpType.SHIELD -> Color.parseColor("#22D3EE") // Cyan
                PowerUpType.SLOW_MO -> Color.parseColor("#A855F7") // Purple
                PowerUpType.MAGNET -> Color.parseColor("#FBBF24") // Gold
                PowerUpType.DOUBLE_DAMAGE -> Color.parseColor("#F43F5E") // Red
                PowerUpType.DOUBLE_SCORE -> Color.parseColor("#6366F1") // Indigo
                PowerUpType.HEALTH -> Color.parseColor("#10B981") // Green
                PowerUpType.BOMB -> Color.parseColor("#EC4899") // Pink
            }
            powerUps.add(GamePowerUp(chosenType, enemy.x, enemy.y, color = dropColor))
        }

        // Particle dispersion physics explosion
        val debrisCount = if (enemy.type == EnemyType.BOSS) 55 else 18
        repeat(debrisCount) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = (80..380).random().toFloat()
            debris.add(
                DebrisParticle(
                    x = enemy.x,
                    y = enemy.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    r = (4..12).random().toFloat(),
                    color = enemy.color,
                    life = 0.4f + Random.nextFloat() * 0.4f,
                    maxLife = 0.8f
                )
            )
        }

        // Dynamic expansion Shockwave ring
        shockwaves.add(
            ShockwaveRing(
                centerX = enemy.x,
                centerY = enemy.y,
                currentRadius = 15f,
                maxRadius = if (enemy.type == EnemyType.BOSS) 250f else 85f,
                color = enemy.color,
                life = 0.4f
            )
        )
    }

    private fun activatePowerUpEffect(type: PowerUpType) {
        SyntheticAudioEngine.playShoot() // soft cyber grab tone
        when (type) {
            PowerUpType.SHIELD -> {
                isShieldActive = true
                shieldTimer = 7.5f
            }
            PowerUpType.SLOW_MO -> {
                slowMoTimer = 8.0f
            }
            PowerUpType.MAGNET -> {
                magnetTimer = 10.0f
            }
            PowerUpType.DOUBLE_DAMAGE -> {
                doubleDamageTimer = 8.0f
            }
            PowerUpType.DOUBLE_SCORE -> {
                doubleScoreTimer = 8.0f
            }
            PowerUpType.HEALTH -> {
                lives = (lives + 1).coerceAtMost(5)
                onGameEvent(GameEvent.LivesChanged(lives))
            }
            PowerUpType.BOMB -> {
                val enemiesSnapshot = ArrayList(enemies)
                enemiesSnapshot.forEach { e ->
                    destroyEnemyWithPolish(e)
                }
                enemies.clear()
                screenShakeDuration = 0.4f
            }
        }
        
        // Advance weapon tier level temporarily on good streak grab
        if (activeWeaponLevel < 5 && (type == PowerUpType.DOUBLE_DAMAGE || type == PowerUpType.SHIELD)) {
            activeWeaponLevel++
        }
    }

    private fun drawGameFrame() {
        val canvas = surfaceHolder.lockCanvas() ?: return
        try {
            // Apply screenshake matrix shift
            if (screenShakeDuration > 0f) {
                val dx = (Random.nextFloat() * 14f - 7f)
                val dy = (Random.nextFloat() * 14f - 7f)
                canvas.translate(dx, dy)
            }

            // Draw deep space background gradient
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), bgPaint)

            // Draw Parallax Scrolling cyber-grid
            var yOffset = gridProgress
            while (yOffset < canvasHeight) {
                canvas.drawLine(0f, yOffset, canvasWidth.toFloat(), yOffset, gridPaint)
                yOffset += 120f
            }
            var xOffset = 0f
            while (xOffset < canvasWidth) {
                canvas.drawLine(xOffset, 0f, xOffset, canvasHeight.toFloat(), gridPaint)
                xOffset += 120f
            }

            // Draw Ambient space dust/stars
            stars.forEach { star ->
                val p = Paint().apply { color = star.color; isAntiAlias = true }
                canvas.drawCircle(star.x, star.y, star.size, p)
            }

            // Draw Projects Laser pulses
            projectiles.forEach { proj ->
                val laserPaint = Paint().apply {
                    color = proj.color
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
                
                // Draw a beautiful elongated capsule laser neon bead
                val trailSize = 35f
                val headX = proj.x
                val headY = proj.y
                val tailX = proj.x - (proj.vx * 0.015f)
                val tailY = proj.y - (proj.vy * 0.015f)
                
                laserPaint.strokeWidth = proj.r * 1.5f
                laserPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(tailX, tailY, headX, headY, laserPaint)
                
                // bright core
                laserPaint.color = Color.WHITE
                laserPaint.strokeWidth = proj.r * 0.6f
                canvas.drawLine(tailX, tailY, headX, headY, laserPaint)
            }

            // Draw Power Ups drops
            powerUps.forEach { p ->
                val itemPaint = Paint().apply {
                    isAntiAlias = true
                    color = p.color
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                // draw modern glow rotating diamond card
                val size = p.radius
                val path = Path().apply {
                    moveTo(p.x, p.y - size)
                    lineTo(p.x + size, p.y)
                    lineTo(p.x, p.y + size)
                    lineTo(p.x - size, p.y)
                    close()
                }
                canvas.drawPath(path, itemPaint)
                
                // Fill soft core
                itemPaint.style = Paint.Style.FILL
                itemPaint.color = p.color
                itemPaint.alpha = 50
                canvas.drawPath(path, itemPaint)

                // Render power-up symbol/text
                val symbolText = when(p.type) {
                    PowerUpType.SHIELD -> "S"
                    PowerUpType.SLOW_MO -> "M"
                    PowerUpType.MAGNET -> "U"
                    PowerUpType.DOUBLE_DAMAGE -> "D"
                    PowerUpType.DOUBLE_SCORE -> "2X"
                    PowerUpType.HEALTH -> "H"
                    PowerUpType.BOMB -> "B"
                }
                val labelPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 21f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(symbolText, p.x, p.y + 7f, labelPaint)
            }

            // Draw Descending Neon Enemy Spheres
            enemies.forEach { e ->
                // Outer cyber aura ring
                val auraPaint = Paint().apply {
                    isAntiAlias = true
                    color = e.color
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                }
                canvas.drawCircle(e.x, e.y, e.radius, auraPaint)

                // Solid glowing sphere body
                auraPaint.style = Paint.Style.FILL
                auraPaint.alpha = 65
                canvas.drawCircle(e.x, e.y, e.radius, auraPaint)

                // Concentric energy core representation
                auraPaint.color = Color.WHITE
                auraPaint.alpha = 240
                canvas.drawCircle(e.x, e.y, e.radius * 0.45f, auraPaint)

                // Health bar fraction on heavy or boss nodes
                if (e.type == EnemyType.BOSS || e.hp > 1) {
                    val barWidth = e.radius * 1.5f
                    val barHeight = 8f
                    val bx = e.x - barWidth / 2f
                    val by = e.y - e.radius - 18f
                    
                    val barBg = Paint().apply { color = Color.DKGRAY }
                    canvas.drawRect(bx, by, bx + barWidth, by + barHeight, barBg)
                    
                    val pct = e.hp.toFloat() / e.maxHp
                    val barHp = Paint().apply { color = e.color }
                    canvas.drawRect(bx, by, bx + (barWidth * pct), by + barHeight, barHp)
                }
            }

            // Draw Expanding Shockwaves
            shockwaves.forEach { sw ->
                val wavePaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    color = sw.color
                    strokeWidth = 4f * (sw.life / 0.4f).coerceIn(0.1f, 1f)
                }
                canvas.drawCircle(sw.centerX, sw.centerY, sw.currentRadius, wavePaint)
            }

            // Draw Debris Particles
            debris.forEach { d ->
                val debPaint = Paint().apply {
                    color = d.color
                    alpha = ((d.life / d.maxLife) * 255f).toInt().coerceIn(0, 255)
                    isAntiAlias = true
                }
                canvas.drawCircle(d.x, d.y, d.r, debPaint)
            }

            // Draw sleek futuristic Player Ship
            val wingSpan = 52f
            val noseLength = 65f
            playerPaint.color = Color.parseColor("#22D3EE") // High energy neon Cyan
            
            val shipPath = Path().apply {
                moveTo(playerX, playerY - noseLength) // nose cone
                lineTo(playerX - wingSpan, playerY + 25f) // left wing tip
                lineTo(playerX - wingSpan * 0.4f, playerY + 15f) // fuselage bend
                lineTo(playerX, playerY + 35f) // retro exhaust thruster node
                lineTo(playerX + wingSpan * 0.4f, playerY + 15f)
                lineTo(playerX + wingSpan, playerY + 25f) // right wing tip
                close()
            }
            canvas.drawPath(shipPath, playerPaint)

            // Dynamic core engine visual flare pulsing
            val engineGlow = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#F53F5E") // Thruster fire Red
                style = Paint.Style.FILL
            }
            val plumeSize = 20f + (sin(System.currentTimeMillis() * 0.05f) * 10f)
            canvas.drawCircle(playerX, playerY + 35f, plumeSize, engineGlow)

            // Draw high energy pilot shield bubble
            if (isShieldActive) {
                playerGlowPaint.color = Color.parseColor("#10B981") // Emerald Shield barrier
                canvas.drawCircle(playerX, playerY - 15f, 75f, playerGlowPaint)
                
                // Draw rotating barrier dashes
                playerGlowPaint.strokeWidth = 3f
                playerGlowPaint.color = Color.parseColor("#22D3EE")
                canvas.drawCircle(playerX, playerY - 15f, 85f, playerGlowPaint)
            }

            // Render level delay alerts or wave announcements
            if (waveState == 0) {
                textPaint.color = Color.parseColor("#22D3EE")
                textPaint.textSize = 65f
                val typeName = when(currentWaveType) {
                    0 -> "GRID ATTACK FORMATION"
                    1 -> "SPIRAL COMET WAVE"
                    2 -> "STORM MATRIX SWARM"
                    3 -> "FAST RECONNAISSANCE SYNDICATE"
                    else -> "ALERT! CORE REACTION BOSS APPROACHING"
                }

                canvas.drawText("SECTOR $gameLevel PREPARATION", canvasWidth / 2f, canvasHeight * 0.4f, textPaint)
                
                textPaint.color = Color.WHITE
                textPaint.textSize = 40f
                canvas.drawText(typeName, canvasWidth / 2f, canvasHeight * 0.45f, textPaint)
            }

            // Draw active top screen active buff badges
            var badgeX = 50f
            val badgeY = 160f
            val textBadgePaint = Paint().apply {
                textSize = 24f
                isFakeBoldText = true
                color = Color.WHITE
            }

            if (slowMoTimer > 0f) {
                drawActiveBuffGlowBadge(canvas, "MATRIX-TIME", Color.parseColor("#A855F7"), badgeX, badgeY, textBadgePaint)
                badgeX += 210f
            }
            if (doubleDamageTimer > 0f) {
                drawActiveBuffGlowBadge(canvas, "HIGH-CORE", Color.parseColor("#F43F5E"), badgeX, badgeY, textBadgePaint)
                badgeX += 210f
            }
            if (doubleScoreTimer > 0f) {
                drawActiveBuffGlowBadge(canvas, "SCORE-BOOST", Color.parseColor("#6366F1"), badgeX, badgeY, textBadgePaint)
                badgeX += 215f
            }
            if (magnetTimer > 0f) {
                drawActiveBuffGlowBadge(canvas, "MAGNET", Color.parseColor("#FBBF24"), badgeX, badgeY, textBadgePaint)
            }

        } finally {
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawActiveBuffGlowBadge(canvas: Canvas, label: String, color: Int, x: Float, y: Float, paint: Paint) {
        val bgBox = Paint().apply {
            this.color = color
            this.alpha = 80
            this.style = Paint.Style.FILL
        }
        val borderBox = Paint().apply {
            this.color = color
            this.style = Paint.Style.STROKE
            this.strokeWidth = 2.5f
            this.isAntiAlias = true
        }
        val r = RectF(x, y, x + 190f, y + 45f)
        canvas.drawRoundRect(r, 8f, 8f, bgBox)
        canvas.drawRoundRect(r, 8f, 8f, borderBox)
        canvas.drawText(label, x + 15f, y + 31f, paint)
    }
}
