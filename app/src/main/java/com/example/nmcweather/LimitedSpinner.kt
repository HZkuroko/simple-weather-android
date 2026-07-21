package com.example.nmcweather

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner

/**
 * 下拉弹窗最多约显示 5 行，其余内容在弹窗内滚动。
 * AppCompat 未公开 popup 高度，反射失败时自动退回系统默认，不影响选择功能。
 */
class LimitedSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.spinnerStyle
) : AppCompatSpinner(context, attrs, defStyleAttr, android.widget.Spinner.MODE_DROPDOWN) {

    override fun performClick(): Boolean {
        limitPopupHeight()
        return super.performClick()
    }

    private fun limitPopupHeight() {
        runCatching {
            val field = AppCompatSpinner::class.java.getDeclaredField("mPopup").apply {
                isAccessible = true
            }
            val popup = field.get(this) ?: return
            val height = (48 * 5 * resources.displayMetrics.density).toInt()
            popup.javaClass.getMethod("setHeight", Int::class.javaPrimitiveType)
                .invoke(popup, height)
        }
    }
}
