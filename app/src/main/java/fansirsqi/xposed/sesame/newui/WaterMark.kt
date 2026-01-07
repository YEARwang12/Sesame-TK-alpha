package fansirsqi.xposed.sesame.newui

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.ui.MainViewModel.Companion.verifuids
import fansirsqi.xposed.sesame.util.TimeUtil
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun WatermarkLayer(
    modifier: Modifier = Modifier,
    autoRefresh: Boolean = true,
    refreshIntervalMs: Long = 1000L,
    refreshTrigger: Any? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { 13.sp.toPx() }

    // 1. 获取颜色 (Compose State，变化时触发绘制)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f).toArgb()

    // 2. 时间状态
    var currentTime by remember { mutableStateOf(TimeUtil.getFormatDateTime()) }

    // 3. 自动刷新
    if (autoRefresh) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(refreshIntervalMs)
                currentTime = TimeUtil.getFormatDateTime()
            }
        }
    }

    // 4. 准备文本数据
    val textLines = remember(verifuids, currentTime, refreshTrigger) {
        val prefixLines = listOf("免费模块仅供学习,勿在国内平台传播!!")
        val suffix = "Now: $currentTime"
        val uidLines = if (verifuids.isEmpty()) {
            listOf("未载入账号", "请启用模块后重启一次支付宝", "确保模块生成对应账号配置")
        } else {
            verifuids.mapIndexed { index, uid -> "UID${index + 1}: $uid" }
        }
        val versionLines = listOf(
            "Ver: ${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
            "Build: ${BuildConfig.BUILD_DATE}",
        )
        prefixLines + uidLines + listOf(suffix) + versionLines
    }

    // 5. 随机偏移 (只在首次组合时生成)
    val offsetX = remember { Random.nextInt(-200, 200).toFloat() }
    val offsetY = remember { Random.nextInt(-200, 200).toFloat() }

    Box(
        modifier = modifier.drawWithCache {
            // A. 在 drawWithCache 的 lambda 中创建/更新 Paint 对象
            // 这样只有在 size 变化或者 State 变化需要重绘时才会执行
            val paint = Paint().apply {
                color = textColor
                textSize = textSizePx
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }

            val fontHeight = paint.fontSpacing
            val maxLineWidth = textLines.maxOfOrNull { paint.measureText(it) } ?: 0f
            val totalTextHeight = fontHeight * textLines.size

            val densityFactor = 0.9f
            val horizontalSpacing = (maxLineWidth * 1.5f / densityFactor)
            val verticalSpacing = (totalTextHeight * 2.5f / densityFactor)
            val rotationDegrees = -30f

            onDrawWithContent {
                // 1. 绘制内容 (原本的 UI)
                drawContent()

                // 2. 绘制水印 (覆盖在上面)
                drawContext.canvas.nativeCanvas.apply {
                    withSave {
                        val width = size.width
                        val height = size.height

                        rotate(rotationDegrees, width / 2, height / 2)

                        var y = -height + offsetY
                        var yIndex = 0

                        // 优化：增加边界检查，避免绘制过多不可见的文本
                        // 这里逻辑保持不变，但因为是在 Draw 阶段执行，效率更高
                        while (y < height * 2) {
                            var x = -width + offsetX
                            if (yIndex % 2 == 1) x += horizontalSpacing / 2

                            while (x < width * 2) {
                                textLines.forEachIndexed { index, line ->
                                    drawText(
                                        line,
                                        x,
                                        y + index * fontHeight,
                                        paint
                                    )
                                }
                                x += horizontalSpacing
                            }
                            y += verticalSpacing
                            yIndex++
                        }
                    }
                }
            }
        }
    ) {
        content()
    }
}