package me.zssu.ime.keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Shows its single child at [scale] of its normal size and takes only that much room.
 *
 * The child is laid out as if the screen were normal size, then drawn scaled from the top-left
 * corner. Touches follow the child's transform, so every key and button still works. Unlike a plain
 * scaleX/scaleY, this box measures itself at the scaled size, so the input window gets shorter and
 * the app above gets the room back. At scale 1 it behaves exactly like a FrameLayout.
 */
class ScaleBox(context: Context) : FrameLayout(context) {
    /** Width the child is laid out at while scaled; the unscaled width of the whole keyboard. */
    var logicalWidth: Int = 0
        set(value) { if (field != value) { field = value; requestLayout() } }

    var scale: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            val child = getChildAt(0)
            if (child != null && value >= 1f) {
                child.scaleX = 1f
                child.scaleY = 1f
            }
            requestLayout()
            invalidate()
        }

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        if (scale >= 1f || child == null || logicalWidth <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        child.measure(
            MeasureSpec.makeMeasureSpec(logicalWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        setMeasuredDimension(
            (logicalWidth * scale).toInt(),
            (child.measuredHeight * scale).toInt(),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0)
        if (scale >= 1f || child == null || logicalWidth <= 0) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams
}
