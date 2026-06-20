package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// Screen Routing Enum
enum class Screen {
    Splash,
    Onboarding,
    Main
}

// Sub-panels of Dashboard
enum class DashboardTab {
    Home,
    Levels,
    Stats,
    Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

// Unified state manager
@Composable
fun MainAppContainer() {
    var currentScreen by remember { mutableStateOf(Screen.Splash) }
    
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vortex_racer_prefs", android.content.Context.MODE_PRIVATE) }
    
    // Global player states loaded from safe SharedPreferences
    var playerLevel by remember { mutableIntStateOf(prefs.getInt("player_level", 1)) }
    var maxUnlockedLevel by remember { mutableIntStateOf(prefs.getInt("max_unlocked_level", 1)) }
    var highScore by remember { mutableIntStateOf(prefs.getInt("high_score", 0)) }
    var selectedLevelForPlay by remember { mutableIntStateOf(1) }
    
    var playerXp by remember { mutableFloatStateOf(prefs.getFloat("player_xp", 0.42f)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var hapticFeedback by remember { mutableStateOf(prefs.getBoolean("haptic_feedback", true)) }
    var particleSpeed by remember { mutableFloatStateOf(prefs.getFloat("particle_speed", 1.0f)) }
    var systemVolume by remember { mutableFloatStateOf(prefs.getFloat("system_volume", 0.5f)) }

    // Sync audio engine on setting values changes
    LaunchedEffect(soundEnabled, systemVolume) {
        SyntheticAudioEngine.isSoundEnabled = soundEnabled
        SyntheticAudioEngine.systemVolume = systemVolume
        SyntheticAudioEngine.refreshMusicVolume()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SpaceBackgroundStart, SpaceBackgroundEnd)
                )
            )
    ) {
        // Soft ambient blur mesh gradients representing the Frosted Glass theme
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // Top-left mesh gradient: Indigo-600/22% blur
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonIndigo.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.1f),
                    radius = size.width * 0.9f
                )
            )
            // Bottom-right mesh gradient: Cyan-500/18% blur
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonCyan.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.9f),
                    radius = size.width * 0.9f
                )
            )
        }

        // Shared immersive background particles across all screens
        FloatingParticles(speedMultiplier = particleSpeed)

        // Gorgeous interactive particle overlay responding smoothly on tap/touch/drag events in background!
        InteractiveTouchParticleOverlay()

        // Navigation Router
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (fadeIn(animationSpec = tween(600)) + slideInHorizontally(
                    initialOffsetX = { 400 },
                    animationSpec = tween(600)
                )) togetherWith
                (fadeOut(animationSpec = tween(400)) + slideOutHorizontally(
                    targetOffsetX = { -400 },
                    animationSpec = tween(400)
                ))
            },
            label = "screenTransition"
        ) { targetScreen ->
            when (targetScreen) {
                Screen.Splash -> SplashScreen(
                    onTimeout = { currentScreen = Screen.Onboarding }
                )
                Screen.Onboarding -> OnboardingFlow(
                    onComplete = { currentScreen = Screen.Main }
                )
                Screen.Main -> MainDashboard(
                    playerLevel = playerLevel,
                    playerXp = playerXp,
                    maxUnlockedLevel = maxUnlockedLevel,
                    highScore = highScore,
                    selectedLevelForPlay = selectedLevelForPlay,
                    onSelectLevel = { level ->
                        selectedLevelForPlay = level
                    },
                    onLevelUp = {
                        if (playerXp >= 0.9f) {
                            playerLevel += 1
                            playerXp = 0.1f
                        } else {
                            playerXp += 0.15f
                        }
                        prefs.edit().putInt("player_level", playerLevel).putFloat("player_xp", playerXp).apply()
                    },
                    onGameCompleted = { score, completed ->
                        if (score > highScore) {
                            highScore = score
                            prefs.edit().putInt("high_score", highScore).apply()
                        }
                        if (completed && selectedLevelForPlay == maxUnlockedLevel && maxUnlockedLevel < 7) {
                            maxUnlockedLevel += 1
                            prefs.edit().putInt("max_unlocked_level", maxUnlockedLevel).apply()
                        }
                    },
                    soundEnabled = soundEnabled,
                    hapticFeedback = hapticFeedback,
                    particleSpeed = particleSpeed,
                    systemVolume = systemVolume,
                    onToggleSound = { 
                        soundEnabled = !soundEnabled 
                        prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
                    },
                    onToggleHaptic = { 
                        hapticFeedback = !hapticFeedback 
                        prefs.edit().putBoolean("haptic_feedback", hapticFeedback).apply()
                    },
                    onChangeParticleSpeed = { 
                        particleSpeed = it 
                        prefs.edit().putFloat("particle_speed", particleSpeed).apply()
                    },
                    onChangeVolume = {
                        systemVolume = it
                        prefs.edit().putFloat("system_volume", systemVolume).apply()
                    }
                )
            }
        }

    }
}

// COMPATIBILITY CONTRACT STUB FOR ROBORAZZI GREETING TEST
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .testTag("greeting_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NeonCyan),
        colors = CardDefaults.cardColors(containerColor = GlassCardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to Space Arcade",
                style = MaterialTheme.typography.titleLarge,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Profile initialized for target: $name",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

// ----------------------------------------------------
// 1. FLOATING PARTICLES CANVAS EFFECT
// ----------------------------------------------------
@Composable
fun FloatingParticles(speedMultiplier: Float = 1.0f) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val animValue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    val particles = remember {
        List(25) {
            ParticleNode(
                x = (0..100).random() / 100f,
                y = (0..100).random() / 100f,
                radius = (3..8).random().toFloat(),
                baseAlpha = (2..5).random() / 10f,
                wobbleFactor = (10..30).random().toFloat()
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        particles.forEach { p ->
            val angle = animValue * speedMultiplier
            val offsetX = (p.x * width + sin(angle + p.x * 12f) * p.wobbleFactor) % width
            val offsetY = (p.y * height + cos(angle * 0.8f + p.y * 10f) * p.wobbleFactor) % height
            val alpha = (p.baseAlpha + sin(angle + p.x) * 0.15f).coerceIn(0.1f, 0.8f)

            drawCircle(
                color = if (p.radius > 5f) NeonCyan.copy(alpha = alpha) else NeonIndigo.copy(alpha = alpha),
                radius = p.radius,
                center = Offset(offsetX, offsetY)
            )
        }
    }
}

data class ParticleNode(
    val x: Float,
    val y: Float,
    val radius: Float,
    val baseAlpha: Float,
    val wobbleFactor: Float
)

// ----------------------------------------------------
// 1B. INTERACTIVE TOUCH-RESPONSE PARTICLE OVERLAY
// ----------------------------------------------------
enum class MainParticleType {
    SPARK,     // Star cross
    SPARKLE,   // Hollow laser ring
    GLOWCIRCLE // Soft high-density light core
}

class InteractiveTouchParticle(
    var id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val maxLife: Float,
    var life: Float,
    val color: Color,
    val size: Float,
    val type: MainParticleType
)

@Composable
fun InteractiveTouchParticleOverlay(modifier: Modifier = Modifier) {
    val particles = remember { ArrayList<InteractiveTouchParticle>() }
    var particleIdCounter by remember { mutableLongStateOf(0L) }
    var lastTick by remember { mutableLongStateOf(0L) }

    val neonColors = remember {
        listOf(
            Color(0xFF22D3EE), // Neon Cyan
            Color(0xFF6366F1), // Neon Indigo
            Color(0xFF10B981), // Emerald Green
            Color(0xFFF43F5E), // Rose Red
            Color(0xFF06B6D4), // Electric Blue
            Color(0xFFFBBF24)  // Amber Gold
        )
    }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (true) {
            withFrameMillis { frameTime ->
                val deltaSec = if (lastTime == 0L) 0.016f else ((frameTime - lastTime) / 1000f).coerceIn(0f, 0.1f)
                lastTime = frameTime

                synchronized(particles) {
                    val iterator = particles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.life -= deltaSec
                        if (p.life <= 0f) {
                            iterator.remove()
                        } else {
                            p.x += p.vx * deltaSec
                            p.y += p.vy * deltaSec
                            // Apply drag deceleration smoothly
                            p.vx *= (1f - 0.08f * (deltaSec / 0.016f)).coerceIn(0.5f, 1f)
                            p.vy *= (1f - 0.08f * (deltaSec / 0.016f)).coerceIn(0.5f, 1f)
                        }
                    }
                }
                lastTick = frameTime
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val position = change.position
                                val isFirstTouch = !change.previousPressed
                                val count = if (isFirstTouch) 8 else 2 // small trail on move, burst on tap

                                synchronized(particles) {
                                    repeat(count) {
                                        val angle = (0..359).random() * (Math.PI / 180f).toFloat()
                                        val speed = if (isFirstTouch) (100..320).random().toFloat() else (60..180).random().toFloat()
                                        val randLife = 0.5f + kotlin.random.Random.nextFloat() * 0.4f
                                        val randSize = 6f + kotlin.random.Random.nextFloat() * 8f
                                        particles.add(
                                            InteractiveTouchParticle(
                                                id = particleIdCounter++,
                                                x = position.x,
                                                y = position.y,
                                                vx = cos(angle) * speed,
                                                vy = sin(angle) * speed,
                                                maxLife = randLife,
                                                life = randLife,
                                                color = neonColors.random(),
                                                size = randSize,
                                                type = MainParticleType.values().random()
                                            )
                                        )
                                    }
                                    // Trim size for excellent runtime efficiency
                                    while (particles.size > 120) {
                                        particles.removeAt(0)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dummy = lastTick // read state to force draw pass of Canvas

            synchronized(particles) {
                particles.forEach { p ->
                    val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
                    val alpha = ratio
                    val currentSize = p.size * ratio

                    when (p.type) {
                        MainParticleType.SPARK -> {
                            val half = currentSize
                            drawLine(
                                color = p.color.copy(alpha = alpha),
                                start = Offset(p.x - half, p.y),
                                end = Offset(p.x + half, p.y),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = p.color.copy(alpha = alpha),
                                start = Offset(p.x, p.y - half),
                                end = Offset(p.x, p.y + half),
                                strokeWidth = 3f
                            )
                        }
                        MainParticleType.SPARKLE -> {
                            val extraRadius = p.size * 2f * (1f - ratio + 0.1f)
                            drawCircle(
                                color = p.color.copy(alpha = alpha),
                                radius = extraRadius,
                                center = Offset(p.x, p.y),
                                style = Stroke(width = 2.5f)
                            )
                        }
                        MainParticleType.GLOWCIRCLE -> {
                            drawCircle(
                                color = p.color.copy(alpha = alpha * 0.45f),
                                radius = currentSize * 1.8f,
                                center = Offset(p.x, p.y)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * 0.95f),
                                radius = currentSize * 0.7f,
                                center = Offset(p.x, p.y)
                            )
                        }
                    }
                }
            }
        }
    }
}


// ----------------------------------------------------
// 2. ANIMATED SPLASH SCREEN
// ----------------------------------------------------
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var triggerEntrance by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        triggerEntrance = true
        delay(2500)
        onTimeout()
    }

    val scale by animateFloatAsState(
        targetValue = if (triggerEntrance) 1.0f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (triggerEntrance) 1.0f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlowRadius by pulseTransition.animateFloat(
        initialValue = 12f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Skip Button top right
        TextButton(
            onClick = onTimeout,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .testTag("splash_skip"),
            colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
        ) {
            Text("SKIP", style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Skip to Onboarding")
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Neon Pulsing Logo Shape
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    // Behind Neon Halo Glow
                    drawCircle(
                        color = NeonIndigo.copy(alpha = 0.3f),
                        radius = 65.dp.toPx() + pulseGlowRadius,
                        center = center
                    )

                    // Draw double technology orbital circles
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(NeonCyan, NeonIndigo, NeonCyan)),
                        radius = 50.dp.toPx(),
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    drawCircle(
                        brush = Brush.sweepGradient(listOf(NeonIndigo, ElectricBlue, NeonIndigo)),
                        radius = 42.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Central tech node logo triangle
                    val path = Path().apply {
                        moveTo(center.x, center.y - 20.dp.toPx())
                        lineTo(center.x - 18.dp.toPx(), center.y + 15.dp.toPx())
                        lineTo(center.x + 18.dp.toPx(), center.y + 15.dp.toPx())
                        close()
                    }
                    drawPath(path, brush = Brush.linearGradient(listOf(NeonCyan, NeonIndigo)))
                }

                // Inner core pulsing symbol
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Arcade core",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Text Title
            HorizontalDivider(
                modifier = Modifier
                    .width(100.dp)
                    .animateContentSize(),
                thickness = 2.dp,
                color = NeonCyan.copy(alpha = alpha)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SPACE ARCADE",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                letterSpacing = 6.sp,
                color = Color.White,
                modifier = Modifier.testTag("app_title").scale(alpha)
            )

            Text(
                text = "NEO ENGINE CORE v2.2",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan.copy(alpha = alpha),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}


// ----------------------------------------------------
// 3. ONBOARDING SCREEN FLOW (MULTI-PAGER MODEL)
// ----------------------------------------------------
@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = 4

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Slider content with AnimatedContent for smooth slide transitions
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(initialOffsetX = { 600 }) + fadeIn()) togetherWith
                                (slideOutHorizontally(targetOffsetX = { -600 }) + fadeOut())
                    } else {
                        (slideInHorizontally(initialOffsetX = { -600 }) + fadeIn()) togetherWith
                                (slideOutHorizontally(targetOffsetX = { 600 }) + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboardingPager"
            ) { page ->
                when (page) {
                    0 -> OnboardingIntroPage()
                    1 -> OnboardingFeatureHighlightPage()
                    2 -> OnboardingPowerupsPage()
                    3 -> OnboardingGetStartedPage(onComplete = onComplete)
                }
            }

            // Dots Indicator & Action Row
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Progress dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    repeat(totalPages) { idx ->
                        val active = idx == currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (active) 24.dp else 8.dp, 
                            animationSpec = spring(),
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(if (active) NeonCyan else NeonIndigo.copy(alpha = 0.5f))
                        )
                    }
                }

                // Nav buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPage > 0) {
                        OutlinedButton(
                            onClick = { currentPage-- },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .testTag("btn_prev"),
                            border = BorderStroke(1.dp, NeonIndigo),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("BACK", color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            if (currentPage < totalPages - 1) {
                                currentPage++
                            } else {
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(if (currentPage == 0) 2f else 1f)
                            .testTag("btn_next")
                    ) {
                        Text(
                            text = if (currentPage == totalPages - 1) "START EXPERIENCE" else "NEXT",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        }

        // Top Right Skip Anchor - drawn last to ensure it remains clickable on top of children Columns
        TextButton(
            onClick = onComplete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .testTag("onboarding_skip"),
            colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
        ) {
            Text("SKIP", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ONBOARDING PAGE 1: Intro
@Composable
fun OnboardingIntroPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Beautiful glass container with animated tech globe
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GlassCardBg)
                .border(width = 2.dp, brush = Brush.linearGradient(listOf(NeonCyan, Color.Transparent)), shape = RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "gyro")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "angle"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                
                // Tech rings grid
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.15f),
                    radius = 80.dp.toPx(),
                    center = center
                )
                
                // Rotated spinning neon energy rings
                rotate(degrees = angle, pivot = center) {
                    drawOval(
                        color = NeonCyan,
                        topLeft = Offset(center.x - 70.dp.toPx(), center.y - 20.dp.toPx()),
                        size = Size(140.dp.toPx(), 40.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                rotate(degrees = -angle * 1.5f, pivot = center) {
                    drawOval(
                        color = NeonIndigo,
                        topLeft = Offset(center.x - 20.dp.toPx(), center.y - 70.dp.toPx()),
                        size = Size(40.dp.toPx(), 140.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Core shining sun
                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.4f),
                    radius = 24.dp.toPx(),
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Welcome to the Experience",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Step inside a hyper-modern universe. Highly immersive layouts, neon fluid interactions, and deep space telemetry dashboards await.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ONBOARDING PAGE 2: Features Highlighting with a live gameplay Telemetry Card
@Composable
fun OnboardingFeatureHighlightPage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Simulated telemetry game card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassCardBg)
                .border(1.dp, GlassCardBorder)
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SoftNeonGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE TELEMETRY Sector 7",
                            color = SoftNeonGreen,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = "85% COMPLETED",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gauge bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(NeonIndigo, NeonCyan)))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val liveStats = listOf(
                    "WIN STREAK" to "12 STRAIGHT",
                    "CORE MULTIPLIER" to "x4.8 SECURE",
                    "CURRENT POSITION" to "RANK #18"
                )

                liveStats.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                        Text(text = value, color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Play. Compete. Progress.",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Track real-time XP telemetry charts. Complete daily level configurations, claim massive neon bounty multipliers, and push to top score leaderboard.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// ONBOARDING PAGE 3: Powerups Showcase with real click toggling
@Composable
fun OnboardingPowerupsPage() {
    var activePowerUp by remember { mutableIntStateOf(1) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Select & Power Up",
            style = MaterialTheme.typography.labelLarge,
            color = NeonCyan,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Interactive card select list
        val powerups = listOf(
            Triple("PLASMA INJECTOR", "Generates high intensity green shields", SoftNeonGreen),
            Triple("SHIELD OVERLOAD", "Full 100% force field bubble state protect", NeonCyan),
            Triple("VORTEX DESTROYER", "Wipes out active sector hazards instantly", SoftNeonRed)
        )

        powerups.forEachIndexed { index, (title, desc, color) ->
            val isSelected = activePowerUp == index
            val scale by animateFloatAsState(if (isSelected) 1.03f else 1.0f, label = "pScale")
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 6.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) GlassCardBg else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        brush = if (isSelected) Brush.linearGradient(listOf(color, NeonIndigo))
                        else Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { activePowerUp = index }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Star,
                        contentDescription = "Selection",
                        tint = if (isSelected) color else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title, 
                            color = if (isSelected) color else Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 18.sp
                        )
                        Text(
                            text = desc, 
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Interactive Powerups",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap on target cells directly. Customize your loadout configuration files before battle start.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// ONBOARDING PAGE 4: Get Started with gamer avatar profile load
@Composable
fun OnboardingGetStartedPage(onComplete: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Loads our highly customized, beautifully generated IMG gamer profile avatar
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .border(width = 3.dp, brush = Brush.sweepGradient(listOf(NeonCyan, NeonIndigo, NeonCyan)), shape = CircleShape)
                .shadow(elevation = 16.dp, shape = CircleShape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_gamer_avatar),
                contentDescription = "Gamer avatar background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SYSTEM PROFILE SYNCED",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
        )

        Text(
            text = "ARCADE_WARRIOR_42",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "All systems green. Your cyber profile is mapped seamlessly to active sector leaders.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(containerColor = NeonMagentaOrCyan(true)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
                .testTag("onboarding_start_btn")
        ) {
            Text(
                "LAUNCH DECK INTERFACE",
                color = Color.Black,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun NeonMagentaOrCyan(isMain: Boolean): Color {
    // Returns bright glowing neon cyan
    return NeonCyan
}


// ----------------------------------------------------
// 4. MAIN HOMESCREEN CARD-BASED DASHBOARD
// ----------------------------------------------------
@Composable
fun MainDashboard(
    playerLevel: Int,
    playerXp: Float,
    maxUnlockedLevel: Int,
    highScore: Int,
    selectedLevelForPlay: Int,
    onSelectLevel: (Int) -> Unit,
    onLevelUp: () -> Unit,
    onGameCompleted: (Int, Boolean) -> Unit,
    soundEnabled: Boolean,
    hapticFeedback: Boolean,
    particleSpeed: Float,
    systemVolume: Float,
    onToggleSound: () -> Unit,
    onToggleHaptic: () -> Unit,
    onChangeParticleSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.Home) }
    
    // Play Game interactive states
    var isPlayingMiniGame by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color.Black.copy(alpha = 0.4f),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.05f))
                    .navigationBarsPadding(),
                contentColor = NeonCyan
            ) {
                listOf(
                    Triple(DashboardTab.Home, "Home", Icons.Default.Home),
                    Triple(DashboardTab.Levels, "Quests", Icons.Default.List),
                    Triple(DashboardTab.Stats, "Arena", Icons.Default.Person),
                    Triple(DashboardTab.Settings, "System", Icons.Default.Settings)
                ).forEach { (tab, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { 
                            selectedTab = tab 
                            if (tab != DashboardTab.Home) {
                                isPlayingMiniGame = false
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = Color.White.copy(alpha = 0.08f),
                            unselectedIconColor = Slate500,
                            unselectedTextColor = Slate500
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            
            // Render active dashboard tab
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.95f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.95f))
                },
                label = "tabContent"
            ) { targetTab ->
                when (targetTab) {
                    DashboardTab.Home -> {
                        if (isPlayingMiniGame) {
                            MiniRetroGameView(
                                selectedLevel = selectedLevelForPlay,
                                hapticFeedbackEnabled = hapticFeedback,
                                onClose = { isPlayingMiniGame = false },
                                onGainXp = onLevelUp,
                                onGameCompleted = onGameCompleted
                            )
                        } else {
                            DashboardHomeView(
                                playerLevel = playerLevel,
                                playerXp = playerXp,
                                onGainXp = onLevelUp,
                                onLaunchGame = { isPlayingMiniGame = true },
                                onGoToTab = { selectedTab = it }
                            )
                        }
                    }
                    DashboardTab.Levels -> LevelsView(
                        maxUnlockedLevel = maxUnlockedLevel,
                        selectedLevel = selectedLevelForPlay,
                        onSelectLevel = onSelectLevel
                    )
                    DashboardTab.Stats -> StatsView(
                        playerLevel = playerLevel,
                        playerXp = playerXp,
                        highScore = highScore,
                        sectorsCleared = maxUnlockedLevel
                    )
                    DashboardTab.Settings -> SettingsView(
                        soundEnabled = soundEnabled,
                        hapticFeedback = hapticFeedback,
                        particleSpeed = particleSpeed,
                        systemVolume = systemVolume,
                        onToggleSound = onToggleSound,
                        onToggleHaptic = onToggleHaptic,
                        onChangeParticleSpeed = onChangeParticleSpeed,
                        onChangeVolume = onChangeVolume
                    )
                }
            }
        }
    }
}

// 4A. MAIN VIEW: GAME CORE DECK
@Composable
fun DashboardHomeView(
    playerLevel: Int,
    playerXp: Float,
    onGainXp: () -> Unit,
    onLaunchGame: () -> Unit,
    onGoToTab: (DashboardTab) -> Unit
) {
    // Simple state to track claiming rewards
    var rewardsClaimed by remember { mutableStateOf(false) }
    var showClaimSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Header profile bar
        item {
            Column(modifier = Modifier.statusBarsPadding().padding(top = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VORTEX GALAXY",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Hello, Commander",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Slate100,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    // Avatar click launches Profile stats context with premium dual gradient border (Tailwind style)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(NeonIndigo, NeonCyan)))
                            .padding(1.5.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0A0A1B))
                            .clickable { onGoToTab(DashboardTab.Stats) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_gamer_avatar),
                            contentDescription = "Dashboard Profile avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // XP / LEVEL BAR GAUGE (Frosted Glass style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassCardBg)
                        .border(width = 1.dp, color = GlassCardBorder, shape = RoundedCornerShape(16.dp))
                        .clickable { onGainXp() } // Manual boost developer check
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "Level $playerLevel • ${(playerXp * 2500).toInt() + 100} XP", 
                                color = Slate400, 
                                fontWeight = FontWeight.Medium, 
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${(playerXp * 100).toInt()}% to Pro", 
                                color = NeonCyan, 
                                fontWeight = FontWeight.Bold, 
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Animated progress indicator bar
                        val animatedXp by animateFloatAsState(targetValue = playerXp, label = "animatedXp")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedXp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Brush.horizontalGradient(listOf(NeonIndigo, NeonCyan)))
                            )
                        }
                        Text(
                            text = "💡 TAP GAUGE FOR QUICK XP POWER-UP",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = Slate500,
                            modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        // 1. HERO BIG CARD: PLAY ARCADE INVASION GAME! (Frosted Glass glow style)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunchGame() }
                    .testTag("card_play_game")
            ) {
                // Background shadow glow (Absolute -inset-0.5 bg-gradient-to-r from-indigo-600 to-cyan-500 blur opacity-25 equivalent)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(listOf(NeonIndigo.copy(alpha = 0.25f), NeonCyan.copy(alpha = 0.25f)))
                        )
                )
                
                // Active Card Surface (bg-white/10 backdrop-blur-xl border-white/20 rounded-3xl)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(GlassCardBgHigh)
                        .border(width = 1.dp, color = GlassCardBorderHigh, shape = RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar/Emoji container (w-16 h-16 rounded-2xl bg-indigo-500/20 shadow-inner border border-white/10)
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NeonIndigo.copy(alpha = 0.2f))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎮", fontSize = 28.sp)
                            }
                            
                            Column {
                                Text(
                                    text = "Jump into Orbit",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Slate100,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                )
                                Text(
                                    text = "Active Season: Nebula Echo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate400
                                )
                            }
                        }

                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Active Indicator",
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Fly retro space ship, dodge asteroid waves, shoot down structures, and score unlimited level up XP. Drag to control horizontally!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = onLaunchGame,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("LAUNCH MINIGAME", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
            }
        }

        // GRID CONTENT: MULTIPLE DYNAMIC NEON CARDS (Frosted Glass Theme)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Row 1: Levels & Analytics
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    
                    // Card A: Levels
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassCardBg)
                            .border(width = 1.dp, color = GlassCardBorder, shape = RoundedCornerShape(24.dp))
                            .clickable { onGoToTab(DashboardTab.Levels) }
                            .padding(16.dp)
                            .testTag("card_levels")
                    ) {
                        Column {
                            Text("🧩", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Levels", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("14/20 Completed", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        }
                    }

                    // Card B: Ranks (Stats & Telemetry)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassCardBg)
                            .border(width = 1.dp, color = GlassCardBorder, shape = RoundedCornerShape(24.dp))
                            .clickable { onGoToTab(DashboardTab.Stats) }
                            .padding(16.dp)
                            .testTag("card_stats")
                    ) {
                        Column {
                            Text("🏆", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Ranks", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Top 2% Global", style = MaterialTheme.typography.labelSmall, color = NeonIndigo)
                        }
                    }
                }

                // Row 2: Rewards & Config Engine
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    
                    // Card C: Claim core daily rewards / Vault
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassCardBg)
                            .border(
                                width = 1.5.dp, 
                                brush = if (rewardsClaimed) SolidColor(GlassCardBorder) else Brush.linearGradient(listOf(Color(0xFFFBBF24), NeonIndigo)),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable { 
                                if (!rewardsClaimed) {
                                    scope.launch {
                                        showClaimSuccess = true
                                        onGainXp() // Reward some XP
                                        delay(2000)
                                        showClaimSuccess = false
                                        rewardsClaimed = true
                                    }
                                }
                            }
                            .padding(16.dp)
                            .testTag("card_rewards")
                    ) {
                        Column {
                            Text("🎁", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Vault", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (rewardsClaimed) "Claimed" else "2 New Rewards", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = if (rewardsClaimed) Slate500 else Color(0xFFFBBF24)
                            )
                        }
                    }

                    // Card D: Config / Insights
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassCardBg)
                            .border(width = 1.dp, color = GlassCardBorder, shape = RoundedCornerShape(24.dp))
                            .clickable { onGoToTab(DashboardTab.Settings) }
                            .padding(16.dp)
                            .testTag("card_settings")
                    ) {
                        Column {
                            Text("📊", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Insights", style = MaterialTheme.typography.titleMedium, color = Slate100, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("View Playstyle", style = MaterialTheme.typography.labelSmall, color = SoftNeonGreen)
                        }
                    }
                }
            }
        }

        // Leaderboard teaser banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassCardBg)
                    .border(width = 0.5.dp, color = GlassCardBorder, shape = RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = "Trophy", tint = NeonCyan, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Global Leaderboard", style = MaterialTheme.typography.titleLarge, color = Color.White, fontSize = 18.sp)
                            Text("TOP CHASSIS FIGHTERS TODAY", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(NeonIndigo)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("#18 COMMAND", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        
        // Blank spatial offset
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Success popup Overlay
    if (showClaimSuccess) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassCardBg)
                    .border(width = 2.dp, color = SoftNeonGreen, shape = RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, contentDescription = "Success", tint = SoftNeonGreen, modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "BOUNTY UNLOCKED!", style = MaterialTheme.typography.headlineMedium, color = SoftNeonGreen, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "+150 XP Core Points Configured", style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun HighlightPurp(): Color {
    return NeonIndigo
}


// ----------------------------------------------------
// 5. INTERACTIVE CANVAS MINIGAME VIEW ("PLAY GAME")
// ----------------------------------------------------
@Composable
fun PowerUpIndicator(name: String, color: Color, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) color.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
            .border(width = 0.5.dp, color = if (active) color else Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) color else Color.White.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name, 
                fontSize = 9.sp, 
                fontWeight = FontWeight.Bold, 
                color = if (active) Color.White else Color.White.copy(alpha = 0.4f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun MiniRetroGameView(
    selectedLevel: Int,
    hapticFeedbackEnabled: Boolean = true,
    onClose: () -> Unit,
    onGainXp: () -> Unit,
    onGameCompleted: (Int, Boolean) -> Unit
) {
    val context = LocalContext.current
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var gameStarted by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    // Reference to the custom view to handle pause / resume and cleanup safely
    var surfaceViewRef by remember { mutableStateOf<RetroGameSurfaceView?>(null) }

    DisposableEffect(gameStarted) {
        onDispose {
            surfaceViewRef?.stopGame()
            surfaceViewRef = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050510))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header stats (Frosted Glass style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            surfaceViewRef?.stopGame()
                            surfaceViewRef = null
                            onClose()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Return to deck", 
                            tint = Color.White
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SECTOR $selectedLevel", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = NeonCyan.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "SCORE: $score", 
                            style = MaterialTheme.typography.titleMedium, 
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "ENERGY: ${"❤️".repeat(lives.coerceAtLeast(0))}", 
                        style = MaterialTheme.typography.labelLarge, 
                        color = SoftNeonRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // In-game dynamic Power-up icons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PowerUpIndicator("SHIELD ON", SoftNeonGreen, true)
                    Spacer(modifier = Modifier.width(8.dp))
                    PowerUpIndicator("RAPID FIRE", NeonIndigo, lives >= 3)
                    Spacer(modifier = Modifier.width(8.dp))
                    PowerUpIndicator("LASER X2", NeonCyan, score > 800)
                }
            }

            if (!gameStarted) {
                // Pre-game introduction overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "NEON INVADERS",
                            style = MaterialTheme.typography.displayMedium,
                            color = NeonCyan,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Drag horizontally to slide your cybernetic space interceptor. Hold down to auto-fire vibrant laser pulses! Blast wave formations of incoming neon reactors and collect powerful weapon cores & energetic buffs to dominate the leaderboards!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate400,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = { gameStarted = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                        ) {
                            Text("ENGAGE ENGINES", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
            } else if (isGameOver) {
                // Game over overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "INTELLIGENCE BRIEF", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = SoftNeonRed, 
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "DECK CRITICAL END", 
                            style = MaterialTheme.typography.headlineLarge, 
                            color = SoftNeonRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            text = "FINAL SCORE: $score POINTS", 
                            style = MaterialTheme.typography.titleLarge, 
                            color = Color.White, 
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Reset game states
                                    score = 0
                                    lives = 3
                                    isGameOver = false
                                    // Force recreation of SurfaceView for a clean start
                                    gameStarted = false
                                    surfaceViewRef?.stopGame()
                                    surfaceViewRef = null
                                    
                                    // Set started to true back on next tick
                                    gameStarted = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("RETRY ENGINE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    surfaceViewRef?.stopGame()
                                    surfaceViewRef = null
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("DECK RETURN", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Core Gameplay active High-Performance Native SurfaceView Game Engine
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            RetroGameSurfaceView(ctx).apply {
                                setStartLevel(selectedLevel)
                                setHapticFeedbackEnabled(hapticFeedbackEnabled)
                                setGameCallbacks(
                                    onScoreChanged = { newScore -> 
                                        score = newScore 
                                        onGameCompleted(newScore, false) 
                                    },
                                    onLivesChanged = { newLives -> lives = newLives },
                                    onGameOver = { finalScore -> 
                                        isGameOver = true 
                                        onGameCompleted(finalScore, false) 
                                    },
                                    onXpEarned = { 
                                        onGainXp() 
                                        onGameCompleted(score, true) 
                                    }
                                )
                                surfaceViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            // Update callback values if needed dynamically
                        }
                    )
                }
            }
        }
    }
}


// ----------------------------------------------------
// 4B. SUBVIEW: LEVELS ACTIVE STATE LIST
// ----------------------------------------------------
@Composable
fun LevelsView(
    maxUnlockedLevel: Int,
    selectedLevel: Int,
    onSelectLevel: (Int) -> Unit
) {
    val levelsList = remember(maxUnlockedLevel) {
        listOf(
            LevelMetadata("Sector 1: Alpha Base", "Clear basic orbital hazards", true, maxUnlockedLevel > 1),
            LevelMetadata("Sector 2: Laser Belt", "High frequency energy lasers", maxUnlockedLevel >= 2, maxUnlockedLevel > 2),
            LevelMetadata("Sector 3: Cyber Ring", "Spine matrix obstacle nodes", maxUnlockedLevel >= 3, maxUnlockedLevel > 3),
            LevelMetadata("Sector 4: Wormhole Gateway", "Hyperspeed trajectory tunnel", maxUnlockedLevel >= 4, maxUnlockedLevel > 4),
            LevelMetadata("Sector 5: Core Reactor", "Critical matrix fusion chamber", maxUnlockedLevel >= 5, maxUnlockedLevel > 5),
            LevelMetadata("Sector 6: Nebula Outpost", "Gravity anomaly grid battle", maxUnlockedLevel >= 6, maxUnlockedLevel > 6),
            LevelMetadata("Sector 7: Matrix Core", "AI rogue master system confrontation", maxUnlockedLevel >= 7, maxUnlockedLevel > 7)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp)
    ) {
        Text("Sector Configurations", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
        Text("CHALLENGE LEVELS", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(levelsList.size) { index ->
                val stateItem = levelsList[index]
                val levelNum = index + 1
                val isSelected = levelNum == selectedLevel
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) NeonIndigo.copy(alpha = 0.25f) else GlassCardBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp, 
                            color = if (isSelected) NeonCyan else if (stateItem.isUnlocked) GlassCardBorder else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = stateItem.isUnlocked) {
                            onSelectLevel(levelNum)
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // Status icon
                            Icon(
                                imageVector = when {
                                    !stateItem.isUnlocked -> Icons.Default.Lock
                                    stateItem.isCompleted -> Icons.Default.Check
                                    isSelected -> Icons.Default.PlayArrow
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = "Level State",
                                tint = when {
                                    !stateItem.isUnlocked -> Color.White.copy(alpha = 0.3f)
                                    stateItem.isCompleted -> SoftNeonGreen
                                    isSelected -> NeonCyan
                                    else -> Color.White.copy(alpha = 0.6f)
                                },
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = stateItem.title, 
                                    style = MaterialTheme.typography.titleLarge, 
                                    color = if (stateItem.isUnlocked) Color.White else Color.White.copy(alpha = 0.3f),
                                    fontSize = 17.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = stateItem.desc, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    color = if (stateItem.isUnlocked) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }

                        if (stateItem.isUnlocked) {
                            Text(
                                text = if (isSelected) "SELECTED" else if (stateItem.isCompleted) "SECURE" else "ACTIVE",
                                color = if (isSelected) NeonCyan else if (stateItem.isCompleted) SoftNeonGreen else Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "LOCKED",
                                color = Color.White.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

data class LevelMetadata(
    val title: String,
    val desc: String,
    val isUnlocked: Boolean,
    val isCompleted: Boolean
)


// ----------------------------------------------------
// 4C. SUBVIEW: GRAPHICAL TELEMETRY ANALYTICS (STATS)
// ----------------------------------------------------
@Composable
fun StatsView(
    playerLevel: Int,
    playerXp: Float,
    highScore: Int,
    sectorsCleared: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp)
    ) {
        Text("Active Metrics", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
        Text("TELEMETRY LOGS", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        // Large glass visualization dashboard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlassCardBg)
                .border(width = 1.dp, color = GlassCardBorder, shape = RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    "WIN/XP EXPANSION PROFILE", 
                    style = MaterialTheme.typography.labelLarge, 
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Render vector line graph
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val graphWidth = size.width
                        val graphHeight = size.height

                        // Graph points
                        val points = listOf(
                            Offset(0.0f, 0.7f),
                            Offset(0.2f, 0.5f),
                            Offset(0.4f, 0.85f),
                            Offset(0.6f, 0.35f),
                            Offset(0.8f, 0.95f),
                            Offset(1.0f, playerXp)
                        )

                        // Path calculation
                        val linePath = Path().apply {
                            val startX = points[0].x * graphWidth
                            val startY = (1f - points[0].y) * graphHeight
                            moveTo(startX, startY)
                            for (i in 1 until points.size) {
                                val currentX = points[i].x * graphWidth
                                val currentY = (1f - points[i].y) * graphHeight
                                lineTo(currentX, currentY)
                            }
                        }

                        // Gradient fill path beneath lines
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(graphWidth, graphHeight)
                            lineTo(0f, graphHeight)
                            close()
                        }

                        // Draw structural guides
                        drawRect(
                            color = Color.White.copy(alpha = 0.04f),
                            topLeft = Offset(0f, 0f),
                            size = Size(graphWidth, graphHeight)
                        )
                        for (i in 1..4) {
                            val lineY = i * (graphHeight / 5)
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(0f, lineY),
                                end = Offset(graphWidth, lineY),
                                strokeWidth = 1f
                            )
                        }

                        // Render gradient fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(NeonCyan.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )

                        // Render glowing neon line
                        drawPath(
                            path = linePath,
                            color = NeonCyan,
                            style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
                        )

                        // Draw nodes circles
                        points.forEach { pt ->
                            val ptX = pt.x * graphWidth
                            val ptY = (1f - pt.y) * graphHeight
                            drawCircle(
                                color = NeonIndigo,
                                radius = 6.dp.toPx(),
                                center = Offset(ptX, ptY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(ptX, ptY)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("S1", "S2", "S3", "S4", "S5", "LIVE").forEach {
                        Text(text = it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile metadata rows
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                StatsRow("LAUNCHED PROTOCOLS", "1,248 SECURE")
                StatsRow("ALL-TIME HIGHEST SCORE", "$highScore PTS")
                StatsRow("SECTORS COMPLETED", "$sectorsCleared / 7 SECTORS")
                StatsRow("CURRENT EXP", "${(playerXp * 1500).toInt()} / 1500 PT")
                StatsRow("ACTIVE PROFILE MULTIPLIER", "x12.5 CRITICAL BOOST")
                StatsRow("RANK DIVISION", "GRAND COMMANDER")
                StatsRow("STABILITY FREQUENCY", "98.92% COMPLIANT")
            }
        }
    }
}

@Composable
fun StatsRow(label: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassCardBg)
            .border(width = 0.5.dp, color = GlassCardBorder, shape = RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            Text(text = value, style = MaterialTheme.typography.labelLarge, color = NeonCyan)
        }
    }
}


// ----------------------------------------------------
// 4D. SUBVIEW: SYSTEM CONFIGURATION (SETTINGS)
// ----------------------------------------------------
@Composable
fun SettingsView(
    soundEnabled: Boolean,
    hapticFeedback: Boolean,
    particleSpeed: Float,
    systemVolume: Float,
    onToggleSound: () -> Unit,
    onToggleHaptic: () -> Unit,
    onChangeParticleSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp)
    ) {
        Text("Deck Preferences", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
        Text("CORE ENGINE CONFIG", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlassCardBg)
                .border(width = 1.dp, color = GlassCardBorder, shape = RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                
                // Sound Toggle Row + Volume Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("AUDIO SOUND FX", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text("Engage tactical cyber sweeps", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { onToggleSound() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonIndigo,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                    
                    if (soundEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SYNTH VOLUME", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            Text(text = "${(systemVolume * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                        }
                        Slider(
                            value = systemVolume,
                            onValueChange = { onChangeVolume(it) },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                // Haptic Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("HAPTIC OVERDRIVE", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("Tactile response on asteroid impact", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = hapticFeedback,
                        onCheckedChange = { onToggleHaptic() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonIndigo,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                // Particle Speed Simulation Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("PARTICLE ENGINE MULTIPLIER", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text(text = "x${"%.1f".format(particleSpeed)}", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    }
                    Text("Controls floating speed of deep space vectors", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 6.dp))
                    
                    Slider(
                        value = particleSpeed,
                        onValueChange = { onChangeParticleSpeed(it) },
                        valueRange = 0.1f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dynamic System Status card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassCardBg)
                .border(width = 2.dp, color = SoftNeonGreen.copy(alpha = 0.3f), shape = RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(SoftNeonGreen)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("ALL DECK SYSTEMS OPTIMIZED", style = MaterialTheme.typography.titleLarge, color = SoftNeonGreen, fontSize = 16.sp)
                    Text("Compiled safely using Kotlin & compose state machines", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
