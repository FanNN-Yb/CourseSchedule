package com.example.schedule

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import io.github.fannn_yb.rubberband.overscroll.rememberOffsetOverscrollFactory

/**
 * startActivity 启动走系统转场动画，返回 finish() 走系统返回动画。
 * Store 为进程内共享单例，Activity 与主界面天然状态同步。
 *
 * 页面 Composable 本身不再包 AppTheme —— 在这里按当前主题包好
 * （主题页内切换主题色/明暗时，Store 状态驱动两个窗口同时重组）。
 */
@Composable
private fun SubPageTheme(content: @Composable () -> Unit) {
    val store = Store.shared(androidx.compose.ui.platform.LocalContext.current)
    val dark = store.isDarkTheme(isSystemInDarkTheme())
    // 否则「应用内深色 + 系统浅色」时子页面状态栏图标不可见
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val activity = view.context as? android.app.Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    AppTheme(dark = dark, accentColor = store.accentColor()) {
        val overscrollFactory = rememberOffsetOverscrollFactory(Orientation.Vertical)
        CompositionLocalProvider(LocalOverscrollFactory provides overscrollFactory) {
            content()
        }
    }
}

private fun ComponentActivity.setSubPageContent(content: @Composable () -> Unit) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // 状态栏全透明（enableEdgeToEdge 默认带半透明黑纱罩，页面顶部会发灰）
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
    )
    setContent {
        SubPageTheme(content = content)
    }
}

/** 主题（浅色 / 深色 / 自动 / 自定义主题色）。 */
class ThemeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSubPageContent {
            ThemeScreen(store = Store.shared(applicationContext), onBack = { finish() })
        }
    }
}

/** 课表时间设置。 */
class PeriodTimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSubPageContent {
            PeriodTimeScreen(store = Store.shared(applicationContext), onBack = { finish() })
        }
    }
}

/** 多课表管理。 */
class ThanksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSubPageContent {
            ThanksScreen(onBack = { finish() })
        }
    }
}

class TablesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSubPageContent {
            TablesScreen(store = Store.shared(applicationContext), onBack = { finish() })
        }
    }
}
