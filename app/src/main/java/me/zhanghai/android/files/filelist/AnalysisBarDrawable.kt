package me.zhanghai.android.files.filelist

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

class AnalysisBarDrawable(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val width = (bounds.width() * fraction).toInt()
        if (width > 0) {
            paint.color = color
            paint.alpha = 48
            canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(),
                (bounds.left + width).toFloat(), bounds.bottom.toFloat(), paint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun onBoundsChange(bounds: Rect) = invalidateSelf()
}
