package com.example.testresqmesh.feature.sos.ui.map

import android.content.Context
import androidx.compose.animation.core.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.maplibre.android.maps.Style

internal fun createMyLocationMarkerBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (36 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer soft pulsing halo (Safety Orange, 25% alpha)
    paint.color = AndroidColor.parseColor("#40FF5A00")
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, center - (2 * density), paint)

    // Crisp white outer ring
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, 12 * density, paint)

    // Safety Orange core disc
    paint.color = AndroidColor.parseColor("#FF5A00")
    canvas.drawCircle(center, center, 9 * density, paint)

    // Pinpoint white center dot
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, 3.5f * density, paint)

    return bitmap
}

internal fun createSosMarkerBitmap(context: Context, label: String = "SOS"): Bitmap {
    val density = context.resources.displayMetrics.density
    val widthPx = (44 * density).toInt()
    val heightPx = (52 * density).toInt()
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val centerX = widthPx / 2f
    val pinRadius = 18 * density
    val centerY = pinRadius + (2 * density)

    // Outer Warning Halo (Red, 30% alpha)
    paint.color = AndroidColor.parseColor("#4DFF1744")
    paint.style = Paint.Style.FILL
    canvas.drawCircle(centerX, centerY, pinRadius + (3 * density), paint)

    // Teardrop pin pointer tip path
    val path = android.graphics.Path().apply {
        moveTo(centerX, heightPx - (2 * density))
        lineTo(centerX - (11 * density), centerY + (6 * density))
        quadTo(centerX, heightPx.toFloat(), centerX + (11 * density), centerY + (6 * density))
        close()
    }
    // White outer border
    paint.color = AndroidColor.WHITE
    canvas.drawPath(path, paint)
    canvas.drawCircle(centerX, centerY, pinRadius, paint)

    // Deep Signal Red inner pin body
    paint.color = AndroidColor.parseColor("#D50000")
    canvas.drawCircle(centerX, centerY, pinRadius - (2.5f * density), paint)

    val innerPath = android.graphics.Path().apply {
        moveTo(centerX, heightPx - (4 * density))
        lineTo(centerX - (9 * density), centerY + (5 * density))
        quadTo(centerX, heightPx - (2 * density), centerX + (9 * density), centerY + (5 * density))
        close()
    }
    canvas.drawPath(innerPath, paint)

    // Bold white "SOS" text
    paint.color = AndroidColor.WHITE
    paint.textSize = 10 * density
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val textY = centerY - ((paint.descent() + paint.ascent()) / 2)
    canvas.drawText(label, centerX, textY, paint)

    return bitmap
}
