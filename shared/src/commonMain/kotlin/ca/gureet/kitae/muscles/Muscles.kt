// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.muscles

import ca.gureet.kitae.database.DoneSet
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseSetCount
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.database.SetType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The muscles the muscle map and the animations show, as the iPhone names them
 * (`ExerciseMotion.swift`). [stored] is the name in `ExerciseMuscles.json` and
 * `ExerciseMotions.json`.
 */
enum class Muscle(val stored: String) {
    Quads("quads"),
    Hamstrings("hamstrings"),
    Glutes("glutes"),
    Calves("calves"),
    Chest("chest"),
    Abs("abs"),
    Obliques("obliques"),
    Lats("lats"),
    Traps("traps"),
    LowerBack("lowerBack"),
    FrontDelts("frontDelts"),
    SideDelts("sideDelts"),
    RearDelts("rearDelts"),
    Biceps("biceps"),
    Triceps("triceps"),
    Forearms("forearms"),
    UpperBack("upperBack"),
    Adductors("adductors"),
    Abductors("abductors"),
    ;

    companion object {
        fun fromStored(stored: String): Muscle? = entries.firstOrNull { it.stored == stored }
    }
}

/** The muscles an exercise works: the ones it works most, and the ones that help. */
data class MuscleWork(
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
)

/** The muscles the map shows for an exercise of this group, as the iPhone's `MuscleGroup.muscles`. */
val MuscleGroup.muscles: List<Muscle>
    get() = when (this) {
        MuscleGroup.Biceps -> listOf(Muscle.Biceps)
        MuscleGroup.Triceps -> listOf(Muscle.Triceps)
        MuscleGroup.Shoulders -> listOf(Muscle.FrontDelts, Muscle.SideDelts, Muscle.RearDelts)
        MuscleGroup.Quads -> listOf(Muscle.Quads)
        MuscleGroup.Hamstrings -> listOf(Muscle.Hamstrings)
        MuscleGroup.Calves -> listOf(Muscle.Calves)
        MuscleGroup.Glutes -> listOf(Muscle.Glutes)
        MuscleGroup.Core -> listOf(Muscle.Abs, Muscle.Obliques)
        MuscleGroup.Chest -> listOf(Muscle.Chest)
        MuscleGroup.Traps -> listOf(Muscle.Traps)
        MuscleGroup.Lats -> listOf(Muscle.Lats)
        MuscleGroup.UpperBack -> listOf(Muscle.UpperBack)
    }

/**
 * The muscle map's body, seen from the front and from the back, and each of Kitae's exercises'
 * muscles by library key, as `scripts/motions/muscles.js` writes them to the iPhone's
 * `ExerciseMuscles.json`. A shape is closed: its first point, then for each point after it a
 * cubic Bézier's two control points and the point, all in one list.
 */
@Serializable
class MuscleMap(
    /** The size of the picture of one side. */
    val width: Double,
    val height: Double,
    /** The body's outline, in parts that overlap. */
    val body: List<List<Double>>,
    /** Each muscle's shapes on that side, by its stored name. */
    val front: Map<String, List<List<Double>>>,
    val back: Map<String, List<List<Double>>>,
    val exercises: List<Entry>,
) {
    @Serializable
    class Entry(
        val key: String,
        val name: String,
        val primary: List<String>,
        val secondary: List<String>,
    ) {
        val work: MuscleWork
            get() = MuscleWork(primary.mapNotNull(Muscle::fromStored), secondary.mapNotNull(Muscle::fromStored))
    }

    enum class Side { Front, Back }

    /** The muscles drawn on a side with their shapes, in the order of their names. */
    fun muscles(side: Side): List<Pair<Muscle, List<List<Double>>>> {
        val shapes = if (side == Side.Front) front else back
        return shapes.keys.sorted().mapNotNull { key -> Muscle.fromStored(key)?.let { it to shapes.getValue(key) } }
    }
}

/**
 * Each of Kitae's exercises' muscles, and what to show for people's own exercises, from the
 * text of `ExerciseMuscles.json`, which each app hands in, as the iPhone's `ExerciseMuscles`.
 */
class ExerciseMuscles(val map: MuscleMap) {
    /** By library key. */
    val byKey: Map<String, MuscleWork> =
        map.exercises.distinctBy { it.key }.associate { it.key to it.work }

    /** One of Kitae's exercises' own muscles, or for people's own exercises, their muscle group's. */
    fun work(exercise: Exercise): MuscleWork? = work(exercise.libraryKey, exercise.target)

    /** The same, from an exercise's library key and its muscle group as it's stored, like "Chest". */
    fun work(key: String?, target: String): MuscleWork? {
        if (key != null) byKey[key]?.let { return it }
        val group = MuscleGroup.fromStored(target) ?: return null
        return MuscleWork(group.muscles, emptyList())
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads the text of `ExerciseMuscles.json`. */
        fun decode(text: String): ExerciseMuscles = ExerciseMuscles(json.decodeFromString(MuscleMap.serializer(), text))
    }
}

/**
 * How much each muscle was worked over some days, in sets, as the iPhone counts it
 * (`MuscleVolume.swift`): a done set counts once for the muscles its exercise works most and
 * half for the ones that help, as its page's muscle map shows them. Warm-ups don't count, so
 * [counts] leaves them out (see `Stats.exerciseSets`).
 */
class MuscleVolume private constructor(counted: List<Pair<MuscleWork?, Int>>) {
    val sets: Map<Muscle, Double>
    val totalSets: Int

    constructor(counts: List<ExerciseSetCount> = emptyList(), muscles: ExerciseMuscles? = null) :
        this(counts.map { muscles?.work(it.exercise) to it.sets })

    init {
        val sets = HashMap<Muscle, Double>()
        var total = 0
        for ((work, count) in counted) {
            total += count
            if (work == null) continue
            for (muscle in work.primary) sets[muscle] = (sets[muscle] ?: 0.0) + count
            for (muscle in work.secondary) sets[muscle] = (sets[muscle] ?: 0.0) + count / 2.0
        }
        this.sets = sets
        totalSets = total
    }

    companion object {
        /** A workout's muscles, from its done sets, leaving out warm-ups, as its summary shows them. */
        fun of(doneSets: List<DoneSet>, muscles: ExerciseMuscles?): MuscleVolume =
            MuscleVolume(doneSets.filter { it.type != SetType.Warmup }.map { muscles?.work(it.libraryKey, it.target) to 1 })
    }

    /** The muscles worked, most sets first, then in [Muscle]'s order. */
    val ranked: List<Muscle>
        get() = sets.keys.sortedWith(compareByDescending<Muscle> { sets.getValue(it) }.thenBy { it.ordinal })

    /**
     * For the muscle map: the muscles with at least half the sets of the most worked one as
     * primary, in olive, and the others that were worked as secondary, lighter.
     */
    val shading: MuscleWork
        get() {
            val top = sets.values.maxOrNull() ?: 0.0
            val ranked = ranked
            return MuscleWork(
                primary = ranked.filter { sets.getValue(it) >= top / 2 },
                secondary = ranked.filter { sets.getValue(it) < top / 2 },
            )
        }
}
