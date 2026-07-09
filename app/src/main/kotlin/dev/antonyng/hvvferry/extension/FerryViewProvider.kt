package dev.antonyng.hvvferry.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import dev.antonyng.hvvferry.ui.FerryTimesActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonyng.hvvferry.R
import dev.antonyng.hvvferry.data.models.Departure
import dev.antonyng.hvvferry.data.models.FerryConfig
import dev.antonyng.hvvferry.data.models.RouteFormat
import dev.antonyng.hvvferry.utils.DisplayFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FerryViewProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayFormatter: DisplayFormatter
) {

    private fun isDarkMode(ctx: Context): Boolean {
        val nightMode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun createFerryDataFieldView(
        departures: List<Departure>,
        distance: Int?,
        config: FerryConfig,
        displayContext: Context = context,
        textSize: Int = 18
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.ferry_data_field)
        val darkMode = isDarkMode(displayContext)

        val normalColor = if (darkMode) Color.WHITE else Color.BLACK
        val secondaryColor = if (darkMode) Color.parseColor("#CCCCCC") else Color.parseColor("#555555")
        val noDataColor = Color.parseColor("#808080")
        val delayColor = Color.parseColor("#FF9500")

        val validDepartures = departures.filter { !it.cancelled }

        if (validDepartures.isEmpty()) {
            views.setTextViewText(R.id.ferry_destination, "No departures found")
            views.setTextColor(R.id.ferry_destination, noDataColor)
            views.setTextViewText(R.id.ferry_origin, "")
            views.setTextViewText(R.id.ferry_time, "")
            return views
        }

        val first = validDepartures[0]
        val color = if (first.hasDelay()) delayColor else normalColor

        // Line + destination
        val destText = when (config.routeFormat) {
            RouteFormat.ABBREVIATED -> "${first.line.name} → ${displayFormatter.abbreviate(first.line.direction)}"
            RouteFormat.DIRECTION_ONLY -> "${first.line.name} → ${first.line.direction}"
        }
        views.setTextViewText(R.id.ferry_destination, destText)
        views.setTextColor(R.id.ferry_destination, color)

        // Departure pier
        val pierText = when (config.routeFormat) {
            RouteFormat.ABBREVIATED -> displayFormatter.abbreviate(first.line.origin)
            RouteFormat.DIRECTION_ONLY -> first.line.origin
        }
        views.setTextViewText(R.id.ferry_origin, pierText)
        views.setTextColor(R.id.ferry_origin, secondaryColor)

        // Current time + next departure of same line
        val nextSameLine = validDepartures.drop(1).firstOrNull {
            it.line.name == first.line.name && it.line.direction == first.line.direction
        }
        val timeText = if (nextSameLine != null) {
            "${displayFormatter.formatTime(first)}  ·  ${displayFormatter.formatTime(nextSameLine)}"
        } else {
            displayFormatter.formatTime(first)
        }
        views.setTextViewText(R.id.ferry_time, timeText)
        views.setTextColor(R.id.ferry_time, color)

        // Tap the data field to open the full departure list
        val intent = Intent(context, FerryTimesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.ferry_destination, pendingIntent)
        views.setOnClickPendingIntent(R.id.ferry_time, pendingIntent)

        return views
    }
}
