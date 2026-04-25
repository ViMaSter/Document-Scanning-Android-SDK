/**
Copyright 2020 ZynkSoftware SRL

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.zynksoftware.documentscanner.ui.components.polygon

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.zynksoftware.documentscanner.R
import java.util.ArrayDeque

internal class PolygonPointImageView @JvmOverloads constructor(
    context: Context,
    private val polygonView: PolygonView? = null,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val POSITION_HISTORY_WINDOW_MS = 300L
    }

    private data class TimedPosition(val timestampMs: Long, val point: PointF)

    private var downPoint = PointF()
    private var parentTopLeft = PointF()
    private val positionHistory = ArrayDeque<TimedPosition>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)

        if (polygonView != null) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val nextX = (event.rawX - parentTopLeft.x - downPoint.x)
                        .coerceIn(0f, (polygonView.width - width).toFloat())
                    val nextY = (event.rawY - parentTopLeft.y - downPoint.y)
                        .coerceIn(0f, (polygonView.height - height).toFloat())

                    x = nextX
                    y = nextY
                    recordPosition(event.eventTime)
                    dispatchCornerTouchEvent(MotionEvent.ACTION_MOVE)
                }

                MotionEvent.ACTION_DOWN -> {
                    downPoint.x = event.x
                    downPoint.y = event.y
                    val parentLocation = IntArray(2)
                    polygonView.getLocationOnScreen(parentLocation)
                    parentTopLeft = PointF(parentLocation[0].toFloat(), parentLocation[1].toFloat())
                    positionHistory.clear()
                    recordPosition(event.eventTime)
                    dispatchCornerTouchEvent(MotionEvent.ACTION_DOWN)
                }

                MotionEvent.ACTION_UP -> {
                    snapToOldestRecordedPosition()
                    performClick()
                    dispatchCornerTouchEvent(MotionEvent.ACTION_UP)
                    positionHistory.clear()
                }

                MotionEvent.ACTION_CANCEL -> {
                    dispatchCornerTouchEvent(MotionEvent.ACTION_CANCEL)
                    positionHistory.clear()
                }
            }
            polygonView.invalidate()
        }
        return true
    }

    private fun dispatchCornerTouchEvent(action: Int) {
        val polygon = polygonView ?: return
        val location = IntArray(2)
        getLocationOnScreen(location)
        val centerRawX = location[0] + width / 2f
        val centerRawY = location[1] + height / 2f
        polygon.dispatchCornerTouchEvent(action, centerRawX, centerRawY)
    }

    private fun recordPosition(timestampMs: Long) {
        val cutoff = timestampMs - POSITION_HISTORY_WINDOW_MS
        while (positionHistory.isNotEmpty() && positionHistory.first.timestampMs < cutoff) {
            positionHistory.removeFirst()
        }
        positionHistory.addLast(TimedPosition(timestampMs, PointF(x, y)))
    }

    private fun snapToOldestRecordedPosition() {
        val oldestPosition = positionHistory.firstOrNull() ?: return
        x = oldestPosition.point.x
        y = oldestPosition.point.y
    }

    // Because we call this from onTouchEvent, this code will be executed for both
    // normal touch events and for when the system calls this using Accessibility
    override fun performClick(): Boolean {
        super.performClick()

        val color = if (polygonView?.isValidShape(polygonView.getPoints()) == true) {
            ContextCompat.getColor(context, android.R.color.white)
        } else {
            ContextCompat.getColor(context, R.color.zdc_red)
        }
        polygonView?.paint?.color = color

        return true
    }

}