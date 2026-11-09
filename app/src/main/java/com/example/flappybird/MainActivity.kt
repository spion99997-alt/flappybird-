package com.example.flappybird

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.random.Random

// ============================================================
//  Flappy Bird на Jetpack Compose — однофайловая игра
//  Скопируйте этот файл в ваш проект как MainActivity.kt
// ============================================================

enum class GameState { Ready, Playing, GameOver }

data class Pipe(
    var x: Float,
    val gapY: Float,       // центр прогона
    val gapHeight: Float,  // высота прогона
    var passed: Boolean = false
)

class GameData {
    var state by mutableStateOf(GameState.Ready)
    var birdY by mutableStateOf(0f)
    var birdVelocity by mutableStateOf(0f)
    var pipes by mutableStateOf(listOf<Pipe>())
    var score by mutableStateOf(0)
    var highScore by mutableStateOf(0)
    var birdX by mutableStateOf(0f) // задаётся при первом кадре
    var screenWidth by mutableStateOf(0f)
    var screenHeight by mutableStateOf(0f)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FlappyBirdGame() }
    }
}

@Composable
fun FlappyBirdGame() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("flappy_prefs", Context.MODE_PRIVATE) }

    val game = remember { GameData() }

    // Физика (пиксели в секунду)
    val gravity = 2200f
    val flapStrength = -650f
    val pipeSpeed = 280f
    val pipeWidth = 100f
    val gapHeight = 230f
    val pipeSpacing = 420f
    val birdRadius = 26f
    val groundHeight = 90f
    val birdXFraction = 0.25f

    // Загружаем рекорд
    LaunchedEffect(Unit) {
        game.highScore = prefs.getInt("high_score", 0)
        game.birdY = Float.NaN // будет инициализирован на первом кадре
    }

    var lastTime by remember { mutableStateOf(0L) }

    // Игровой цикл
    LaunchedEffect(game.state) {
        if (game.state == GameState.Playing) {
            lastTime = 0L
            while (game.state == GameState.Playing) {
                withFrameNanos { now ->
                    val dt = if (lastTime == 0L) 0f else (now - lastTime) / 1_000_000_000f
                    lastTime = now
                    if (dt > 0f) {
                        stepGame(game, dt, gravity, pipeSpeed, pipeSpacing, pipeWidth, gapHeight, groundHeight, birdRadius, prefs)
                    }
                }
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    when (game.state) {
                        GameState.Ready -> {
                            game.state = GameState.Playing
                            game.birdVelocity = flapStrength
                        }
                        GameState.Playing -> game.birdVelocity = flapStrength
                        GameState.GameOver -> {
                            // рестарт
                            game.pipes = emptyList()
                            game.score = 0
                            game.birdVelocity = 0f
                            game.state = GameState.Ready
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        game.screenWidth = w
        game.screenHeight = h

        // Инициализация позиции птицы
        if (game.birdY.isNaN() || game.state == GameState.Ready) {
            if (game.birdY.isNaN()) game.birdY = h * 0.45f
            game.birdX = w * birdXFraction
        }

        // Небо
        drawRect(Color(0xFF70C5CE), size = Size(w, h))
        // Облака-полоски (декор)
        drawRect(Color(0xFFB9E5EA), topLeft = Offset(0f, h * 0.15f), size = Size(w, 6f))
        drawRect(Color(0xFFB9E5EA), topLeft = Offset(0f, h * 0.3f), size = Size(w, 4f))

        // Земля
        drawRect(Color(0xFF9EE65B), topLeft = Offset(0f, h - groundHeight), size = Size(w, groundHeight))
        drawRect(Color(0xFF7BC043), topLeft = Offset(0f, h - groundHeight), size = Size(w, 8f))

        // Трубы
        game.pipes.forEach { pipe ->
            val topBottom = pipe.gapY - gapHeight / 2
            val bottomTop = pipe.gapY + gapHeight / 2
            val pipeColor = Color(0xFF73BF2E)
            val pipeBorder = Color(0xFF4E8A1C)
            // верхняя труба
            drawRect(pipeColor, topLeft = Offset(pipe.x, 0f), size = Size(pipeWidth, topBottom))
            drawRect(pipeBorder, topLeft = Offset(pipe.x, 0f), size = Size(pipeWidth, topBottom), style = Stroke(4f))
            drawRect(pipeBorder, topLeft = Offset(pipe.x - 6f, topBottom - 24f), size = Size(pipeWidth + 12f, 24f))
            // нижняя труба
            drawRect(pipeColor, topLeft = Offset(pipe.x, bottomTop), size = Size(pipeWidth, h - groundHeight - bottomTop))
            drawRect(pipeBorder, topLeft = Offset(pipe.x, bottomTop), size = Size(pipeWidth, h - groundHeight - bottomTop), style = Stroke(4f))
            drawRect(pipeBorder, topLeft = Offset(pipe.x - 6f, bottomTop), size = Size(pipeWidth + 12f, 24f))
        }

        // Птица
        val birdX = game.birdX
        val birdY = game.birdY
        // тень
        drawCircle(Color(0xFFE0A030), radius = birdRadius, center = Offset(birdX + 3f, birdY + 4f))
        // тело
        drawCircle(Color(0xFFFBD23C), radius = birdRadius, center = Offset(birdX, birdY))
        // глаз
        drawCircle(Color.White, radius = birdRadius * 0.4f, center = Offset(birdX + birdRadius * 0.45f, birdY - birdRadius * 0.25f))
        drawCircle(Color.Black, radius = birdRadius * 0.2f, center = Offset(birdX + birdRadius * 0.55f, birdY - birdRadius * 0.25f))
        // клюв
        drawRect(
            Color(0xFFFB8500),
            topLeft = Offset(birdX + birdRadius * 0.7f, birdY),
            size = Size(birdRadius * 0.6f, birdRadius * 0.25f)
        )

        // Текст
        when (game.state) {
            GameState.Ready -> {
                drawCenteredText(textMeasurer, "Flappy Bird", w, h * 0.35f, 64.sp, Color.White)
                drawCenteredText(textMeasurer, "Коснись, чтобы начать", w, h * 0.45f, 28.sp, Color.White)
            }
            GameState.Playing -> {
                drawCenteredText(textMeasurer, "${game.score}", w, h * 0.12f, 72.sp, Color.White)
            }
            GameState.GameOver -> {
                drawCenteredText(textMeasurer, "Game Over", w, h * 0.35f, 64.sp, Color.White)
                drawCenteredText(textMeasurer, "Счёт: ${game.score}", w, h * 0.45f, 36.sp, Color.White)
                drawCenteredText(textMeasurer, "Рекорд: ${game.highScore}", w, h * 0.52f, 36.sp, Color.White)
                drawCenteredText(textMeasurer, "Коснись для рестарта", w, h * 0.62f, 28.sp, Color.White)
            }
        }
    }
}

private fun stepGame(
    game: GameData,
    dt: Float,
    gravity: Float,
    pipeSpeed: Float,
    pipeSpacing: Float,
    pipeWidth: Float,
    gapHeight: Float,
    groundHeight: Float,
    birdRadius: Float,
    prefs: android.content.SharedPreferences
) {
    val w = game.screenWidth
    val h = game.screenHeight
    if (w == 0f || h == 0f) return

    // Физика птицы
    game.birdVelocity += gravity * dt
    game.birdY += game.birdVelocity * dt

    // Двигаем трубы
    val moved = game.pipes.map { it.copy(x = it.x - pipeSpeed * dt) }.filter { it.x + pipeWidth > 0f }
    game.pipes = moved

    // Спавн новых труб
    val lastX = game.pipes.maxOfOrNull { it.x } ?: (w - pipeSpacing)
    if (lastX < w - pipeSpacing) {
        val margin = 140f
        val gapY = Random.nextFloat() * (h - groundHeight - 2 * margin - gapHeight) + margin + gapHeight / 2
        game.pipes = game.pipes + Pipe(x = w + 20f, gapY = gapY, gapHeight = gapHeight)
    }

    // Подсчёт очков
    game.pipes.forEach { pipe ->
        if (!pipe.passed && pipe.x + pipeWidth < game.birdX - birdRadius) {
            pipe.passed = true
            game.score++
        }
    }

    // Коллизии
    val birdRect = Rect(
        game.birdX - birdRadius, game.birdY - birdRadius,
        game.birdX + birdRadius, game.birdY + birdRadius
    )
    var collision = game.birdY + birdRadius >= h - groundHeight || game.birdY - birdRadius <= 0f
    if (!collision) {
        for (pipe in game.pipes) {
            val topRect = Rect(pipe.x, 0f, pipe.x + pipeWidth, pipe.gapY - gapHeight / 2)
            val bottomRect = Rect(pipe.x, pipe.gapY + gapHeight / 2, pipe.x + pipeWidth, h - groundHeight)
            if (birdRect.overlaps(topRect) || birdRect.overlaps(bottomRect)) {
                collision = true
                break
            }
        }
    }

    if (collision) {
        game.state = GameState.GameOver
        if (game.score > game.highScore) {
            game.highScore = game.score
            prefs.edit().putInt("high_score", game.highScore).apply()
        }
    }
}

private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    centerX: Float,
    centerY: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color
) {
    val layout = measurer.measure(
        text = text,
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, color = color)
    )
    drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, centerY - layout.size.height / 2f))
}
