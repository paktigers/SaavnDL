package com.saavndl.app.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.saavndl.app.MainActivity
import com.saavndl.app.R

/** Home-screen widget: current song with previous / play-pause / next. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        push(context)
    }

    companion object {
        private fun pi(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(
            ctx, 100 + code, Intent(ctx, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun push(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val t = PlayerController.current.value
            val playing = PlayerController.playing.value
            val v = RemoteViews(ctx.packageName, R.layout.widget_player)
            v.setTextViewText(R.id.w_title, t?.title ?: "Nothing playing")
            v.setTextViewText(R.id.w_artist, t?.artist ?: "Open SaavnDL to pick a song")
            v.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            v.setOnClickPendingIntent(R.id.w_prev, pi(ctx, PlaybackService.ACT_PREV, 1))
            v.setOnClickPendingIntent(R.id.w_play, pi(ctx, PlaybackService.ACT_TOGGLE, 2))
            v.setOnClickPendingIntent(R.id.w_next, pi(ctx, PlaybackService.ACT_NEXT, 3))
            v.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            mgr.updateAppWidget(ids, v)
        }
    }
}
