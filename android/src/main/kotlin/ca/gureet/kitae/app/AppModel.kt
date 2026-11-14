// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.gureet.kitae.backup.BackupContents
import ca.gureet.kitae.backup.BackupException
import ca.gureet.kitae.backup.BackupProblem
import ca.gureet.kitae.backup.Backups
import ca.gureet.kitae.database.ActivityWeeks
import ca.gureet.kitae.database.DatabaseFiles
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.DoneSet
import ca.gureet.kitae.database.EpochDay
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.PlanSummary
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.TrainingTotals
import ca.gureet.kitae.database.Workout
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSummary
import ca.gureet.kitae.database.historyMonths
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.BodyProfile
import ca.gureet.kitae.model.RestTimer
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.model.WeightUnit
import ca.gureet.kitae.model.WorkoutProgress
import ca.gureet.kitae.model.startOfWeek
import ca.gureet.kitae.muscles.MuscleVolume
import ca.gureet.kitae.workout.phoneDecimalSeparator
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** Goes up each time what's saved is read again, so the pages read theirs again too. */
    val revision: Int = 0,
    val backup: BackupState = BackupState(),
    /** Set once a backup is restored or Kamae is reset, for Kamae to start again. */
    val restarting: Boolean = false,
    /** The muscle maps and animations, once they're read. */
    val library: Library? = null,
    /** People's notes on their exercises, by exercise. */
    val notes: Map<Long, String> = emptyMap(),
    /** The weigh-ins and the profile. */
    val bodyLog: BodyLog = BodyLog(),
    /** The workout just finished, while its summary shows. */
    val finishedWorkoutId: Long? = null,
    /** Today's finished workouts, newest first, and their done sets. */
    val doneToday: List<WorkoutSummary> = emptyList(),
    val doneTodaySets: Map<Long, List<DoneSet>> = emptyMap(),
    /** Whether the welcome is showing. */
    val isWelcoming: Boolean = false,
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

/** Where backups go, how the last one went, and a backup being restored. */
data class BackupState(
    val place: BackupPlace = BackupPlace(),
    /** The backup folder's name, as Android's picker shows it. */
    val folderName: String? = null,
    val isWorking: Boolean = false,
    /** Why the last backup didn't work. */
    val lastProblem: BackupProblem? = null,
    /** Why a backup can't be restored, to say in a dialog. */
    val restoreProblem: BackupProblem? = null,
    /** A backup that was checked and waits for "Restore this backup?". */
    val restoring: PendingRestore? = null,
)

/** A checked backup, and the name of its file. */
class PendingRestore(val name: String, val contents: BackupContents)

/** You's totals, activity, this week's workouts and their sets, and the muscles worked in the last 7 days. */
data class YouData(
    val totals: TrainingTotals,
    val weeks: ActivityWeeks,
    val muscles: MuscleVolume = MuscleVolume(),
    val week: List<WorkoutSummary> = emptyList(),
    val weekSets: Map<Long, List<DoneSet>> = emptyMap(),
)

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

/**
 * Kamae's state and what people do with it: Today, the list of plans and the workout, the pages'
 * data, and the changes made on them, each of which reads everything again.
 */
class AppModel(
    private val container: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::currentSystemDefault,
) : ViewModel(), AppActions, PageSource, BodyActions {
    private val database get() = container.database

    var state by mutableStateOf(AppState())
        private set

    val settings: StateFlow<AppSettings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var restEnd: Job? = null

    init {
        viewModelScope.launch {
            reload()
            when {
                state.plan != null -> Unit
                !container.settings.onboardingDone.first() -> state = state.copy(isWelcoming = true)
                else -> state = state.copy(isChoosingPlan = true)
            }
        }
        viewModelScope.launch { database.body.observeLog().collect { state = state.copy(bodyLog = it) } }
        viewModelScope.launch {
            container.settings.backups.collect { place ->
                val name = place.folderUri?.let { uri ->
                    withContext(Dispatchers.IO) { BackupFolder(container.resolver, uri.toUri()).name }
                }
                state = state.copy(backup = state.backup.copy(place = place, folderName = name))
            }
        }
        viewModelScope.launch { state = state.copy(library = library()) }
        viewModelScope.launch { database.notes.observeAll().collect { state = state.copy(notes = it) } }
        // The notification's buttons, and the end of a rest while Kamae is closed.
        viewModelScope.launch {
            container.restNotifications.changed.collect {
                val workout = state.workout ?: return@collect
                val rest = container.rest.load(workout.id, clock())
                if (rest != workout.rest) state = state.copy(workout = workout.copy(rest = rest))
                scheduleRestEnd()
            }
        }
    }

    /** The muscle maps and animations, read off the main thread the first time. */
    private suspend fun library(): Library = withContext(Dispatchers.Default) { container.library }

    private fun today(now: Long = clock()) = EpochDay.of(Instant.fromEpochMilliseconds(now), timeZone())

    /** Reads the plans and the workout in progress again. */
    suspend fun reload() {
        val plans = database.plans()
        val workouts = database.workouts()
        val currentId = plans.currentId()
        val plan = currentId?.let { plans.plan(it) }
        val today = today()
        val history = database.history()
        val active = workouts.active(today)
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
            revision = state.revision + 1,
            doneToday = history.finished(today, today),
            doneTodaySets = history.doneSets(today, today),
        )
        scheduleRestEnd()
        syncRest()
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
        // Its summary, with a set done, unless Settings say not to or another day starts after it.
        val summary = then == null && settings.value.showsWorkoutSummary && workout.progress.doneCount > 0
        database.workouts().finish(workout.id, mode, addNewSets, clock())
        close(workout.id)
        reload()
        if (summary) state = state.copy(finishedWorkoutId = workout.id)
        if (settings.value.backUpAfterWorkouts && state.backup.place.folderUri != null) backUp()
        if (then != null) state.days.firstOrNull { it.day.id == then.day.id }?.let { begin(it) }
    }

    override fun finish(mode: FinishMode, addNewSets: Boolean) = finishWorkout(mode, addNewSets)

    override fun finishAndStart(mode: FinishMode, addNewSets: Boolean, day: DayWithExercises) = finishWorkout(mode, addNewSets, day)

    override fun minimize() = presentWorkout(false)

    override fun saveNote(exerciseId: Long, note: String) = launch { database.notes.save(exerciseId, note) }

    override fun closeSummary() {
        state = state.copy(finishedWorkoutId = null)
    }

    // Body

    override fun saveWeighIn(date: Long, weightKg: Double?, bodyFatPercent: Double?, replacingId: Long?) = launch {
        database.body.save(date, weightKg, bodyFatPercent, replacingId)
    }

    override fun deleteWeighIn(id: Long) = launch {
        database.body.delete(id)
    }

    override fun saveProfile(profile: BodyProfile) = launch {
        database.body.save(profile)
    }

    override fun finishWelcome() = launch {
        container.settings.setOnboardingDone()
        state = state.copy(isWelcoming = false, isChoosingPlan = state.plan == null)
    }

    override fun setTextSize(size: TextSize?) = launch { container.settings.setTextSize(size) }

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
        container.restNotifications.refresh(clock())
    }

    private suspend fun reloadWorkout() {
        val workout = state.workout ?: return
        val exercises = database.workouts().exercises(workout.id, workout.dayId)
        state = state.copy(workout = state.workout?.copy(exercises = exercises))
        syncRest()
    }

    /** Keeps the rest notification showing the rest going, with the sets done and the next one. */
    private fun syncRest() {
        val workout = state.workout
        if (workout != null && workout.rest.isActive) {
            val settings = settings.value
            container.restNotifications.describe(workout, WeightFormatter(settings.unit, settings.decimals, phoneDecimalSeparator()), settings.restAlerts)
        }
        container.restNotifications.refresh(clock())
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
        syncRest()
    }

    /** When a rest runs out, it ends, and the screen taps the phone. */
    private fun scheduleRestEnd() {
        restEnd?.cancel()
        val endsAt = state.workout?.rest?.endsAt ?: return
        restEnd = viewModelScope.launch {
            delay(maxOf(endsAt - clock(), 0))
            val workout = state.workout ?: return@launch
            if (workout.rest.endsAt != endsAt) return@launch
            // Ends it for the notification too, which says "Rest over" when Kamae isn't on screen.
            container.restNotifications.handle(RestNotifications.ACTION_END, clock())
            container.rest.save(workout.id, RestTimer())
            state = state.copy(workout = workout.copy(rest = RestTimer()), restsFinished = state.restsFinished + 1)
        }
    }
    // The pages' data

    override suspend fun history(): HistoryPage {
        val activeId = state.workout?.id
        val history = database.history()
        val workouts = history.workouts(activeId)
        val dates = workouts.map { it.workout.date }
        val sets = if (dates.isEmpty()) emptyMap() else history.doneSets(dates.min(), dates.max())
        return HistoryPage(historyMonths(workouts, activeId), sets)
    }

    override suspend fun workoutDone(id: Long, withBests: Boolean): WorkoutDone? {
        val history = database.history()
        val summary = history.summary(id) ?: return null
        val date = summary.workout.date
        return WorkoutDone(summary, history.doneSets(date, date)[id].orEmpty(), if (withBests) database.exercises().newBests(id) else emptyList())
    }

    override suspend fun workoutRecord(id: Long): WorkoutRecord? {
        val history = database.history()
        val summary = history.summary(id) ?: return null
        return WorkoutRecord(
            summary,
            database.workouts().exercises(id, dayId = null),
            history.previousId(id, summary.workout.dayId, summary.workout.date),
        )
    }

    override suspend fun exerciseList(recent: Int): ExerciseList {
        val exercises = database.exercises()
        return ExerciseList(
            exercises.all(),
            exercises.lastSessions(),
            if (recent > 0) exercises.recentIds(recent) else emptyList(),
        )
    }

    override suspend fun exerciseDetails(id: Long): ExerciseDetails? {
        val exercises = database.exercises()
        val exercise = exercises.exercise(id) ?: return null
        return ExerciseDetails(exercise, exercises.history(id))
    }

    override suspend fun plan(id: Long): PlanWithDays? = database.plans().plan(id)

    // Changes on the pages

    /** What a new set weighs when there's nothing to copy: three sets of 12 at 20 kg, or 45 lb. */
    private val newSet: SetDraft
        get() = SetDraft(12, settings.value.unit.defaultWeightKg)

    override fun deleteWorkout(id: Long) = launch {
        if (id == state.workout?.id) return@launch
        database.workouts().discard(id)
        reload()
    }

    override suspend fun saveExercise(draft: ExerciseDraft, id: Long?): Long {
        val exercises = database.exercises()
        val saved = if (id == null) exercises.create(draft) else id.also { exercises.update(it, draft) }
        reload()
        return saved
    }

    /** Not while the exercise is in the workout going. */
    override fun deleteExercise(id: Long) = launch {
        if (state.workout?.exercises?.any { it.exercise.id == id } == true) return@launch
        database.exercises().delete(id)
        reload()
    }

    override suspend fun createPlan(name: String): Long = database.planEdits().create(name).also { reload() }

    override suspend fun renamePlan(planId: Long, name: String) {
        database.planEdits().rename(planId, name)
        reload()
    }

    /** Not the plan of the workout going. */
    override fun deletePlan(planId: Long) = launch {
        if (planId == state.workout?.workout?.planId) return@launch
        database.planEdits().delete(planId)
        state = state.copy(pickedDayId = null)
        reload()
    }

    override suspend fun addDay(planId: Long, name: String): Long = database.planEdits().addDay(planId, name).also { reload() }

    override suspend fun renameDay(dayId: Long, name: String) {
        database.planEdits().renameDay(dayId, name)
        reload()
    }

    /** Not the day of the workout going. */
    override fun deleteDay(dayId: Long) = launch {
        if (dayId == state.workout?.dayId) return@launch
        database.planEdits().deleteDay(dayId)
        if (state.pickedDayId == dayId) state = state.copy(pickedDayId = null)
        reload()
    }

    override fun orderDays(dayIds: List<Long>) = launch {
        database.planEdits().orderDays(dayIds)
        reload()
    }

    override suspend fun addExerciseToDay(dayId: Long, exerciseId: Long): Long {
        val edits = database.planEdits()
        val sets = edits.suggestedSets(exerciseId, newSet)
        return edits.addExercise(dayId, exerciseId, sets).also { reload() }
    }

    override fun removeFromDay(dayExerciseId: Long) = launch {
        database.planEdits().removeExercise(dayExerciseId)
        reload()
    }

    override fun orderExercises(dayExerciseIds: List<Long>) = launch {
        database.planEdits().orderExercises(dayExerciseIds)
        reload()
    }

    override fun addPlannedSet(dayExerciseId: Long, last: SetDraft?) = launch {
        val set = last ?: newSet
        database.planEdits().addSet(dayExerciseId, if (set.type == SetType.Warmup) set.copy(type = SetType.Standard) else set)
        reload()
    }

    override fun updatePlannedSet(plannedSetId: Long, set: SetDraft) = launch {
        database.planEdits().updateSet(plannedSetId, set)
        reload()
    }

    override fun deletePlannedSet(plannedSetId: Long) = launch {
        database.planEdits().deleteSet(plannedSetId)
        reload()
    }

    // You

    override suspend fun youData(today: Long, firstWeekday: Int): YouData {
        val stats = database.stats()
        val history = database.history()
        val weekStart = startOfWeek(today, firstWeekday)
        return YouData(
            stats.totals(),
            stats.activity(today, firstWeekday),
            MuscleVolume(stats.exerciseSets(today - 6, today), library().muscles),
            history.finished(weekStart, today),
            history.doneSets(weekStart, today),
        )
    }

    // Settings

    override fun updateSettings(change: (AppSettings) -> AppSettings) = launch { container.settings.update(change) }

    override fun setBackupFolder(uri: String) = launch {
        runCatching { BackupFolder.keep(container.resolver, uri.toUri()) }
        container.settings.setBackupFolder(uri)
        state = state.copy(backup = state.backup.copy(lastProblem = null))
    }

    override fun backUpNow() = launch { backUp() }

    /** Saves a backup in the backup folder, as kitae_backup_DD_MM.zip, or [name]. */
    private suspend fun backUp(name: String? = null): Boolean {
        val uri = state.backup.place.folderUri ?: return false
        state = state.copy(backup = state.backup.copy(isWorking = true))
        val problem = withContext(Dispatchers.IO) {
            try {
                val folder = BackupFolder(container.resolver, uri.toUri())
                if (!BackupFolder.isReachable(container.resolver, folder.uri)) return@withContext BackupProblem.FolderUnavailable
                // Its own folder, apart from a backup being restored, which this one may come before.
                val work = File(container.backupWork, "make").apply { deleteRecursively(); mkdirs() }
                val zip = Backups.makeArchive(database, settings.value.backupPreferences(planChosen = state.plan != null), work.path, ZipArchive)
                folder.save(File(zip), name ?: Backups.fileName(today()))
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                BackupProblem.FolderUnavailable
            }
        }
        if (problem == null && name == null) container.settings.setLastBackup(clock() / 1000)
        state = state.copy(backup = state.backup.copy(isWorking = false, lastProblem = problem))
        return problem == null
    }

    /** Checks the backup at [uri] before asking whether to restore it. */
    override fun openBackup(uri: String) = launch {
        if (state.workout != null) {
            state = state.copy(backup = state.backup.copy(restoreProblem = BackupProblem.WorkoutInProgress))
            return@launch
        }
        state = state.copy(backup = state.backup.copy(isWorking = true))
        val result = withContext(Dispatchers.IO) {
            try {
                val work = File(container.backupWork, "restore").apply { deleteRecursively(); mkdirs() }
                val zip = File(work, "backup.zip")
                val source = uri.toUri()
                (container.resolver.openInputStream(source) ?: throw BackupException(BackupProblem.NotKitae)).use { input ->
                    zip.outputStream().use { input.copyTo(it) }
                }
                Result.success(PendingRestore(displayName(container.resolver, source), Backups.open(zip.path, work.path, ZipArchive)))
            } catch (problem: BackupException) {
                Result.failure(problem)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(BackupException(BackupProblem.NotKitae, error))
            }
        }
        state = state.copy(
            backup = state.backup.copy(
                isWorking = false,
                restoring = result.getOrNull(),
                restoreProblem = (result.exceptionOrNull() as? BackupException)?.problem,
            ),
        )
    }

    override fun cancelRestore() {
        state = state.copy(backup = state.backup.copy(restoring = null, restoreProblem = null))
    }

    /**
     * Restores the checked backup: what's in Kamae now is saved first in the backup folder, then
     * the database and settings are replaced and Kamae starts again, so nothing keeps using the
     * closed database.
     */
    override fun restoreBackup() = launch {
        val pending = state.backup.restoring ?: return@launch
        if (state.workout != null) {
            state = state.copy(backup = state.backup.copy(restoring = null, restoreProblem = BackupProblem.WorkoutInProgress))
            return@launch
        }
        if (state.backup.place.folderUri != null && !backUp(Backups.BEFORE_RESTORE_NAME)) {
            state = state.copy(backup = state.backup.copy(restoring = null, restoreProblem = BackupProblem.FolderUnavailable))
            return@launch
        }
        restEnd?.cancel()
        withContext(Dispatchers.IO) {
            container.database.close()
            Backups.install(pending.contents, container.databasePath)
        }
        container.settings.restore(pending.contents.preferences)
        state = state.copy(restarting = true)
    }

    /** Deletes everything but the backups, like a fresh install, and starts again. */
    override fun resetKamae() = launch {
        restEnd?.cancel()
        state.workout?.let { container.rest.save(it.id, RestTimer()) }
        container.restNotifications.refresh(clock())
        withContext(Dispatchers.IO) {
            container.database.close()
            DatabaseFiles.delete(container.databasePath)
        }
        container.settings.reset()
        state = state.copy(restarting = true)
    }

    override fun dismissBackupProblem() {
        state = state.copy(backup = state.backup.copy(restoreProblem = null))
    }
}
