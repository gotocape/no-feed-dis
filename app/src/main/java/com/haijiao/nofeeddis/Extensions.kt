package com.haijiao.nofeeddis

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Context.toast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.toast(@StringRes resId: Int) {
    toast(getString(resId))
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/**
 * 兼容 Android 15+ 的强制 edge-to-edge（targetSdk 35 起状态栏颜色设置失效，
 * 内容会画到状态栏下面）。
 *
 * 同时把 ime（软键盘）insets 也算进底部 padding，避免键盘弹起时盖住搜索按钮。
 */
fun View.applySystemBarPadding(horizontalDp: Int, verticalDp: Int) {
    val baseH = context.dp(horizontalDp)
    val baseV = context.dp(verticalDp)

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        )
        view.setPadding(
            baseH + bars.left,
            baseV + bars.top,
            baseH + bars.right,
            baseV + bars.bottom
        )
        insets
    }
}
