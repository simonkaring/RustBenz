package app.rustbenz.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Thin line icons (24 grid, 1.6 stroke) drawn to match the luxury type; tint via Icon(). */
object LuxIcons {
    val Home = icon("home") {
        moveTo(2.5f, 15.5f); verticalLineTo(12.9f)
        curveTo(2.5f, 12.1f, 3f, 11.5f, 3.8f, 11.3f)
        lineTo(6.6f, 10.6f); lineTo(9.1f, 7.7f)
        curveTo(9.5f, 7.3f, 10f, 7f, 10.6f, 7f)
        horizontalLineTo(14.9f)
        curveTo(15.6f, 7f, 16.2f, 7.3f, 16.6f, 7.8f)
        lineTo(19.1f, 10.6f); lineTo(20.5f, 11f)
        curveTo(21.1f, 11.2f, 21.5f, 11.7f, 21.5f, 12.4f)
        verticalLineTo(15.5f); horizontalLineTo(19.6f)
        moveTo(14.4f, 15.5f); horizontalLineTo(9.6f)
        moveTo(4.4f, 15.5f); horizontalLineTo(2.5f)
        circle(7f, 15.5f, 2.3f); circle(17f, 15.5f, 2.3f)
    }
    val Climate = icon("climate") {
        moveTo(3f, 9f); horizontalLineTo(14f)
        arcToRelative(2.5f, 2.5f, 0f, true, false, -2.5f, -2.5f)
        moveTo(3f, 13f); horizontalLineTo(18f)
        arcToRelative(2.5f, 2.5f, 0f, true, true, -2.5f, 2.5f)
        moveTo(3f, 17f); horizontalLineTo(9f)
    }
    val Charging = icon("charging") {
        moveTo(13f, 3f); lineTo(5.5f, 13.5f); horizontalLineTo(11.5f)
        lineTo(10.5f, 21f); lineTo(18.5f, 10f); horizontalLineTo(12.5f); close()
    }
    val Trips = icon("trips") {
        circle(6f, 18f, 2f); circle(18f, 6f, 2f)
        moveTo(6f, 16f); verticalLineTo(13f)
        curveTo(6f, 11.3f, 7.3f, 10f, 9f, 10f); horizontalLineTo(15f)
        curveTo(16.7f, 10f, 18f, 9.2f, 18f, 8f)
    }
    val Vehicle = icon("vehicle") {
        moveTo(4.2f, 17f)
        arcTo(8.2f, 8.2f, 0f, true, true, 19.8f, 17f)
        moveTo(12f, 13f); lineTo(15.6f, 9.4f)
        circle(12f, 13f, 1.3f)
    }
    val Lock = icon("lock") {
        moveTo(6f, 11f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(6f); close()
        moveTo(8.5f, 11f); verticalLineTo(8f)
        arcToRelative(3.5f, 3.5f, 0f, false, true, 7f, 0f); verticalLineTo(11f)
    }
    val Unlock = icon("unlock") {
        moveTo(6f, 11f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(6f); close()
        moveTo(8.5f, 11f); verticalLineTo(8f)
        arcToRelative(3.5f, 3.5f, 0f, false, true, 6.9f, -0.8f)
    }
    val Locate = icon("locate") {
        moveTo(12f, 21f)
        curveTo(12f, 21f, 5f, 14.6f, 5f, 9.6f)
        arcToRelative(7f, 7f, 0f, false, true, 14f, 0f)
        curveTo(19f, 14.6f, 12f, 21f, 12f, 21f); close()
        circle(12f, 9.6f, 2.4f)
    }
    val Power = icon("power") {
        moveTo(12f, 3.5f); verticalLineTo(11f)
        moveTo(7.2f, 6.6f)
        arcTo(7.4f, 7.4f, 0f, true, false, 16.8f, 6.6f)
    }
    val Plug = icon("plug") {
        moveTo(9f, 3f); verticalLineTo(7f); moveTo(15f, 3f); verticalLineTo(7f)
        moveTo(7f, 7f); horizontalLineTo(17f); verticalLineTo(11f)
        arcToRelative(5f, 5f, 0f, false, true, -10f, 0f); close()
        moveTo(12f, 16f); verticalLineTo(21f)
    }
    val Seat = icon("seat") {
        moveTo(7f, 4f)
        curveTo(8.1f, 4f, 8.9f, 4.8f, 9.1f, 5.8f)
        lineTo(10.4f, 13.5f); horizontalLineTo(16.6f)
        curveTo(17.7f, 13.5f, 18.5f, 14.3f, 18.5f, 15.4f); verticalLineTo(16.6f)
        horizontalLineTo(9.6f)
        curveTo(8.5f, 16.6f, 7.6f, 15.8f, 7.4f, 14.8f)
        lineTo(5.6f, 6f)
        curveTo(5.4f, 4.9f, 6f, 4f, 7f, 4f); close()
        moveTo(10.5f, 16.6f); verticalLineTo(20f); moveTo(16f, 16.6f); verticalLineTo(20f)
    }
    val Window = icon("window") {
        moveTo(4f, 18f); verticalLineTo(10.5f); lineTo(9.5f, 5f); horizontalLineTo(20f)
        verticalLineTo(18f); close()
        moveTo(12f, 9f); verticalLineTo(14f); moveTo(10f, 12f); lineTo(12f, 14f); lineTo(14f, 12f)
    }
    val Signal = icon("signal") {
        moveTo(4.5f, 10f); verticalLineTo(14f); horizontalLineTo(7.5f)
        lineTo(11.5f, 17.5f); verticalLineTo(6.5f); lineTo(7.5f, 10f); close()
        moveTo(14.8f, 9.6f); arcToRelative(3.4f, 3.4f, 0f, false, true, 0f, 4.8f)
        moveTo(17.3f, 7.1f); arcToRelative(7f, 7f, 0f, false, true, 0f, 9.8f)
    }
    val Trunk = icon("trunk") {
        moveTo(3f, 16f); verticalLineTo(12f); lineTo(6f, 8f); horizontalLineTo(13f)
        lineTo(18f, 11f); lineTo(21f, 11.5f); verticalLineTo(16f); close()
        moveTo(13f, 8f); lineTo(19.5f, 4.5f)
    }
    val Battery = icon("battery") {
        moveTo(3f, 8f); horizontalLineTo(18f); verticalLineTo(16f); horizontalLineTo(3f); close()
        moveTo(20.5f, 10.5f); verticalLineTo(13.5f)
        moveTo(6f, 11f); verticalLineTo(13f); moveTo(9f, 11f); verticalLineTo(13f)
    }
    val Tire = icon("tire") {
        circle(12f, 12f, 8.5f); circle(12f, 12f, 3.5f)
        moveTo(12f, 3.5f); verticalLineTo(8.5f); moveTo(12f, 15.5f); verticalLineTo(20.5f)
        moveTo(3.5f, 12f); horizontalLineTo(8.5f); moveTo(15.5f, 12f); horizontalLineTo(20.5f)
    }
    val Clock = icon("clock") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7.5f); verticalLineTo(12f); lineTo(15f, 14f)
    }
    val Sliders = icon("sliders") {
        moveTo(4f, 7f); horizontalLineTo(13f); moveTo(19f, 7f); horizontalLineTo(20f); circle(16f, 7f, 2.2f)
        moveTo(4f, 17f); horizontalLineTo(5f); moveTo(11f, 17f); horizontalLineTo(20f); circle(8f, 17f, 2.2f)
    }
    val Chevron = icon("chevron") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) }
    val Back = icon("back") { moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f) }
    val Plus = icon("plus") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
    val Minus = icon("minus") { moveTo(5f, 12f); horizontalLineTo(19f) }
    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    val Info = icon("info") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 11f); verticalLineTo(16.5f); moveTo(12f, 7.6f); verticalLineTo(7.8f)
    }
    val Refresh = icon("refresh") {
        moveTo(19f, 12f)
        arcTo(7f, 7f, 0f, true, true, 16.9f, 7f)
        moveTo(17f, 3.5f); verticalLineTo(7.2f); horizontalLineTo(13.3f)
    }
    val Leaf = icon("leaf") {
        moveTo(5f, 19f)
        curveTo(5f, 10f, 10f, 5f, 19f, 5f)
        curveTo(19f, 14f, 14f, 19f, 5f, 19f); close()
        moveTo(5f, 19f); lineTo(13f, 11f)
    }
}

private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
        .build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
}
