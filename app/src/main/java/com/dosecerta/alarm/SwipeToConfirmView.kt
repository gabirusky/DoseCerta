package com.dosecerta.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.dosecerta.R

/**
 * C5: Custom View implementing a horizontal swipe-to-confirm interaction for medication take action.
 *
 * Layout: [ ←←← thumb ○ ---|--- track "Deslize para tomar" ---→→→ ]
 *
 * Usage:
 *   swipeView.onConfirmed = { handleTakeAction() }
 */
class SwipeToConfirmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onConfirmed: (() -> Unit)? = null

    // State
    private var thumbX = 0f          // current center-X of thumb
    private var trackWidth = 0f
    private var thumbRadius = 0f
    private var progress = 0f        // 0f..1f
    private var isDragging = false
    private var isConfirmed = false
    private var springAnimator: ValueAnimator? = null

    // Colors
    private val trackBgColor = ContextCompat.getColor(context, R.color.swipe_track_bg)
    private val trackSuccessColor = ContextCompat.getColor(context, R.color.swipe_track_success)
    private val thumbColor = ContextCompat.getColor(context, R.color.mint_leaf)
    private val labelColor = ContextCompat.getColor(context, R.color.gray_700)
    private val labelDoneColor = ContextCompat.getColor(context, R.color.white)

    // Paints
    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackBgColor
        style = Paint.Style.FILL
    }
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackSuccessColor
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = thumbColor
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textAlign = Paint.Align.CENTER
        textSize = resources.getDimension(R.dimen.swipe_label_text_size)
    }

    // Drawables
    private val arrowBitmap = ContextCompat.getDrawable(context, R.drawable.ic_arrow_forward)
        ?.toBitmap(40, 40)
    private val checkBitmap = ContextCompat.getDrawable(context, R.drawable.ic_check)
        ?.toBitmap(40, 40)

    // Dimensions
    private val cornerRadius = resources.getDimensionPixelSize(R.dimen.swipe_track_corner_radius).toFloat()
    private val thumbHitExpansion = 1.5f  // expand thumb hit area by 1.5x for usability

    // Strings
    private val labelDefault = context.getString(R.string.alarm_swipe_label)
    private val labelDone = context.getString(R.string.alarm_swipe_done)

    // Confirm threshold — 85% of track width
    private val confirmThreshold = 0.85f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        thumbRadius = (h / 2f) - 6f
        trackWidth = w.toFloat()
        thumbX = thumbRadius + paddingStart
        labelPaint.textSize = h * 0.30f  // scale label to view height
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()

        // Draw track background
        val trackRect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackBgPaint)

        // Draw progress fill (clips to track RectF)
        if (progress > 0f || isConfirmed) {
            val fillRight = if (isConfirmed) w else thumbX + thumbRadius
            trackFillPaint.alpha = if (isConfirmed) 255 else (progress * 200 + 55).toInt()
            val fillRect = RectF(0f, 0f, fillRight.coerceAtMost(w), h)
            canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, trackFillPaint)
        }

        if (isConfirmed) {
            // Draw "Tomei!" centered with white text
            labelPaint.color = labelDoneColor
            labelPaint.alpha = 255
            canvas.drawText(labelDone, w / 2f, h / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
            // Draw checkmark centered
            checkBitmap?.let {
                canvas.drawBitmap(it, w / 2f - it.width - 8f, h / 2f - it.height / 2f, null)
            }
        } else {
            // Draw label with alpha that fades as thumb moves right
            val labelAlpha = ((1f - progress * 1.8f).coerceIn(0f, 1f) * 255).toInt()
            labelPaint.color = labelColor
            labelPaint.alpha = labelAlpha
            if (labelAlpha > 0) {
                canvas.drawText(labelDefault, w / 2f, h / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
            }

            // Draw thumb circle
            thumbPaint.color = thumbColor
            canvas.drawCircle(thumbX, h / 2f, thumbRadius, thumbPaint)

            // Draw arrow icon centered on thumb
            arrowBitmap?.let {
                val bx = thumbX - it.width / 2f
                val by = h / 2f - it.height / 2f
                // Tint white via color filter
                val iconPaint = Paint().apply { colorFilter = android.graphics.PorterDuffColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN) }
                canvas.drawBitmap(it, bx, by, iconPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isConfirmed) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Accept touch if within thumb hit area (expanded for usability)
                val hitRadius = thumbRadius * thumbHitExpansion
                if (Math.abs(event.x - thumbX) <= hitRadius && Math.abs(event.y - height / 2f) <= hitRadius) {
                    springAnimator?.cancel()
                    isDragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val minX = thumbRadius + paddingStart
                val maxX = trackWidth - thumbRadius - paddingEnd
                thumbX = event.x.coerceIn(minX, maxX)
                progress = ((thumbX - minX) / (maxX - minX)).coerceIn(0f, 1f)
                invalidate()
                if (progress >= confirmThreshold) {
                    triggerConfirm()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                if (!isConfirmed) {
                    springBack()
                }
                return true
            }
        }
        return false
    }

    private fun triggerConfirm() {
        if (isConfirmed) return
        isConfirmed = true
        isDragging = false

        // Snap thumb to end
        thumbX = trackWidth - thumbRadius
        progress = 1f
        invalidate()

        // Haptic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        // Delay 350ms so user sees the success state before dismiss
        postDelayed({ onConfirmed?.invoke() }, 350)
    }

    private fun springBack() {
        val startX = thumbX
        val endX = thumbRadius + paddingStart
        springAnimator = ValueAnimator.ofFloat(startX, endX).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                thumbX = anim.animatedValue as Float
                progress = ((thumbX - endX) / (trackWidth - endX * 2)).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
    }

    /** Reset to initial state (called if activity needs to be reused). */
    fun reset() {
        isConfirmed = false
        isDragging = false
        progress = 0f
        springAnimator?.cancel()
        thumbX = thumbRadius + paddingStart
        invalidate()
    }
}
