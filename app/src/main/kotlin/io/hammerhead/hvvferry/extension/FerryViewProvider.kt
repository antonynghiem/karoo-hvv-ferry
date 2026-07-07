package io.hammerhead.hvvferry.extension

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.qualifiers.ApplicationContext
import io.hammerhead.hvvferry.R
import io.hammerhead.hvvferry.data.models.Departure
import io.hammerhead.hvvferry.data.models.FerryConfig
import io.hammerhead.hvvferry.utils.DisplayFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FerryViewProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayFormatter: DisplayFormatter
) {
    
    /**
     * Create RemoteViews for ferry data field
     */
    fun createFerryDataFieldView(
        departures: List<Departure>,
        distance: Int?,
        config: FerryConfig
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.ferry_data_field)
        
        // Filter out cancelled departures
        val validDepartures = departures.filter { !it.cancelled }
        
        if (validDepartures.isEmpty()) {
            // No ferries available
            views.setTextViewText(
                R.id.ferry_departure_1,
                context.getString(R.string.no_ferries_nearby)
            )
            views.setTextColor(R.id.ferry_departure_1, Color.parseColor("#808080"))
            views.setViewVisibility(R.id.ferry_departure_2, View.GONE)
            return views
        }
        
        // First non-cancelled departure
        val firstText = displayFormatter.formatDeparture(
            validDepartures[0],
            distance,
            config.routeFormat
        )
        views.setTextViewText(R.id.ferry_departure_1, firstText)
        
        // Color based on delay
        val firstColor = if (validDepartures[0].hasDelay()) {
            Color.parseColor("#FF9500") // Orange
        } else {
            Color.parseColor("#FFFFFF") // White
        }
        views.setTextColor(R.id.ferry_departure_1, firstColor)
        
        // Second departure (if enabled and available)
        if (config.showTwoDepartures && validDepartures.size > 1) {
            val secondText = displayFormatter.formatDeparture(
                validDepartures[1],
                null, // No distance on second line
                config.routeFormat
            )
            views.setTextViewText(R.id.ferry_departure_2, secondText)
            views.setViewVisibility(R.id.ferry_departure_2, View.VISIBLE)
            
            val secondColor = if (validDepartures[1].hasDelay()) {
                Color.parseColor("#FF9500")
            } else {
                Color.parseColor("#CCCCCC")
            }
            views.setTextColor(R.id.ferry_departure_2, secondColor)
        } else {
            views.setViewVisibility(R.id.ferry_departure_2, View.GONE)
        }
        
        return views
    }
}
