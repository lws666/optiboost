package com.lwsnb.optiboost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lwsnb.optiboost.engine.ShellEngine
import com.lwsnb.optiboost.engine.SysReader
import com.lwsnb.optiboost.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiState.refreshEngine()
        UiState.refreshDashboard()
        setContent { OptiBoostUI() }
    }
}

// ===================== 配色 =====================

val BgDeep = Color(0xFF070A16)
val CardDeep = Color(0xFF141A33)
val AccentCyan = Color(0xFF00E5FF)
val AccentGreen = Color(0xFF00E676)
val AccentRed = Color(0xFFFF5252)
val TextDim = Color(0xFF8A93B5)

@Composable
fun OptiBoostUI() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentCyan, secondary = AccentCyan,
            background = BgDeep, surface = CardDeep,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        LaunchedEffect(Unit) {
            while (true) {
                withContext(Dispatchers.IO) {
                    UiState.refreshCpu()
                    UiState.refreshDashboard()
                }
                delay(3000)
            }
        }
        Surface(Modifier.fillMaxSize(), color = BgDeep) {
            MainScreen()
        }
    }
}

// ===================== 主界面：只有开 / 关 =====================

@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    val on = UiState.enabled

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Spacer(Modifier.height(28.dp))
        Text("极启", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            "${SysReader.deviceModel()} · ${UiState.osLabel.ifEmpty { SysReader.androidVersion() }}",
            fontSize = 12.sp, color = TextDim
        )

        // 状态指示
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            (if (on) AccentGreen else AccentRed).copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(
                        (if (on) AccentGreen else AccentRed).copy(alpha = 0.15f),
                        RoundedCornerShape(50)
                    )
                    .border(
                        2.dp,
                        (if (on) AccentGreen else AccentRed).copy(alpha = 0.6f),
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(if (on) AccentGreen else AccentRed, RoundedCornerShape(50))
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                UiState.busy -> UiState.currentStep.ifEmpty { "处理中…" }
                on -> "已开启"
                else -> "已关闭"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                UiState.busy -> AccentCyan
                on -> AccentGreen
                else -> TextDim
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (on) "加速与清理已生效" else "系统保持原始状态",
            fontSize = 11.sp, color = TextDim
        )

        Spacer(Modifier.weight(1f))

        // 两个按钮
        ActionButton(
            text = "开 启",
            sub = "加速 · 清理 · 放行温控",
            color = AccentGreen,
            filled = !on,
            enabled = !UiState.busy
        ) {
            if (!on) UiState.toggle(scope)
        }
        Spacer(Modifier.height(14.dp))
        ActionButton(
            text = "关 闭",
            sub = "完整回滚到原始状态",
            color = AccentRed,
            filled = on,
            enabled = !UiState.busy
        ) {
            if (on) UiState.toggle(scope)
        }

        Spacer(Modifier.height(24.dp))

        // 引擎提示
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (label, color) = when (UiState.engineMode) {
                ShellEngine.Mode.SHIZUKU -> "Shizuku 免 Root" to AccentGreen
                ShellEngine.Mode.ROOT -> "Root" to Color(0xFFFFC400)
                ShellEngine.Mode.NONE -> "未授权引擎" to AccentRed
            }
            Box(Modifier.size(6.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = color)
        }

        if (UiState.engineMode == ShellEngine.Mode.NONE) {
            Spacer(Modifier.height(10.dp))
            Text(
                "点击授权 Shizuku",
                fontSize = 12.sp,
                color = AccentCyan,
                modifier = Modifier.clickable {
                    ShellEngine.requestShizukuPermission(1001)
                }
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun ActionButton(
    text: String,
    sub: String,
    color: Color,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val active = enabled && filled
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (filled) color.copy(alpha = 0.16f) else CardDeep
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .border(
                1.dp,
                if (filled) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = active, onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (filled) color else TextDim
            )
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = TextDim)
        }
    }
}
