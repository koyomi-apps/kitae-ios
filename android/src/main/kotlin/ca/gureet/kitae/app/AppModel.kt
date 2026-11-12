// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.EpochDay
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.PlanSummary
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.Workout
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.RestTimer
import ca.gureet.kitae.model.WeightUnit
import ca.gureet.kitae.model.WorkoutProgress
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/** What Today and the workout show, as the iPhone's `AppModel` keeps it. */
data class AppState(
    val loaded: Boolean = false,
    val plans: List<PlanSummary> = emptyList(),
    val plan: PlanWithDays? = null,
    /** The day after the one done last. */
    val upNextDayId: Long? = null,
    /** The day picked on Today, instead of the one up next. */
    val pickedDayId: Long? = null,
    /** Whether a set has ever been done, before the workout in progress. */
    val hadDoneSets: Boolean = false,
    val workout: WorkoutState? = null,
    val isWorkoutPresented: Boolean = false,
    val isChoosingPlan: Boolean = false,
    /** Goes up each time a rest runs out, so the screen can tap the phone. */
    val restsFinished: Int = 0,
) {
    val days: List<DayWithExercises>
        get() = plan?.days.orEmpty()

    /** The day Today shows: the one picked, else the one in progress, else the one up next. */
    val selectedDay: DayWithExercises?
        get() = days.firstOrNull { it.day.id == pickedDayId }
            ?: workout?.dayId?.let { id -> days.firstOrNull { it.day.id == id } }
            ?: days.firstOrNull { it.day.id == upNextDayId }
            ?: days.firstOrNull()

    val hasDoneSets: Boolean
        get() = hadDoneSets || (workout?.progress?.doneCount ?: 0) > 0
}

/** The workout in progress, its exercises and sets as the screen shows them, and its rest. */
data class WorkoutState(
    val workout: Workout,
    /** Null when its day has been deleted. */
    val dayName: String?,
    val exercises: List<WorkoutExercise>,
    val rest: RestTimer = RestTimer(),
) {
    val id: Long
        get() = workout.id

    val dayId: Long?
        get() = workout.dayId

    val progress: WorkoutProgress
        get() = WorkoutProgress(exercises, hasDay = workout.dayId != null)
}

/** Kamae's state and what people do with it, for Today, the list of plans and the workout. */
class AppModel(
    private val container: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::currentSystemDefault,
) : ViewModel(), AppActions {
    private val database get() = container.database

    var state by mutableStateOf(AppState())
        private set

    val settings: StateFlow<AppSettings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var restEnd: Job? = null

    init {
        viewModelScope.launch {
            reload()
            if (state.plan == null) state = state.copy(isChoosingPlan = true)
        }
    }

    private fun today(now: Long = clock()) = EpochDay.of(Instant.fromEpochMilliseconds(now), timeZone())

    /** Reads the plans and the workout in progress again. */
    suspend fun reload() {
        val plans = database.plans()
        val workouts = database.workouts()
        val currentId = plans.currentId()
        val plan = currentId?.let { plans.plan(it) }
        val active = workouts.active(today())
        val workout = active?.let { found ->
            val kept = state.workout?.takeIf { it.id == found.workout.id }?.rest ?: container.rest.load(found.workout.id, clock())
            WorkoutState(found.workout, found.dayName, workouts.exercises(found.workout.id, found.workout.dayId), kept)
        }
        state = state.copy(
            loaded = true,
            plans = plans.all(),
            plan = plan,
            upNextDayId = plan?.dayAfter(workouts.lastDoneDayId())?.day?.id,
            hadDoneSets = workouts.hasDoneSets(),
            workout = workout,
            isWorkoutPresented = state.isWorkoutPresented && workout != null,
        )
        scheduleRestEnd()
    }

    /** Reads everything again, as when Kamae comes back to the front, maybe on another day. */
    fun refresh() = launch { reload() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // Plans and days

    override fun pickDay(id: Long) {
        state = state.copy(pickedDayId = id)
    }

    override fun choosePlan(open: Boolean) {
        state = state.copy(isChoosingPlan = open)
    }

    /** Follows another plan from today, which can't happen while a workout is going. */
    override fun selectPlan(id: Long) = launch {
        state = state.copy(isChoosingPlan = false)
        if (state.workout != null || id == state.plan?.plan?.id) return@launch
        database.plans().setCurrent(id, today())
        container.settings.setOnboardingDone()
        state = state.copy(pickedDayId = null)
        reload()
    }

    // The workout

    override fun presentWorkout(open: Boolean) {
        state = state.copy(isWorkoutPresented = open && state.workout != null)
    }

    /** Starts a workout of [day], or opens it again when it's the one going. */
    override fun start(day: DayWithExercises) = launch { begin(day) }

    private suspend fun begin(day: DayWithExercises) {
        val going = state.workout
        if (going != null) {
            if (going.dayId == day.day.id) state = state.copy(isWorkoutPresented = true)
            return
        }
        val now = clock()
        database.workouts().start(day.day.id, today(now), now)
        state = state.copy(pickedDayId = null, isWorkoutPresented = true)
        reload()
    }

    /** Ends the workout; Today moves on to the plan's next day. [then] starts another day after it. */
    fun finishWorkout(mode: FinishMode, addNewSets: Boolean, then: DayWithExercises? = null) = launch {
        val workout = state.workout ?: return@launch
        database.workouts().finish(workout.id, mode, addNewSets, clock())
        close(workout.id)
        reload()
        if (then != null) state.days.firstOrNull { it.day.id == then.day.id }?.let { begin(it) }
    }

    override fun finish(mode: FinishMode, addNewSets: Boolean) = finishWorkout(mode, addNewSets)

    override fun finishAndStart(mode: FinishMode, addNewSets: Boolean, day: DayWithExercises) = finishWorkout(mode, addNewSets, day)

    override fun minimize() = presentWorkout(false)

    override fun discard() = discardWorkout()

    /** Removes the workout and its sets; the plan stays the same. */
    fun discardWorkout() = launch {
        val workout = state.workout ?: return@launch
        database.workouts().discard(workout.id)
        close(workout.id)
        reload()
    }

    private fun close(workoutId: Long) {
        restEnd?.cancel()
        container.rest.save(workoutId, RestTimer())
        state = state.copy(workout = null, isWorkoutPresented = false)
    }

    private suspend fun reloadWorkout() {
        val workout = state.workout ?: return
        val exercises = database.workouts().exercises(workout.id, workout.dayId)
        state = state.copy(workout = state.workout?.copy(exercises = exercises))
    }

    /** Ticks a set off, or unticks it. Ticking starts the rest, unless it was the last set. */
    override fun toggleDone(setId: Long) = launch {
        val workout = state.workout ?: return@launch
        val set = workout.exercises.flatMap { it.sets }.firstOrNull { it.id == setId } ?: return@launch
        val rest = workout.progress.restAfterTicking(setId, settings.value.defaultRestSeconds)
        database.workouts().setDone(setId, !set.isDone)
        reloadWorkout()
        if (rest != null) changeRest { it.started(rest, clock()) }
    }

    /** Changes a set's reps, weight, type or rest. */
    override fun updateSet(setId: Long, draft: SetDraft) = launch {
        database.workouts().update(setId, draft)
        reloadWorkout()
    }

    /** Adds a set like the exercise's last one, as the + beside it does. */
    override fun addSet(exerciseId: Long) = launch {
        val workout = state.workout ?: return@launch
        val defaultKg = if (settings.value.unit == WeightUnit.Pounds) WeightUnit.Pounds.toKilograms(45.0) else 20.0
        database.workouts().addSetLikeLast(workout.id, exerciseId, workout.dayId, defaultKg)
        reloadWorkout()
    }

    override fun deleteSet(setId: Long) = launch {
        database.workouts().deleteSet(setId)
        reloadWorkout()
    }

    // The rest

    override fun pauseRest() = changeRest { it.paused(clock()) }

    override fun resumeRest() = changeRest { it.resumed(clock()) }

    override fun addRest(seconds: Int) = changeRest { it.adding(seconds, clock()) }

    override fun skipRest() = changeRest { RestTimer() }

    private fun changeRest(change: (RestTimer) -> RestTimer) {
        val workout = state.workout ?: return
        val rest = change(workout.rest)
        state = state.copy(workout = workout.copy(rest = rest))
        container.rest.save(workout.id, rest)
        scheduleRestEnd()
    }

    /** When a rest runs out, it ends, and the screen taps the phone. */
    private fun scheduleRestEnd() {
        restEnd?.cancel()
        val endsAt = state.workout?.rest?.endsAt ?: return
        restEnd = viewModelScope.launch {
            delay(maxOf(endsAt - clock(), 0))
            val workout = state.workout ?: return@launch
            if (workout.rest.endsAt != endsAt) return@launch
            container.rest.save(workout.id, RestTimer())
            state = state.copy(workout = workout.copy(rest = RestTimer()), restsFinished = state.restsFinished + 1)
        }
    }
}
