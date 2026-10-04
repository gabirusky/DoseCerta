package com.dosecerta.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.dosecerta.R

/** Optional gesture; click/accessibility always reaches the same explicit confirmation dialog. */
class SwipeToConfirmView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    var onConfirmed: (() -> Unit)? = null
    private var progress = 0f
    private var dragging = false
    private var animation: ValueAnimator? = null
    private val density = resources.displayMetrics.density
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ui_on_primary_container)
        textSize = resources.getDimension(R.dimen.swipe_label_text_size)
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.ui_primary_container) }
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.ui_primary) }
    private var label: StaticLayout? = null
    init {
        isClickable = true; isFocusable = true
        contentDescription = context.getString(R.string.reminder_take_accessibility)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize((280 * density).toInt(), widthMeasureSpec)
        label = StaticLayout.Builder.obtain(context.getString(R.string.alarm_swipe_label), 0,
            context.getString(R.string.alarm_swipe_label).length, text, (width - 72 * density).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        val height = maxOf((64 * density).toInt(), (label?.height ?: 0) + (24 * density).toInt())
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 24 * density
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, track)
        label?.let { layout ->
            canvas.save(); canvas.translate((width - layout.width) / 2f, (height - layout.height) / 2f); layout.draw(canvas); canvas.restore()
        }
        val thumbRadius = minOf(24 * density, height / 2f - 4 * density)
        val x = thumbRadius + 4 * density + progress * (width - 2 * thumbRadius - 8 * density).coerceAtLeast(1f)
        canvas.drawCircle(x, height / 2f, thumbRadius, thumb)
        text.color = ContextCompat.getColor(context, R.color.ui_on_primary)
        canvas.drawText("›", x - 4 * density, height / 2f + 6 * density, text)
        text.color = ContextCompat.getColor(context, R.color.ui_on_primary_container)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { animation?.cancel(); dragging = true; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> { if (!dragging) return false; progress = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f); invalidate(); return true }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false; parent?.requestDisallowInterceptTouchEvent(false)
                if (progress >= .85f) performClick() else springBack()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; parent?.requestDisallowInterceptTouchEvent(false); reset(); return true }
        }
        return false
    }
    override fun performClick(): Boolean {
        super.performClick()
        if (isEnabled) onConfirmed?.invoke()
        reset()
        return true
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }
    private fun springBack() {
        animation = ValueAnimator.ofFloat(progress, 0f).apply { duration = 160; addUpdateListener { progress = it.animatedValue as Float; invalidate() }; start() }
    }
    fun reset() { animation?.cancel(); animation = null; dragging = false; progress = 0f; parent?.requestDisallowInterceptTouchEvent(false); invalidate() }
    override fun onDetachedFromWindow() { reset(); super.onDetachedFromWindow() }
}
