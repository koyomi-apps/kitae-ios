// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import ca.gureet.kitae.database.DatabaseFileTest
import ca.gureet.kitae.database.DoneSet
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.Workout
import ca.gureet.kitae.database.WorkoutSummary
import ca.gureet.kitae.database.bundledSeed
import ca.gureet.kitae.database.number
import ca.gureet.kitae.database.rows
import ca.gureet.kitae.database.texts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Weigh-ins, the profile and workout energy, as the iPhone's `BodyTests` check them. */
class BodyTest : DatabaseFileTest() {
    private fun near(expected: Double, actual: Double?) = assertTrue(actual != null && abs(expected - actual) < 1e-9, "$actual isn't $expected")

    @Test
    fun theBodyTablesLeaveRoomsVersionAlone() = runBlocking {
        val database = open()
        assertEquals(10L, database.number("PRAGMA user_version"))
        assertEquals(listOf("a8dd8c0661148678f11cce771546f37e"), database.texts("SELECT identity_hash FROM room_master_table WHERE id = 42"))
        assertEquals(
            listOf("body_measurements", "body_profile"),
            database.texts("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'body_%' ORDER BY name"),
        )
        database.close()
    }

    @Test
    fun aDayHasOneWeighIn() = runBlocking {
        val database = open()
        val body = database.body
        // Someone following the log sees each change, once it's saved.
        val latest = MutableStateFlow<BodyLog?>(null)
        val watching = launch(Dispatchers.Default) { body.observeLog().collect { latest.value = it } }
        suspend fun follows(dates: List<Long>) = withTimeout(5_000) { latest.first { it?.entries?.map { entry -> entry.date } == dates } }
        follows(emptyList())
        body.save(100, 80.0, null)
        body.save(100, 79.5, 18.0)
        body.save(98, 81.0, null)
        var log = body.log()
        assertEquals(listOf(98L, 100L), log.entries.map { it.date })
        assertEquals(79.5, log.entries.last().weightKg)
        assertEquals(18.0, log.entries.last().bodyFatPercent)
        follows(listOf(98L, 100L))

        // Moving a weigh-in to another day takes it off its old day.
        body.save(99, 81.0, null, replacingId = log.entries.first().id)
        log = body.log()
        assertEquals(listOf(99L, 100L), log.entries.map { it.date })

        // Without a weight or body fat, the day has nothing.
        body.save(100, null, null)
        assertEquals(listOf(99L), body.log().entries.map { it.date })
        body.delete(body.log().entries.single().id)
        assertTrue(body.log().entries.isEmpty())
        follows(emptyList())
        watching.cancel()
        database.close()
    }

    @Test
    fun theProfileIsSavedAsAndroidWouldReadIt() = runBlocking {
        val database = open()
        val profile = BodyProfile(heightCm = 178.0, birthDate = 9630, sex = Sex.Female, activityLevel = ActivityLevel.ModeratelyActive)
        database.body.save(profile)
        assertEquals(profile, database.body.log().profile)
        val row = database.rows("SELECT id, sex, activityLevel FROM body_profile") { Triple(it.getLong(0), it.getText(1), it.getText(2)) }
        assertEquals(listOf(Triple(1L, "Female", "ModeratelyActive")), row)
        database.close()
    }

    @Test
    fun aBackupFromAndroidGetsKitaesOwnTables() = runBlocking {
        // Room's tables only, as Kitae for Android makes them.
        makeDatabase(10)
        val database = open()
        assertTrue(database.body.log().isEmpty)
        database.body.save(101, 79.0, null)
        assertEquals(1, database.body.log().entries.size)
        database.notes.save(21, "Seat on 4")
        assertEquals(mapOf(21L to "Seat on 4"), database.notes.all())
        database.close()
    }

    @Test
    fun aDayUsesTheLastWeightBeforeIt() {
        val log = BodyLog(
            entries = listOf(
                BodyEntry(id = 1, date = 10, weightKg = 80.0, bodyFatPercent = 20.0),
                BodyEntry(id = 2, date = 20, weightKg = null, bodyFatPercent = 19.0),
                BodyEntry(id = 3, date = 30, weightKg = 78.0, bodyFatPercent = null),
            ),
        )
        assertEquals(80.0, log.weightKg(5))
        assertEquals(80.0, log.weightKg(25))
        assertEquals(78.0, log.weightKg(30))
        assertEquals(19.0, log.bodyFatPercent(25))
        assertEquals(3L, log.latestWeight?.id)
        assertEquals(64.0, log.entries[0].leanKg)
        assertNull(BodyLog().weightKg(10))
    }

    @Test
    fun ageCountsWholeYears() {
        val profile = BodyProfile(birthDate = 9630) // 14 May 1996
        assertEquals(29, profile.age(9630L + 365 * 30 + 6)) // 7 May 2026
        assertEquals(30, profile.age(9630L + 365 * 30 + 8)) // 15 May 2026
    }

    private fun workout(minutes: Int?, sets: Int, date: Long = 100, id: Long = 1) = WorkoutSummary(
        workout = Workout(
            date = date,
            planId = null,
            dayId = null,
            startedAt = if (minutes == null) null else 0,
            finishedAt = minutes?.let { it * 60_000L },
            id = id,
        ),
        dayName = "Legs",
        setCount = sets,
        doneCount = sets,
    )

    private fun done(key: String, reps: Int, kg: Double, type: SetType = SetType.Standard, target: String = "Quads", isIsometric: Boolean = false) =
        DoneSet(exerciseId = 0, libraryKey = key, target = target, isIsometric = isIsometric, reps = reps, weightKg = kg, type = type)

    @Test
    fun aSetCostsTheWeightItMovesByTheExercise() {
        // Squats: 0.0075 kcal a rep for each kilogram moved, the bar and 85% of the body.
        val squats = ExerciseEnergy.of("barbell-back-squats", "Quads", isIsometric = false)
        near(0.0075 * (100 + 68) * 10, squats.kilocalories(reps = 10, weightKg = 100.0, bodyKg = 80.0))
        // Curls with a pair of 12 kg dumbbells: both count.
        val curls = ExerciseEnergy.of("dumbbell-curls", "Biceps", isIsometric = false)
        near(0.011 * (24 + 1.6) * 10, curls.kilocalories(reps = 10, weightKg = 12.0, bodyKg = 80.0))
        // A minute of plank: 2.5 METs over resting.
        val plank = ExerciseEnergy.of("plank", "Core", isIsometric = true)
        near(2.5 * 80 / 60, plank.kilocalories(reps = 60, weightKg = 0.0, bodyKg = 80.0))
        // Dips on a machine that takes 30 kg off: 95% of the body less the help, and never below nothing.
        val assisted = ExerciseEnergy.of("assisted-dips", "Triceps", isIsometric = false)
        near(0.011 * (76 - 30) * 10, assisted.kilocalories(reps = 10, weightKg = 30.0, bodyKg = 80.0))
        assertEquals(0.0, assisted.kilocalories(reps = 10, weightKg = 100.0, bodyKg = 80.0))
        // A minute of farmer's carry with two 20 kg dumbbells: 2.5 METs over resting, for the body and both weights.
        val carry = ExerciseEnergy.of("dumbbell-farmers-carry", "Traps", isIsometric = true)
        near(2.5 * 120 / 60, carry.kilocalories(reps = 60, weightKg = 20.0, bodyKg = 80.0))
        // A big lift costs far more than a small one.
        assertTrue(
            squats.kilocalories(reps = 10, weightKg = 40.0, bodyKg = 80.0) > 3 * curls.kilocalories(reps = 10, weightKg = 10.0, bodyKg = 80.0),
        )
        // People's own exercises go by their muscle group.
        assertEquals(0.0034, ExerciseEnergy.of(null, "Calves", isIsometric = false).perKilogram)
        assertNotNull(ExerciseEnergy.of(null, "Core", isIsometric = true).holdMETs)
    }

    @Test
    fun everyStarterExerciseHasItsOwnEnergy() {
        assertEquals(bundledSeed.exercises.map { it.key }.toSet(), ExerciseEnergy.starters.keys)
        for ((key, energy) in ExerciseEnergy.starters) assertTrue(energy.holdMETs != null || energy.perKilogram > 0, key)
        // The holds are Kitae's isometric exercises.
        val isometric = bundledSeed.exercises.filter { it.isIsometric }.map { it.key }.toSet()
        assertEquals(isometric, ExerciseEnergy.starters.filterValues { it.holdMETs != null }.keys)
    }

    @Test
    fun setTypesCountWhatTheirRepsDontShow() {
        val between = 0.5 * 80 * 2.5 / 60
        val plain = WorkoutEnergy.active(listOf(done("barbell-back-squats", 10, 100.0)), durationMilliseconds = null, bodyKg = 80.0)
        val failure = WorkoutEnergy.active(listOf(done("barbell-back-squats", 10, 100.0, SetType.Failure)), durationMilliseconds = null, bodyKg = 80.0)
        near(1.3 * (plain - between), failure - between)
        assertEquals(1.3, SetType.Drop.energyFactor)
        assertEquals(1.3, SetType.RestPause.energyFactor)
        // A warm-up counts for its own, lighter weight.
        val warmUp = WorkoutEnergy.active(listOf(done("barbell-back-squats", 10, 40.0, SetType.Warmup)), durationMilliseconds = null, bodyKg = 80.0)
        assertTrue(warmUp < plain)
    }

    @Test
    fun heightScalesHowFarEachRepMoves() {
        val squats = ExerciseEnergy.of("barbell-back-squats", "Quads", isIsometric = false)
        val average = squats.kilocalories(reps = 10, weightKg = 100.0, bodyKg = 80.0)
        val tall = squats.kilocalories(reps = 10, weightKg = 100.0, bodyKg = 80.0, reach = WorkoutEnergy.reach(190.0))
        near(average * 190 / 175, tall)
        // A hold doesn't move, so height doesn't change it.
        val plank = ExerciseEnergy.of("plank", "Core", isIsometric = true)
        assertEquals(plank.kilocalories(60, 0.0, 80.0), plank.kilocalories(60, 0.0, 80.0, reach = 1.1))
        // Without a height nothing changes, and far from the usual heights the change stops.
        assertEquals(1.0, WorkoutEnergy.reach(null))
        assertEquals(1.0, WorkoutEnergy.reach(0.0))
        assertEquals(0.8, WorkoutEnergy.reach(120.0))
        assertEquals(1.2, WorkoutEnergy.reach(230.0))
    }

    @Test
    fun aWorkoutUsesTheHeightOfTheProfile() {
        val squats = listOf(done("barbell-back-squats", 10, 100.0))
        val weighIn = listOf(BodyEntry(id = 1, date = 90, weightKg = 80.0))
        val short = assertNotNull(BodyLog(BodyProfile(heightCm = 160.0), weighIn).activeKilocalories(workout(30, 1), squats))
        val unknown = assertNotNull(BodyLog(entries = weighIn).activeKilocalories(workout(30, 1), squats))
        // The sets scale, the 3 minutes between them don't.
        val between = 0.5 * 80 * 3 / 60
        near((unknown - between) * 160 / 175, short - between)
    }

    @Test
    fun timeBetweenSetsCountsLittleAndHasALimit() {
        val sets = List(10) { done("barbell-bench-press", 10, 60.0, target = "Chest") }
        val lifting = WorkoutEnergy.active(sets, durationMilliseconds = 0, bodyKg = 80.0)
        val hour = WorkoutEnergy.active(sets, durationMilliseconds = 3_600_000, bodyKg = 80.0)
        val overnight = WorkoutEnergy.active(sets, durationMilliseconds = 8 * 3_600_000, bodyKg = 80.0)
        // At most 3 minutes a set: 30 minutes at half a MET for 80 kg is 20 kcal.
        near(20.0, hour - lifting)
        assertEquals(hour, overnight)
        // Without the workout's length, 2.5 minutes a set.
        val unknown = WorkoutEnergy.active(sets, durationMilliseconds = null, bodyKg = 80.0)
        near(0.5 * 80 * 25 / 60, unknown - lifting)
    }

    @Test
    fun aLegDayUsesMoreThanAnArmDay() {
        val log = BodyLog(entries = listOf(BodyEntry(id = 1, date = 90, weightKg = 80.0)))
        val legs = List(4) { done("barbell-back-squats", 8, 100.0) } +
            List(3) { done("barbell-romanian-deadlift", 10, 80.0, target = "Hamstrings") }
        val arms = List(4) { done("dumbbell-curls", 12, 12.0, target = "Biceps") } +
            List(3) { done("tricep-pushdowns", 12, 25.0, target = "Triceps") }
        val legDay = assertNotNull(log.activeKilocalories(workout(45, 7), legs))
        val armDay = assertNotNull(log.activeKilocalories(workout(45, 7), arms))
        assertTrue(legDay > 2 * armDay)
        // The plan's worked examples: about 99 kcal and about 38.
        assertTrue(legDay in 90.0..110.0 && armDay in 30.0..45.0, "$legDay, $armDay")
    }

    @Test
    fun noWeightOrNoSetsMeansNoEnergy() {
        val squats = listOf(done("barbell-back-squats", 10, 100.0))
        assertNull(BodyLog().activeKilocalories(workout(60, 1), squats))
        val log = BodyLog(entries = listOf(BodyEntry(id = 1, date = 90, weightKg = 80.0)))
        assertNull(log.activeKilocalories(workout(60, 0), emptyList()))
        // A workout without its times, from older Android data, still has its sets.
        assertNotNull(log.activeKilocalories(workout(null, 1), squats))
    }

    @Test
    fun theWeekAddsUpItsWorkouts() {
        val log = BodyLog(entries = listOf(BodyEntry(id = 1, date = 90, weightKg = 80.0)))
        val first = workout(60, 1)
        val second = workout(30, 1, id = 2)
        val sets = mapOf(1L to listOf(done("barbell-back-squats", 10, 100.0)), 2L to listOf(done("barbell-bench-press", 10, 60.0, target = "Chest")))
        val totals = log.totals(listOf(first, second), sets)
        assertEquals(2, totals.workouts)
        assertEquals(90, totals.minutes)
        val expected = (log.activeKilocalories(first, sets.getValue(1)) ?: 0.0) + (log.activeKilocalories(second, sets.getValue(2)) ?: 0.0)
        near(expected, totals.kilocalories)
        assertNull(BodyLog().totals(listOf(first), sets).kilocalories)
    }

    @Test
    fun weeksStartOnThePhonesFirstDay() {
        // 1 January 1970 was a Thursday.
        assertEquals(-4L, startOfWeek(0, firstWeekday = 1)) // Sunday
        assertEquals(-3L, startOfWeek(0, firstWeekday = 2)) // Monday
        assertEquals(3L, startOfWeek(3, firstWeekday = 1)) // Sunday 4 January
    }

    @Test
    fun bodyWeightsAndPercentagesAreShownToATenth() {
        val kilograms = WeightFormatter(WeightUnit.Kilograms, DecimalStyle.Point)
        assertEquals("78.4 kg", kilograms.body(78.44))
        assertEquals("18%", kilograms.percent(18.0))
        assertEquals("18.3%", kilograms.percent(18.25))
        assertEquals("176,4 lb", WeightFormatter(WeightUnit.Pounds, DecimalStyle.Comma).body(80.0))
        assertEquals("5 ft 11 in", HeightFormatter(WeightUnit.Pounds).string(180.0))
        assertEquals("180 cm", HeightFormatter(WeightUnit.Kilograms).string(179.6))
        assertEquals(Pair(5, 11), HeightFormatter.feetAndInches(180.0))
        near(180.34, HeightFormatter.centimetres(5, 11.0))
        assertEquals("≈ 230 kcal", kilocaloriesLabel(232.0))
    }
}
