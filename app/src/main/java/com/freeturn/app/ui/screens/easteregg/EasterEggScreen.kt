package com.freeturn.app.ui.screens.easteregg

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Памятник-через-действие заблокированным и частично ограниченным сервисам.
 *
 * Все узлы появляются сразу. Сеть остаётся полностью построенной.
 * Узлы последовательно блокируются: каждый следующий сервис постепенно краснеет,
 * пока в финале вся сеть не становится красной.
 *
 * Пакеты не бегают между двумя полностью заблокированными узлами.
 *
 * Метафора:
 * "The Net interprets censorship as damage and routes around it"
 * — John Gilmore, 1993.
 */

private data class Memorial(
    val name: String,
    val status: String,
    val date: String,
    val epitaph: String,
)

private val MEMORIALS = listOf(
    Memorial(
        name = "Telegram",
        status = "введены частичные ограничения",
        date = "—",
        epitaph = "Не открывается? Открой через прокси. Не помогло? Другой прокси.",
    ),
    Memorial(
        name = "YouTube",
        status = "введены частичные ограничения",
        date = "—",
        epitaph = "1080p → 480p → кружок загрузки.",
    ),
    Memorial(
        name = "Dailymotion",
        status = "заблокирован",
        date = "28 января 2017 года",
        epitaph = "Видеоархив альтернативного интернета. Был тихим, стал недоступным.",
    ),
    Memorial(
        name = "Discord",
        status = "заблокирован",
        date = "8 октября 2024 года",
        epitaph = "«Подключение...». А вчера ещё сидели с друзьями",
    ),
    Memorial(
        name = "Envato",
        status = "заблокирован",
        date = "16 июля 2024 года",
        epitaph = "Шаблоны, темы, ассеты и чужая работа, внезапно оказавшаяся за стеной.",
    ),
    Memorial(
        name = "FaceTime",
        status = "заблокирован",
        date = "3 декабря 2025 года",
        epitaph = "Друг звонил. Не дозвонился.",
    ),
    Memorial(
        name = "Facebook*",
        status = "заблокирован",
        date = "4 марта 2022 года",
        epitaph = "Фермы, группы, дни рождения и старые фотографии. Соцсеть, превращённая в археологический слой.",
    ),
    Memorial(
        name = "Instagram*",
        status = "заблокирован",
        date = "13 марта 2022 года",
        epitaph = "Сторис, закаты, малый бизнес, кофейни и витрины.",
    ),
    Memorial(
        name = "LinkedIn",
        status = "заблокирован",
        date = "10 ноября 2016 года",
        epitaph = "Заблокировали первым. Многие заметили только при поиске работы.",
    ),
    Memorial(
        name = "Metacritic",
        status = "заблокирован",
        date = "19 октября 2022 года",
        epitaph = "Оценку игре теперь тоже надо смотреть через обход.",
    ),
    Memorial(
        name = "Patreon",
        status = "заблокирован",
        date = "7 августа 2022 года",
        epitaph = "Поддержать автора стало отдельным квестом.",
    ),
    Memorial(
        name = "Roblox",
        status = "заблокирован",
        date = "2 декабря 2025 года",
        epitaph = "Дети спросили, почему не работает. Взрослые сделали вид, что знают.",
    ),
    Memorial(
        name = "Rutracker",
        status = "заблокирован",
        date = "12 февраля 2015 года",
        epitaph = "Заблокирован. Работает. Заблокирован. Работает.",
    ),
    Memorial(
        name = "Signal",
        status = "заблокирован",
        date = "9 августа 2024 года",
        epitaph = "Слишком мало шума, слишком много смысла.",
    ),
    Memorial(
        name = "SoundCloud",
        status = "заблокирован",
        date = "2 октября 2022 года",
        epitaph = "Тот самый микс теперь лежит где-то у кого-то на флешке.",
    ),
    Memorial(
        name = "Speedtest",
        status = "заблокирован",
        date = "30 июля 2025 года",
        epitaph = "Когда даже измерить скорость стало слишком опасным действием.",
    ),
    Memorial(
        name = "X (Twitter)",
        status = "заблокирован",
        date = "4 марта 2022 года",
        epitaph = "До того, как стал X",
    ),
    Memorial(
        name = "Фикбук",
        status = "заблокирован",
        date = "12 июля 2024 года",
        epitaph = "Фандомы, тексты, пейринги и ночные главы. Архив чувств, закрытый как угроза.",
    ),
    Memorial(
        name = "Viber",
        status = "заблокирован",
        date = "13 декабря 2024 года",
        epitaph = "Семейные чаты, стикеры, поздравления и звонки",
    ),
    Memorial(
        name = "WhatsApp*",
        status = "заблокирован",
        date = "28 ноября 2025 года",
        epitaph = "Голосовухи от родителей, домовые чаты и зелёные галочки. Повседневность тоже блокируют.",
    ),
)

private val QUOTES = listOf(
    "«Сеть воспринимает цензуру как повреждение и обходит её.» — Джон Гилмор, 1993",
    "«Информация хочет быть свободной.» — Стюарт Бранд, 1984",
    "«В интернете никто не знает, что ты собака.» — Стайнер, 1993",
    "«Код — это закон.» — Лоуренс Лессиг",
    "Connection reset by peer",
    "Этот сайт больше недоступен на территории...",
    "404 Not Found",
)

private data class Palette(
    val edge: Color,
    val nodeCore: Color,
    val nodeGlow: Color,
    val packet: Color,
    val text: Color,
    val textDim: Color,
    val labelBackground: Color,
)

private data class LabelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private class Node(
    var x: Float,
    var y: Float,
    val memorial: Memorial,
    var phase: Float = Random.nextFloat() * TWO_PI,
    var radiusBase: Float = 4.5f + Random.nextFloat() * 2.5f,
    var blockStartAt: Long? = null,
    var blockDurationMs: Long = randomBlockDurationMs(),
    var blocked: Boolean = false,
)

private class Packet(
    var fromIdx: Int,
    var toIdx: Int,
    var t: Float = 0f,
    var speed: Float = 0.18f + Random.nextFloat() * 0.25f,
)

@Composable
fun EasterEggDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        )
    ) {
        val view = LocalView.current

        LaunchedEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window

            window?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
            )

            window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
        }

        EasterEggScreen()
    }
}

@Composable
private fun EasterEggScreen() {
    val nodes = remember { mutableStateListOf<Node>() }
    val packets = remember { mutableStateListOf<Packet>() }
    val adjacency = remember { mutableStateListOf<List<Int>>() }
    val blockingOrder = remember { mutableStateListOf<Int>() }

    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var tooltip by remember { mutableStateOf<Pair<Memorial, Offset>?>(null) }
    var quoteIdx by remember { mutableStateOf(0) }
    var frameTick by remember { mutableStateOf(0L) }

    val palette = remember {
        Palette(
            edge = Color(0xFF7DD3FC),
            nodeCore = Color(0xFF38BDF8),
            nodeGlow = Color(0xFF0EA5E9),
            packet = Color(0xFFFACC15),
            text = Color(0xFFF8FAFC),
            textDim = Color(0xFFCBD5E1),
            labelBackground = Color(0xCC05060A),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(7000)
            quoteIdx = (quoteIdx + 1) % QUOTES.size
        }
    }

    LaunchedEffect(tooltip) {
        if (tooltip != null) {
            kotlinx.coroutines.delay(3500)
            tooltip = null
        }
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize == Offset.Zero) return@LaunchedEffect

        if (nodes.isEmpty()) {
            seedNodes(
                nodes = nodes,
                size = canvasSize,
            )

            rebuildAdjacency(
                nodes = nodes,
                adj = adjacency,
            )

            blockingOrder.clear()
            blockingOrder.addAll(nodes.indices.shuffled())

            seedPackets(
                packets = packets,
                nodes = nodes,
                adj = adjacency,
                count = 32,
            )
        }

        var lastFrame = 0L

        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrame == 0L) {
                    0.016f
                } else {
                    ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                }

                lastFrame = now

                tick(
                    dt = dt,
                    nodes = nodes,
                    packets = packets,
                    adj = adjacency,
                    blockingOrder = blockingOrder,
                )

                frameTick = now
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tap ->
                            val hit = nodes.indexOfFirst { node ->
                                node.distanceTo(tap) < TAP_HIT_RADIUS
                            }

                            if (hit >= 0) {
                                val node = nodes[hit]
                                tooltip = node.memorial to Offset(node.x, node.y)
                            } else {
                                tooltip = null
                            }
                        }
                    )
                }
        ) {
            if (canvasSize != Offset(size.width, size.height)) {
                canvasSize = Offset(size.width, size.height)
            }

            drawNetwork(
                nodes = nodes,
                packets = packets,
                adj = adjacency,
                palette = palette,
                frameTick = frameTick,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Памятник заблокированным сервисам",
                color = palette.text,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "тап по узлу — эпитафия",
                color = palette.textDim,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }

        AnimatedVisibility(
            visible = tooltip != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            tooltip?.let { (m, _) ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(32.dp),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 12.dp,
                        )
                    ) {
                        Text(
                            text = m.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )

                        Text(
                            text = m.status,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )

                        Text(
                            text = m.date,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = m.epitaph,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = QUOTES[quoteIdx],
                label = "quote",
                animationSpec = tween(900),
            ) { quote ->
                Text(
                    text = quote,
                    color = palette.textDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Логика ────────────────────────────────────────────────────────────────────

private fun seedNodes(
    nodes: SnapshotStateList<Node>,
    size: Offset,
) {
    val shuffledMemorials = MEMORIALS.shuffled()
    val positions = generateGraphPositions(
        size = size,
        count = shuffledMemorials.size,
    )

    shuffledMemorials.forEachIndexed { index, memorial ->
        val p = positions[index]

        nodes.add(
            Node(
                x = p.x,
                y = p.y,
                memorial = memorial,
            )
        )
    }
}

/**
 * Генерация позиций, похожих на живой граф:
 * несколько кластеров вокруг центра + отталкивание узлов.
 */
private fun generateGraphPositions(
    size: Offset,
    count: Int,
): List<Offset> {
    if (count <= 0 || size == Offset.Zero) return emptyList()

    val center = Offset(
        x = size.x * 0.5f,
        y = size.y * 0.52f,
    )

    val safeWidth = size.x * 0.82f
    val safeHeight = size.y * 0.62f

    val clusterCount = 5
    val clusterRadiusX = safeWidth * 0.32f
    val clusterRadiusY = safeHeight * 0.30f

    val clusters = List(clusterCount) { index ->
        val angle = TWO_PI * index / clusterCount + Random.nextFloat() * 0.25f

        Offset(
            x = center.x + cos(angle.toDouble()).toFloat() * clusterRadiusX,
            y = center.y + sin(angle.toDouble()).toFloat() * clusterRadiusY,
        )
    }

    val points = List(count) { index ->
        val cluster = clusters[index % clusterCount]

        val localAngle = Random.nextFloat() * TWO_PI
        val localDistance = 52f + Random.nextFloat() * 145f

        val x = cluster.x + cos(localAngle.toDouble()).toFloat() * localDistance
        val y = cluster.y + sin(localAngle.toDouble()).toFloat() * localDistance

        Offset(
            x = x.coerceIn(size.x * 0.08f, size.x * 0.92f),
            y = y.coerceIn(size.y * 0.20f, size.y * 0.80f),
        )
    }

    return points.relaxPositions(
        minDistance = 92f,
        width = size.x,
        height = size.y,
    )
}

private fun List<Offset>.relaxPositions(
    minDistance: Float,
    width: Float,
    height: Float,
): List<Offset> {
    val points = toMutableList()

    repeat(140) {
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val a = points[i]
                val b = points[j]

                val dx = b.x - a.x
                val dy = b.y - a.y
                val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (distance > 0.01f && distance < minDistance) {
                    val push = (minDistance - distance) * 0.54f
                    val nx = dx / distance
                    val ny = dy / distance

                    points[i] = Offset(
                        x = (a.x - nx * push).coerceIn(width * 0.07f, width * 0.93f),
                        y = (a.y - ny * push).coerceIn(height * 0.20f, height * 0.80f),
                    )

                    points[j] = Offset(
                        x = (b.x + nx * push).coerceIn(width * 0.07f, width * 0.93f),
                        y = (b.y + ny * push).coerceIn(height * 0.20f, height * 0.80f),
                    )
                }
            }
        }
    }

    return points
}

/**
 * Строит полностью связную сеть.
 *
 * 1. Связный каркас: минимальное дерево по близости.
 * 2. Локальные связи: каждый узел получает несколько ближайших соседей.
 * 3. Дальние мосты: несколько длинных связей, чтобы граф выглядел живее.
 */
private fun rebuildAdjacency(
    nodes: List<Node>,
    adj: SnapshotStateList<List<Int>>,
) {
    val n = nodes.size
    val sets = Array(n) { mutableSetOf<Int>() }

    if (n >= 2) {
        val connected = mutableListOf(0)
        val remaining = (1 until n).toMutableList()

        while (remaining.isNotEmpty()) {
            var bestFrom = connected.first()
            var bestTo = remaining.first()
            var bestDistance = Float.MAX_VALUE

            for (from in connected) {
                for (to in remaining) {
                    val distance = nodes[from].distanceTo(nodes[to])

                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestFrom = from
                        bestTo = to
                    }
                }
            }

            sets[bestFrom].add(bestTo)
            sets[bestTo].add(bestFrom)

            connected.add(bestTo)
            remaining.remove(bestTo)
        }

        for (i in nodes.indices) {
            val localDegree = Random.nextInt(2, 5)

            val nearest = nodes.indices
                .filter { it != i }
                .map { it to nodes[i].distanceTo(nodes[it]) }
                .sortedBy { it.second }
                .take(localDegree)
                .map { it.first }

            nearest.forEach { j ->
                sets[i].add(j)
                sets[j].add(i)
            }
        }

        repeat((n * 0.45f).toInt()) {
            val a = Random.nextInt(n)

            val candidates = nodes.indices
                .filter { it != a && it !in sets[a] }
                .map { it to nodes[a].distanceTo(nodes[it]) }
                .sortedByDescending { it.second }
                .take((n / 3).coerceAtLeast(1))
                .map { it.first }

            val b = candidates.randomOrNull()

            if (b != null) {
                sets[a].add(b)
                sets[b].add(a)
            }
        }
    }

    adj.clear()
    sets.forEach { adj.add(it.toList()) }
}

private fun seedPackets(
    packets: SnapshotStateList<Packet>,
    nodes: List<Node>,
    adj: List<List<Int>>,
    count: Int,
) {
    val available = nodes.indices.filter { from ->
        adj.getOrNull(from)
            .orEmpty()
            .any { to ->
                val a = nodes.getOrNull(from)
                val b = nodes.getOrNull(to)

                a != null && b != null && isPacketEdgeAllowed(a, b)
            }
    }

    if (available.isEmpty()) return

    repeat(count) {
        val from = available.random()

        val candidates = adj[from].filter { to ->
            val a = nodes.getOrNull(from)
            val b = nodes.getOrNull(to)

            a != null && b != null && isPacketEdgeAllowed(a, b)
        }

        val to = candidates.randomOrNull() ?: return@repeat

        packets.add(
            Packet(
                fromIdx = from,
                toIdx = to,
            )
        )
    }
}

private fun tick(
    dt: Float,
    nodes: SnapshotStateList<Node>,
    packets: SnapshotStateList<Packet>,
    adj: List<List<Int>>,
    blockingOrder: List<Int>,
) {
    val now = System.currentTimeMillis()

    nodes.forEach { node ->
        node.phase += dt * 1.6f
    }

    updateBlockingSequence(
        nodes = nodes,
        blockingOrder = blockingOrder,
        now = now,
    )

    movePackets(
        dt = dt,
        nodes = nodes,
        packets = packets,
        adj = adj,
    )

    maintainPackets(
        packets = packets,
        nodes = nodes,
        adj = adj,
    )
}

private fun updateBlockingSequence(
    nodes: SnapshotStateList<Node>,
    blockingOrder: List<Int>,
    now: Long,
) {
    val active = nodes.firstOrNull { node ->
        node.blockStartAt != null && !node.blocked
    }

    if (active != null) {
        val startedAt = active.blockStartAt ?: return

        if (now - startedAt >= active.blockDurationMs) {
            active.blocked = true
        }

        return
    }

    val nextIndex = blockingOrder.firstOrNull { index ->
        val node = nodes.getOrNull(index)
        node != null && node.blockStartAt == null && !node.blocked
    } ?: return

    nodes[nextIndex].blockStartAt = now
}

private fun movePackets(
    dt: Float,
    nodes: List<Node>,
    packets: SnapshotStateList<Packet>,
    adj: List<List<Int>>,
) {
    if (nodes.isEmpty()) return

    val toRemove = mutableListOf<Int>()

    packets.forEachIndexed { index, packet ->
        val from = nodes.getOrNull(packet.fromIdx)
        val to = nodes.getOrNull(packet.toIdx)

        val edgeStillExists =
            from != null &&
                    to != null &&
                    adj.getOrNull(packet.fromIdx)?.contains(packet.toIdx) == true &&
                    isPacketEdgeAllowed(from, to)

        if (!edgeStillExists) {
            toRemove.add(index)
            return@forEachIndexed
        }

        packet.t += packet.speed * dt

        if (packet.t >= 1f) {
            val current = nodes.getOrNull(packet.toIdx)

            if (current == null) {
                toRemove.add(index)
                return@forEachIndexed
            }

            val neighbors = adj.getOrNull(packet.toIdx).orEmpty()

            val forwardCandidates = neighbors.filter { next ->
                next != packet.fromIdx &&
                        nodes.getOrNull(next)?.let { candidate ->
                            isPacketEdgeAllowed(current, candidate)
                        } == true
            }

            val fallbackCandidates = neighbors.filter { next ->
                nodes.getOrNull(next)?.let { candidate ->
                    isPacketEdgeAllowed(current, candidate)
                } == true
            }

            val next = forwardCandidates
                .ifEmpty { fallbackCandidates }
                .randomOrNull()

            if (next == null) {
                toRemove.add(index)
            } else {
                packet.fromIdx = packet.toIdx
                packet.toIdx = next
                packet.t = 0f
                packet.speed = 0.18f + Random.nextFloat() * 0.28f
            }
        }
    }

    toRemove
        .asReversed()
        .forEach { packets.removeAt(it) }
}

private fun maintainPackets(
    packets: SnapshotStateList<Packet>,
    nodes: List<Node>,
    adj: List<List<Int>>,
) {
    val allBlocked = nodes.isNotEmpty() && nodes.all { it.blocked }

    if (allBlocked) {
        packets.clear()
        return
    }

    val target = (nodes.size * 1.6f).toInt().coerceIn(16, 42)

    if (packets.size >= target) return

    val available = nodes.indices.filter { from ->
        adj.getOrNull(from)
            .orEmpty()
            .any { to ->
                val a = nodes.getOrNull(from)
                val b = nodes.getOrNull(to)

                a != null && b != null && isPacketEdgeAllowed(a, b)
            }
    }

    if (available.isEmpty()) return

    while (packets.size < target) {
        val from = available.random()

        val candidates = adj[from].filter { to ->
            val a = nodes.getOrNull(from)
            val b = nodes.getOrNull(to)

            a != null && b != null && isPacketEdgeAllowed(a, b)
        }

        val to = candidates.randomOrNull() ?: return

        packets.add(
            Packet(
                fromIdx = from,
                toIdx = to,
            )
        )
    }
}

// ── Отрисовка ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawNetwork(
    nodes: List<Node>,
    packets: List<Packet>,
    adj: List<List<Int>>,
    palette: Palette,
    @Suppress("UNUSED_PARAMETER") frameTick: Long,
) {
    val now = System.currentTimeMillis()
    val blockedColor = Color(0xFFE53935)

    fun blockProgress(node: Node): Float {
        val startedAt = node.blockStartAt

        return when {
            node.blocked -> 1f
            startedAt == null -> 0f
            else -> ((now - startedAt).toFloat() / node.blockDurationMs.toFloat())
                .coerceIn(0f, 1f)
        }
    }

    drawEdges(
        nodes = nodes,
        adj = adj,
        palette = palette,
        blockedColor = blockedColor,
        blockProgress = ::blockProgress,
    )

    drawNodes(
        nodes = nodes,
        palette = palette,
        blockedColor = blockedColor,
        blockProgress = ::blockProgress,
    )

    drawPackets(
        nodes = nodes,
        packets = packets,
        palette = palette,
        blockedColor = blockedColor,
        blockProgress = ::blockProgress,
    )
}

private fun DrawScope.drawEdges(
    nodes: List<Node>,
    adj: List<List<Int>>,
    palette: Palette,
    blockedColor: Color,
    blockProgress: (Node) -> Float,
) {
    val drawn = mutableSetOf<Long>()

    for (i in nodes.indices) {
        val neighbors = adj.getOrNull(i).orEmpty()

        for (j in neighbors) {
            val key = edgeKey(i, j)

            if (!drawn.add(key)) continue

            val a = nodes[i]
            val b = nodes[j]
            val distanceAlpha = (1f - (a.distanceTo(b) / 720f)).coerceIn(0.10f, 0.52f)

            val progress = maxOf(
                blockProgress(a),
                blockProgress(b),
            )

            val edgeColor = lerp(
                start = palette.edge,
                stop = blockedColor,
                fraction = progress * 0.85f,
            )

            drawLine(
                color = edgeColor.copy(alpha = distanceAlpha * 0.62f),
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = 1.15f,
            )
        }
    }
}

private fun DrawScope.drawNodes(
    nodes: List<Node>,
    palette: Palette,
    blockedColor: Color,
    blockProgress: (Node) -> Float,
) {
    val labelRects = mutableListOf<LabelRect>()

    val textPaint = android.graphics.Paint().apply {
        color = palette.text.toArgbWithAlpha(0.92f)
        textSize = 22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.NORMAL,
        )
    }

    val shadowPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(190, 0, 0, 0)
        textSize = 22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val labelBackgroundPaint = android.graphics.Paint().apply {
        color = palette.labelBackground.toArgbWithAlpha(0.92f)
        isAntiAlias = true
    }

    nodes.forEach { node ->
        val pulse = ((sin(node.phase.toDouble()) * 0.5) + 0.5).toFloat()
        val progress = blockProgress(node)

        val nodeCore = lerp(
            start = palette.nodeCore,
            stop = blockedColor,
            fraction = progress,
        )

        val nodeGlow = lerp(
            start = palette.nodeGlow,
            stop = blockedColor,
            fraction = progress,
        )

        drawCircle(
            color = nodeGlow.copy(alpha = 0.17f + pulse * 0.10f + progress * 0.06f),
            radius = node.radiusBase * 4.6f,
            center = Offset(node.x, node.y),
        )

        drawCircle(
            color = nodeGlow.copy(alpha = 0.34f + pulse * 0.14f),
            radius = node.radiusBase * 2.25f,
            center = Offset(node.x, node.y),
        )

        drawCircle(
            color = nodeCore,
            radius = node.radiusBase,
            center = Offset(node.x, node.y),
        )
    }

    nodes
        .sortedByDescending { it.radiusBase }
        .forEach { node ->
            val name = node.memorial.name
            val textWidth = textPaint.measureText(name)
            val textHeight = 24f
            val padX = 7f
            val padY = 4f
            val gap = node.radiusBase + 11f

            val candidates = listOf(
                Offset(node.x + gap, node.y + 8f),
                Offset(node.x - textWidth - gap, node.y + 8f),
                Offset(node.x - textWidth / 2f, node.y - gap),
                Offset(node.x - textWidth / 2f, node.y + gap + textHeight),
                Offset(node.x + gap, node.y - gap),
                Offset(node.x + gap, node.y + gap + textHeight),
                Offset(node.x - textWidth - gap, node.y - gap),
                Offset(node.x - textWidth - gap, node.y + gap + textHeight),
            )

            val chosenTextPos = candidates.firstOrNull { textPos ->
                val rect = LabelRect(
                    left = textPos.x - padX,
                    top = textPos.y - textHeight - padY,
                    right = textPos.x + textWidth + padX,
                    bottom = textPos.y + padY,
                )

                rect.isInsideCanvas(size.width, size.height) &&
                        labelRects.none { existing -> existing.intersects(rect) }
            } ?: candidates.first()

            val rect = LabelRect(
                left = chosenTextPos.x - padX,
                top = chosenTextPos.y - textHeight - padY,
                right = chosenTextPos.x + textWidth + padX,
                bottom = chosenTextPos.y + padY,
            )

            labelRects.add(rect)

            drawContext.canvas.nativeCanvas.apply {
                drawRoundRect(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    8f,
                    8f,
                    labelBackgroundPaint,
                )

                drawText(
                    name,
                    chosenTextPos.x + 1f,
                    chosenTextPos.y + 1f,
                    shadowPaint,
                )

                drawText(
                    name,
                    chosenTextPos.x,
                    chosenTextPos.y,
                    textPaint,
                )
            }
        }
}

private fun DrawScope.drawPackets(
    nodes: List<Node>,
    packets: List<Packet>,
    palette: Palette,
    blockedColor: Color,
    blockProgress: (Node) -> Float,
) {
    packets.forEach { packet ->
        val a = nodes.getOrNull(packet.fromIdx) ?: return@forEach
        val b = nodes.getOrNull(packet.toIdx) ?: return@forEach

        if (!isPacketEdgeAllowed(a, b)) {
            return@forEach
        }

        val x = a.x + (b.x - a.x) * packet.t
        val y = a.y + (b.y - a.y) * packet.t

        val progress = maxOf(
            blockProgress(a),
            blockProgress(b),
        )

        val packetColor = lerp(
            start = palette.packet,
            stop = blockedColor,
            fraction = progress * 0.85f,
        )

        drawCircle(
            color = packetColor.copy(alpha = 0.30f),
            radius = 7f,
            center = Offset(x, y),
        )

        drawCircle(
            color = packetColor,
            radius = 2.6f,
            center = Offset(x, y),
        )
    }
}

// ── Утилиты ───────────────────────────────────────────────────────────────────

private const val TWO_PI = 6.2831855f
private const val TAP_HIT_RADIUS = 52f

private fun Node.distanceTo(other: Node): Float {
    return hypot(
        (x - other.x).toDouble(),
        (y - other.y).toDouble(),
    ).toFloat()
}

private fun Node.distanceTo(point: Offset): Float {
    return hypot(
        (x - point.x).toDouble(),
        (y - point.y).toDouble(),
    ).toFloat()
}

private fun edgeKey(a: Int, b: Int): Long {
    val min = minOf(a, b).toLong()
    val max = maxOf(a, b).toLong()

    return (min shl 32) or max
}

private fun isPacketEdgeAllowed(
    from: Node,
    to: Node,
): Boolean {
    return !(from.blocked && to.blocked)
}

private fun randomBlockDurationMs(): Long {
    return 5_500L + Random.nextLong(3_500L)
}

private fun LabelRect.intersects(other: LabelRect): Boolean {
    return left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
}

private fun LabelRect.isInsideCanvas(
    width: Float,
    height: Float,
): Boolean {
    return left >= 6f &&
            top >= 6f &&
            right <= width - 6f &&
            bottom <= height - 6f
}

private fun Color.toArgbWithAlpha(alphaFraction: Float): Int {
    val alpha = (alphaFraction.coerceIn(0f, 1f) * 255).toInt()

    return android.graphics.Color.argb(
        alpha,
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
}