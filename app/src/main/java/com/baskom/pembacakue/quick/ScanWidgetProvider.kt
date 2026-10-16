package com.baskom.pembacakue.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.baskom.pembacakue.MainActivity
import com.baskom.pembacakue.R

/**
 * Widget layar utama "Cek Saldo" — pintasan satu ketukan ke layar tempel kartu.
 *
 * Sengaja tidak menampilkan saldo terakhir: saldo kartu hanya bisa diketahui dengan
 * menempelkan kartunya, jadi angka apa pun di widget pasti basi dan justru
 * menyesatkan di gerbang tol.
 */
class ScanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_scan).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
