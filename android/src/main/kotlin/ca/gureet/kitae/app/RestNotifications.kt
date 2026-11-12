// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ca.gureet.kitae.R
import ca.gureet.kitae.model.RestTimer
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.workout.clockText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The rest timer outside Kamae, doing the iPhone's Live Activity's job: while a rest goes, an
 * ongoing notification counts down with the day, the sets done and the next set, with Pause or
 * Resume, +15s and Skip; when it runs out, "Rest over" while Settings › Rest timer alerts is on.
 * What it shows is kept with the rest, so its buttons work while Kamae is closed.
 */
class RestNotifications(private val context: Context, private val store: RestStore) {
    private val manager = NotificationManagerCompat.from(context)

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Each time the notification's buttons or the end of a rest change the rest. */
    val changed: SharedFlow<Unit> = changes

    /** Whether Kamae is on screen, where the workout taps the phone itself when a rest ends. */
    @Volatile
    var isVisible = false

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REST, context.getString(R.string.channel_rest), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_rest_text)
                setShowBadge(false)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REST_OVER, context.getString(R.string.channel_rest_over), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_rest_over_text)
            },
        )
    }

    private val canNotify: Boolean
        get() = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Keeps what the notification says beside the time: the sets done and the next set. */
    fun describe(workout: WorkoutState, formatter: WeightFormatter, alerts: Boolean) {
        val progress = workout.progress
        val resources = context.resources
        val sets = resources.getQuantityString(R.plurals.sets_fraction, progress.setCount, progress.doneCount, progress.setCount)
        val next = progress.nextSet
        val detail = if (next == null) {
            sets
        } else {
            val exercise = next.exercise
            val reps = next.set.reps
            val kilograms = exercise.weight(next.set.weightKg)
            val amount = if (exercise.isIsometric) "${reps}s" else "$reps"
            val set = when {
                kilograms > 0 -> "$amount × ${formatter.string(kilograms)} ${formatter.unit.symbol}"
                exercise.isIsometric -> amount
                else -> resources.getQuantityString(R.plurals.reps, reps, reps)
            }
            context.getString(R.string.rest_detail_next, sets, exercise.name, set)
        }
        store.describe(workout.dayName ?: context.getString(R.string.workout), detail, alerts)
    }

    /** Shows the rest kept in the store, or takes the notification away when there's none. */
    fun refresh(now: Long = System.currentTimeMillis()) {
        val kept = store.current(now)
        val rest = kept?.rest
        if (kept == null || rest == null || !rest.isActive || (rest.isRunning && rest.remaining(now) == 0L)) {
            manager.cancel(ID_REST)
            cancelEnd()
            return
        }
        if (rest.isRunning) scheduleEnd(rest.endsAt ?: now) else cancelEnd()
        if (!canNotify) return
        val paused = rest.isPaused
        val left = rest.remaining(now)
        val builder = NotificationCompat.Builder(context, CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_today)
            .setColor(ContextCompat.getColor(context, R.color.icon_background))
            .setContentTitle(context.getString(if (paused) R.string.rest_paused_title else R.string.resting))
            .setContentText(if (paused) context.getString(R.string.rest_paused_text, clockText(left, countdown = true), kept.detail) else kept.detail)
            .setSubText(kept.dayName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .setProgress(1000, if (rest.total > 0) (1000 - left * 1000 / rest.total).toInt().coerceIn(0, 1000) else 0, false)
            .setRequestPromotedOngoing(true)
        if (paused) {
            // The status bar shows this while paused; while counting, it shows the chronometer.
            builder.setShowWhen(false).setShortCriticalText(clockText(left, countdown = true))
        } else {
            builder.setWhen(rest.endsAt ?: now).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
        builder
            .addAction(0, context.getString(if (paused) R.string.resume else R.string.pause), action(if (paused) ACTION_RESUME else ACTION_PAUSE))
            .addAction(0, context.getString(R.string.add_15_seconds), action(ACTION_ADD))
            .addAction(0, context.getString(R.string.skip), action(ACTION_SKIP))
        @Suppress("MissingPermission")
        manager.notify(ID_REST, builder.build())
    }

    /** A button's command, from the notification. */
    fun handle(command: String, now: Long = System.currentTimeMillis()) {
        val kept = store.current(now) ?: return
        val rest = kept.rest
        val changed = when (command) {
            ACTION_PAUSE -> rest.paused(now)
            ACTION_RESUME -> rest.resumed(now)
            ACTION_ADD -> rest.adding(15, now)
            ACTION_SKIP -> RestTimer()
            ACTION_END -> if (rest.isRunning && rest.remaining(now) <= 1000) RestTimer() else return refresh(now)
            else -> return
        }
        store.save(kept.workoutId, changed)
        refresh(now)
        if (command == ACTION_END) restOver(kept)
        changes.tryEmit(Unit)
    }

    /** "Rest over", when a rest runs out while Kamae isn't on screen and alerts are on. */
    private fun restOver(kept: RestStore.Kept) {
        if (!kept.alerts || isVisible || !canNotify) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REST_OVER)
            .setSmallIcon(R.drawable.ic_today)
            .setColor(ContextCompat.getColor(context, R.color.icon_background))
            .setContentTitle(context.getString(R.string.rest_over))
            .setContentText(context.getString(R.string.time_for_next_set))
            .setSubText(kept.dayName)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .setContentIntent(openApp())
            .build()
        @Suppress("MissingPermission")
        manager.notify(ID_REST_OVER, notification)
    }

    private fun openApp(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(command: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        command.hashCode(),
        Intent(context, RestReceiver::class.java).setAction(command),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Wakes Kamae when the rest runs out, on time when Android lets it, else as close as it can. */
    private fun scheduleEnd(endsAt: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val end = action(ACTION_END)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, end)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, end)
        }
    }

    private fun cancelEnd() {
        context.getSystemService(AlarmManager::class.java)?.cancel(action(ACTION_END))
    }

    companion object {
        const val CHANNEL_REST = "rest"
        const val CHANNEL_REST_OVER = "rest_over"
        private const val ID_REST = 1
        private const val ID_REST_OVER = 2
        const val ACTION_PAUSE = "ca.gureet.kitae.rest.PAUSE"
        const val ACTION_RESUME = "ca.gureet.kitae.rest.RESUME"
        const val ACTION_ADD = "ca.gureet.kitae.rest.ADD"
        const val ACTION_SKIP = "ca.gureet.kitae.rest.SKIP"
        const val ACTION_END = "ca.gureet.kitae.rest.END"
    }
}

/** The rest notification's buttons and the alarm at the end of a rest. */
class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.action ?: return
        (context.applicationContext as KamaeApplication).container.restNotifications.handle(command)
    }
}
