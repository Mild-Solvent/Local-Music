package com.lyra.player.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

/**
 * The cute water theme: colours, the illustrated fish, sea slug, corals and bubbles, all drawn
 * from the same shapes as the approved design. Everything here is decoration only.
 */
val LocalWater = staticCompositionLocalOf { false }

object Sea {
    val Water = Color(0xFF049DEB)
    val Tile = Color(0xFF74D0E0)
    val RowBg = Color(0xFFCBE6E1)
    val Cream = Color(0xFFFAF2E0)
    val Blue = Color(0xFF2470DF)
    val FishBody = Color(0xFF358FDD)
    val Belly = Color(0xFF5DCCDF)
    val Pupil = Color(0xFF1B4FA8)
    val Seabed = Color(0xFF2C7BDC)
    val MidWave = Color(0xFF429FE3)
    val YellowCoral = Color(0xFFF8DC79)
    val Yellow = Color(0xFFF6C445)
    val YellowFin = Color(0xFFE8A92E)
    val BlueCoral = Color(0xFF1F63C9)
    val Slug = Color(0xFF93B8F0)
    val Navy = Color(0xFF08294F)
    val Muted = Color(0xFF2E5A8A)
    val Mint = Color(0xFF9ADDEA)
    val BackCoral = Color(0xFF2BB0F2)
    val BackCoralDeep = Color(0xFF0A8FD8)
    val Star = Color(0xFF1B4FA8)
    val LightText = Color(0xFFD3F0F7)
}

// ---- shapes from the design (SVG path data) ----
private const val TAIL = "M28 40 C20 30 12 22 7 24 C2 27 6 35 9 40 C6 45 2 53 7 56 C12 58 20 50 28 40Z"
private const val DORSAL = "M35.82 26.64 C37.82 14.64 54.3 11.95 66.94 14.13 C83.47 16.99 87 24.91 79 24.91 C69 24.91 43.82 28.64 35.82 26.64 Z"
private const val BODY = "M20 40 C30 18 80 14 104 32 C112 38 112 46 104 50 C80 66 32 64 20 40Z"
private const val BODY_SLIM = "M20 40 C25.14 28.68 40.87 22.13 58.19 20.88 C74.55 19.71 92.34 23.26 104 32 C112 38 112 46 104 50 C80 66 32 64 20 40 Z"
private const val BELLY = "M30 46 C50 60 84 60 100 50 C84 56 50 56 30 46Z"
private const val PECTORAL = "M63 46 C59 54 59.36 64.45 66.36 64.45 C73.36 64.45 77.36 59.91 75.36 51.91 Z"
private const val SLUG_FOOT = "M9 34 C7 40 3 44 1 47 C0 49 3 50 7 49 C11 52 15 55 21 55 L78 55 C88 55 92.19 52.88 95.19 48.88 C97.35 46 96.9 35.16 85.14 27.26 Z"
private const val SLUG_BODY = "M14 46 C4 44 6 30 18 26 C30 20 44 20 56 20 C76 20 92 28 92 40 C92 48 80 50 56 50 C38 50 22 48 14 46Z"
private const val SLUG_STRIPE = "M6.67 43.05 C7.45 43.75 10.33 44.06 12.73 45.23 C15.63 46.65 16.02 48.83 23.62 49.19 C40.47 49.98 60.25 50.76 83.94 49.39 C88.25 49.14 91.21 48.82 94.19 45.24"
private const val BRAIN = "M486.25 106.75 C493.62 97.54 497.57 113.05 506.42 105.95 C507.18 105.34 514.71 98.55 515.58 97.68 C521.58 91.68 530.25 104.5 533.5 98.25 M489.88 125.62 C496.75 117.03 498.29 127.94 507.77 128.02 C514.66 128.08 516.04 120 520.07 117.99 C526.18 114.94 529.86 122 536.74 117.63"
private const val CREAM_FISH = "M318 120 C330 104 356 102 370 110 C380 115 380 126 370 131 C356 139 330 138 318 120Z"
private const val CREAM_TAIL = "M324 120 C316 112 308 108 306 112 C308 116 310 118 310 120 C310 122 308 124 306 128 C308 132 316 128 324 120Z"
private const val STAR = "M12 2.5c1 2.6 1.8 5 2.4 6.6 2.2.3 4.6.3 7.1.6-2 1.7-3.9 3.2-5.4 4.4.6 2.3 1.2 4.6 1.6 6.3-1.9-1.2-3.9-2.3-5.7-3.4-1.8 1.1-3.8 2.2-5.7 3.4.4-1.7 1-4 1.6-6.3C6.4 12.9 4.5 11.4 2.5 9.7c2.5-.3 4.9-.3 7.1-.6.6-1.6 1.4-4 2.4-6.6z"
private val BIG_CORAL = listOf(
    "M0 0 C-10 -80 10 -140 0 -220", "M0 -150 C-40 -180 -80 -220 -90 -300", "M-90 -300 C-110 -350 -125 -400 -130 -460",
    "M-90 -300 C-70 -340 -60 -380 -58 -420", "M0 -220 C30 -250 60 -290 70 -350", "M70 -350 C90 -380 115 -410 125 -440",
    "M0 -220 C-6 -270 -4 -320 -10 -380", "M-130 -460 C-140 -480 -150 -490 -160 -500", "M70 -350 C66 -390 70 -420 76 -450",
)

private val pathCache = HashMap<String, Path>()
private fun p(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/** Soft rounded star used instead of the heart (liked songs). */
val RoundStar: ImageVector by lazy {
    ImageVector.Builder("RoundStar", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(STAR).toNodes(),
        fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black),
        strokeLineWidth = 3f, strokeLineJoin = StrokeJoin.Round,
    ).build()
}
val RoundStarOutline: ImageVector by lazy {
    ImageVector.Builder("RoundStarOutline", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(STAR).toNodes(),
        stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineJoin = StrokeJoin.Round,
    ).build()
}

// ---- motion: one slow clock, every animation is a whole number of cycles in it ----
@Composable
private fun seaClock(): State<Float> = rememberInfiniteTransition(label = "sea").animateFloat(
    0f, 360f, infiniteRepeatable(tween(360_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
)

/** 0 → 1 → 0 ease-in-out wave, like the CSS keyframes in the design. */
private fun wave(t: Float, period: Float, delay: Float = 0f) = ((1 - cos(2 * PI * (t + delay) / period)) / 2).toFloat()

// ---- drawing helpers (design units) ----
private inline fun DrawScope.at(x: Float, y: Float, s: Float = 1f, block: DrawScope.() -> Unit) =
    withTransform({ translate(x, y); scale(s, s, Offset.Zero) }, block)

/** Fit a design that is [w] units wide into this canvas. */
private inline fun DrawScope.design(w: Float, block: DrawScope.(h: Float) -> Unit) {
    val k = size.width / w
    withTransform({ scale(k, k, Offset.Zero) }) { block(size.height / k) }
}

private fun DrawScope.oval(c: Color, cx: Float, cy: Float, rx: Float, ry: Float, deg: Float = 0f) =
    rotate(deg, Offset(cx, cy)) { drawOval(c, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2)) }

private fun DrawScope.strokes(ds: List<String>, c: Color, w: Float, alpha: Float = 1f) {
    val st = Stroke(w, cap = StrokeCap.Round)
    ds.forEach { drawPath(p(it), c, alpha = alpha, style = st) }
}

private fun DrawScope.blueFish() {
    drawPath(p(TAIL), Sea.Blue); drawPath(p(DORSAL), Sea.Blue)
    drawPath(p(BODY), Sea.FishBody); drawPath(p(BELLY), Sea.Belly); drawPath(p(PECTORAL), Sea.Blue)
    drawCircle(Sea.Cream, 7f, Offset(92f, 36f)); drawCircle(Sea.Pupil, 4.5f, Offset(93f, 36f))
}

private fun DrawScope.smallFish(body: Color, fin: Color, slim: Boolean = false, bigEye: Boolean = false) {
    drawPath(p(TAIL), fin); drawPath(p(if (slim) BODY_SLIM else BODY), body)
    drawCircle(Sea.Cream, if (bigEye) 8f else 7f, Offset(92f, 36f)); drawCircle(Sea.Pupil, if (bigEye) 5f else 4.5f, Offset(93f, 36f))
}

private fun DrawScope.seaSlug() {
    drawPath(p(SLUG_FOOT), Sea.Slug); drawPath(p(SLUG_BODY), Sea.Slug)
    drawPath(p(SLUG_STRIPE), Sea.Cream, style = Stroke(4f, cap = StrokeCap.Round))
    oval(Sea.Yellow, 16f, 26f, 4.5f, 9f, -50f); oval(Sea.Yellow, 22f, 21f, 4.5f, 9.5f, -20f); oval(Sea.Yellow, 30f, 20f, 4f, 9f, -10f)
    drawCircle(Sea.Seabed, 3f, Offset(52f, 32f)); drawCircle(Sea.Seabed, 3f, Offset(64f, 30f)); drawCircle(Sea.Seabed, 2.5f, Offset(58f, 40f))
    oval(Sea.Yellow, 71f, 22f, 4.5f, 10.5f, -40f); oval(Sea.Yellow, 84f, 19f, 3.5f, 10f)
    drawCircle(Sea.Navy, 2.5f, Offset(82f, 36f))
}

private fun DrawScope.brainCoral(cx: Float, cy: Float, r: Float, t: Float) {
    val s = 1f + 0.06f * wave(t, 4f)
    withTransform({ scale(s, s, Offset(cx, cy)) }) {
        drawCircle(Sea.Blue, r, Offset(cx, cy))
        at(cx, cy, r / 28f) { at(-512f, -112f) { drawPath(p(BRAIN), Sea.Belly, style = Stroke(3.5f, cap = StrokeCap.Round)) } }
    }
}

private fun DrawScope.swaying(pivot: Offset, t: Float, period: Float, delay: Float, reverse: Boolean, block: DrawScope.() -> Unit) {
    val deg = (-3f + 6f * wave(t, period, delay)) * if (reverse) -1 else 1
    rotate(deg, pivot, block)
}

private fun DrawScope.bubbles(t: Float, pts: List<Triple<Float, Float, Float>>) {
    pts.forEachIndexed { i, (x, y, r) ->
        val ph = ((t + i * 1f) % 3f) / 3f
        val a = if (ph < 0.3f) ph / 0.3f else 1f - (ph - 0.3f) / 0.7f
        drawCircle(Sea.Cream, r, Offset(x, y + 6f - 22f * ph), alpha = a.coerceIn(0f, 1f))
    }
}

private fun DrawScope.swim(t: Float, period: Float, delay: Float = 0f, block: DrawScope.() -> Unit) {
    val w = wave(t, period, delay)
    at(8f * w, -5f * w, 1f, block)
}

// ---- composables ----

enum class Backdrop { HOME, LIBRARY, PLAIN, PLAYER }

/** Full-screen water with soft corals rooted at the bottom edge, drawn behind everything. */
@Composable
fun OceanBackdrop(kind: Backdrop, modifier: Modifier = Modifier) {
    val clock = seaClock()
    Canvas(modifier) {
        drawRect(Sea.Water)
        design(390f) { h ->
            fun big(x: Float, sx: Float, s: Float, c: Color, a: Float) =
                withTransform({ translate(x, h); scale(sx, s, Offset.Zero) }) { strokes(BIG_CORAL, c, 20f, a) }
            if (kind == Backdrop.PLAYER) {
                big(70f, -0.9f, 0.9f, Sea.BackCoral, 0.45f); big(330f, 1f, 1f, Sea.BackCoral, 0.45f)
            } else {
                big(180f, 1.05f, 1.05f, Sea.BackCoral, 0.5f); big(360f, -0.75f, 0.75f, Sea.BackCoralDeep, 0.4f); big(30f, 0.55f, 0.55f, Sea.BackCoralDeep, 0.4f)
            }
            val t = clock.value
            when (kind) {
                Backdrop.HOME -> swim(t, 9f) {
                    at(-10f, 0f) { drawPath(p(CREAM_FISH), Sea.Cream); drawPath(p(CREAM_TAIL), Sea.Cream); drawCircle(Sea.Blue, 3f, Offset(365f, 116f)) }
                }
                Backdrop.LIBRARY -> at(232f - 10f * wave(t, 8f), 50f, 140f / 104f) { at(2f, 0f) { seaSlug() } }
                else -> Unit
            }
        }
    }
}

/** The big "now playing" picture on Home: fish over coral (350 × 196). */
@Composable
fun HeroScene(modifier: Modifier = Modifier) {
    val clock = seaClock()
    Canvas(modifier) {
        drawRect(Sea.Tile)
        design(350f) {
            val t = clock.value
            drawPath(p("M0 0 H350 V30 C300 46 250 18 190 30 C130 42 70 20 0 34Z"), Sea.Seabed, alpha = 0.55f)
            drawPath(p("M0 150 C40 128 80 140 110 150 C150 162 170 134 210 140 C250 146 290 126 350 140 V196 H0Z"), Sea.MidWave)
            drawPath(p("M0 172 C60 160 120 176 180 168 C240 160 300 176 350 166 V196 H0Z"), Sea.Seabed)
            swaying(Offset(40f, 166f), t, 5f, 0f, false) {
                strokes(listOf("M44 166 V128", "M44 148 C34 142 30 132 30 118", "M44 140 C54 134 58 124 58 112", "M30 130 C24 126 22 120 22 112"), Sea.YellowCoral, 9f)
            }
            swaying(Offset(311f, 170f), t, 6f, 2f, true) {
                strokes(listOf("M306 170 V120", "M306 146 C296 140 292 128 292 114", "M306 134 C316 128 320 116 320 102", "M320 118 C328 114 330 106 330 98"), Sea.BlueCoral, 7f)
            }
            brainCoral(250f, 170f, 16f, t)
            // bubbles come out of the fish's mouth and swim along with it
            swim(t, 4f) {
                at(128f, 52f) { blueFish() }
                bubbles(t, listOf(Triple(244f, 76f, 4f), Triple(252f, 58f, 6f), Triple(246f, 38f, 5f)))
            }
            swim(t, 3f, 1.5f) { at(206f, 120f, 0.5f) { smallFish(Sea.Yellow, Sea.YellowFin) } }
        }
    }
}

/** Square cover shown in the player when a song has no artwork of its own (350 × 350). */
@Composable
fun CoverScene(modifier: Modifier = Modifier) {
    val clock = seaClock()
    Canvas(modifier) {
        drawRect(Sea.Tile)
        design(350f) {
            val t = clock.value
            drawPath(p("M0 0 H350 V44 C300 64 250 30 190 44 C130 58 70 30 0 50Z"), Sea.Seabed, alpha = 0.55f)
            drawPath(p("M30 70 C42 60 64 60 76 68 C70 72 70 78 76 82 C64 90 42 88 30 78Z"), Sea.Mint)
            drawPath(p("M34 74 C26 66 18 62 16 66 C18 70 20 72 20 74 C20 76 18 78 16 82 C18 86 26 82 34 74Z"), Sea.Mint)
            drawPath(p("M0 266 C50 240 100 256 140 266 C190 280 220 246 260 254 C300 262 320 244 350 250 V350 H0Z"), Sea.MidWave)
            drawPath(p("M0 300 C70 286 140 306 210 296 C270 288 310 304 350 294 V350 H0Z"), Sea.Seabed)
            swaying(Offset(66f, 300f), t, 5f, 0f, false) {
                strokes(listOf("M66 300 V232", "M66 266 C50 256 44 238 44 214", "M66 250 C82 240 88 222 88 200", "M44 236 C34 230 30 220 30 206", "M88 222 C98 216 102 206 102 192"), Sea.YellowCoral, 13f)
            }
            swaying(Offset(307f, 306f), t, 6f, 2f, true) {
                strokes(listOf("M300 306 V222", "M300 266 C286 256 280 238 280 214", "M300 246 C314 236 320 218 320 194", "M320 214 C330 208 334 198 334 186"), Sea.BlueCoral, 10f)
            }
            brainCoral(210f, 300f, 26f, t)
            drawOval(Sea.Cream, Offset(118f, 291f), Size(32f, 22f)); drawCircle(Sea.Blue, 6.5f, Offset(134f, 293f))
            swim(t, 4f) {
                at(70f, 104f, 1.75f) { blueFish() }
                bubbles(t, listOf(Triple(272f, 150f, 6f), Triple(284f, 120f, 9f), Triple(276f, 90f, 7f)))
            }
            swim(t, 3f, 1.5f) { at(224f, 200f, 0.42f) { smallFish(Sea.Yellow, Sea.YellowFin) } }
        }
    }
}

/** Little seabed with coral, brain coral and the crawling sea slug, along the bottom of the player (390 × 70). */
@Composable
fun SeabedStrip(modifier: Modifier = Modifier) {
    val clock = seaClock()
    Canvas(modifier) {
        design(390f) {
            val t = clock.value
            drawPath(p("M0 40 C60 26 130 46 200 36 C270 26 330 44 390 34 V70 H0Z"), Sea.Seabed)
            swaying(Offset(40f, 70f), t, 5f, 0f, false) {
                strokes(listOf("M40 70 V36", "M40 52 C30 46 28 38 28 28", "M40 46 C50 40 52 32 52 24"), Sea.YellowCoral, 8f)
            }
            brainCoral(150f, 44f, 13f, t)
            at(244f - 10f * wave(t, 8f), -6f, 0.8f) { seaSlug() }
        }
    }
}

private val tilePalettes = listOf(
    Triple(Sea.Tile, Sea.FishBody, Sea.Blue),
    Triple(Sea.Belly, Sea.Yellow, Sea.YellowFin),
    Triple(Sea.Cream, Sea.Slug, Sea.Seabed),
    Triple(Sea.Tile, Sea.YellowCoral, Color(0xFFE0B84E)),
    Triple(Sea.Belly, Sea.FishBody, Sea.Blue),
    Triple(Sea.Cream, Sea.Yellow, Sea.YellowFin),
)

/** Cover for songs without artwork: a little fish on a water tile, colour picked from [seed]. */
@Composable
fun FishTile(seed: String, modifier: Modifier = Modifier) {
    val (bg, body, fin) = tilePalettes[(seed.hashCode() and 0x7fffffff) % tilePalettes.size]
    Canvas(modifier) {
        drawRect(bg)
        design(50f) {
            drawPath(p("M0 38 C14 33 34 42 50 36 V50 H0Z"), Sea.Seabed)
            at(5f, 12f, 0.36f) { smallFish(body, fin, bigEye = true) }
        }
    }
}

/** The wavy progress line: a cream zig-zag over the light track (Slider track slot). */
@Composable
fun WavyTrack(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cy = size.height / 2
        val th = 7.dp.toPx()
        val cr = CornerRadius(th / 2)
        drawRoundRect(Sea.Tile, Offset(0f, cy - th / 2), Size(size.width, th), cr)
        val end = size.width * fraction.coerceIn(0f, 1f)
        drawRoundRect(Sea.Water, Offset(0f, cy - th / 2), Size(end, th), cr)
        val seg = 20.dp.toPx()
        val amp = 8.dp.toPx()
        val path = Path().apply {
            moveTo(0f, cy)
            var x = 0f; var up = true
            while (x < end + seg) { quadraticBezierTo(x + seg / 2, if (up) cy - amp else cy + amp, x + seg, cy); x += seg; up = !up }
        }
        clipRect(right = end) { drawPath(path, Sea.Cream, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)) }
    }
}

/** The yellow fish that rides the progress line (Slider thumb slot). */
@Composable
fun FishThumb(modifier: Modifier = Modifier) {
    Canvas(modifier) { design(112f) { smallFish(Sea.Yellow, Sea.YellowFin, slim = true, bigEye = true) } }
}
