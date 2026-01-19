package fansirsqi.xposed.sesame.ui.extension

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fansirsqi.xposed.sesame.ui.compose.CommonAlertDialog
import fansirsqi.xposed.sesame.ui.theme.AppTheme
import fansirsqi.xposed.sesame.ui.theme.ThemeManager

object NativeComposeBridge {

    /**
     * 供 C++ 调用的静态入口
     */
    @JvmStatic
    fun showAlertDialog(context: Context, title: String, message: String, buttonText: String) {
        // JNI 调用可能在后台线程，必须切回主线程操作 UI
        Handler(Looper.getMainLooper()).post {
            try {
                // 1. 尝试将 Context 转为 Activity，因为我们需要往 Window 里塞 ComposeView
                val activity = context as? Activity ?: // 如果 Context 不是 Activity，无法显示 Compose Dialog
                // (除非用 Application Context + SYSTEM_ALERT_WINDOW 权限，太麻烦)
                return@post

                // 2. 找到 Activity 的根视图容器
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

                // 3. 动态创建一个 ComposeView
                val composeView = ComposeView(context)

                // 4. 设置 Compose 内容
                composeView.setContent {
                    // 这里需要套一个 Theme，否则字体颜色可能会很怪
                    // 如果您有全局 Theme，替换 MaterialTheme
                    val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
                    AppTheme(dynamicColor = isDynamicColor) {
                        var show by remember { mutableStateOf(true) }

                        if (show) {
                            CommonAlertDialog(
                                showDialog = true,
                                onDismissRequest = {
                                    show = false
                                    // 5. 关键：弹窗关闭时，把这个临时的 View 销毁掉
                                    // 延时一点点移除，避免动画截断
                                    rootView.post { rootView.removeView(composeView) }
                                },
                                onConfirm = {
                                    // 纯提示框，确认只需关闭
                                },
                                title = title,
                                text = message,
                                confirmText = buttonText,
                                showCancelButton = false
                            )
                        }
                    }
                }

                // 5. 将这个看不见的 ComposeView 添加到 Activity 中
                // Compose 的 AlertDialog 内部会创建新的 Window，所以这个 View 本身多大无所谓
                rootView.addView(composeView)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}