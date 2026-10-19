# Exercise animations

`Kitae/Exercises/Motion/ExerciseMotions.json` holds each exercise's animation: its start and end
poses and the gear it uses, keyed by the exercises' library keys (Seed.json's `key`, the
database's `exercises.libraryKey`). `make.js` writes it (edit the
poses there, not in the JSON). `sheet.js` and `zoom.js` draw it as pictures, so poses can be
checked without building the app. They draw with `motion.js`, which matches `FigureRig.swift` and
`FigurePainter.swift` step for step: a change to one needs the same change in the other.

The figure takes its muscles from the muscle map below (`motion.js` reads
`ExerciseMuscles.json` from the repository): the ones an exercise works most in olive, the ones
that help in a lighter olive, drawn first so the olive ones stay on top where they meet. It has no
shapes of its own for the upper back and the abductors, so its traps and glutes show them; the
adductors aren't seen.

    npm install          # only needed for the pictures
    node make.js
    node check.js ../../Kitae/Exercises/Motion/ExerciseMotions.json
    node sheet.js ../../Kitae/Exercises/Motion/ExerciseMotions.json sheet.png light barbell-bench-press,barbell-incline-bench-press
    node zoom.js ../../Kitae/Exercises/Motion/ExerciseMotions.json zoom.png barbell-bench-press:0 barbell-bench-press:0.55 barbell-bench-press:still

`check.js` runs every loop and reports limbs that stretch, hands that can't reach what they hold
and anything below the floor (a bar trailing straight arms mid-lift is fine). `sheet.js` draws a
row per exercise (all of them, or the keys given, in that order): the start, the middle of the move,
the end and the Reduce Motion picture, in `light` or `dark`. `zoom.js` draws chosen moments,
`key:t` with `t` from 0 to 1 or `still`, at twice the size.

## Poses

The figure faces right, 190 units tall, with the floor at y = 0 and y growing downward. A pose
gives the hip, the spine's `lean` (0 upright, 90 face down, -90 face up), and either an ankle the
leg reaches for or `thigh` and `shin` angles (0 up, 90 forward, 180 down). Arms either `swing`
(`upper` degrees forward from hanging and `bend` at the elbow) or reach for a `hand` point, in the
scene or on the body (`on: "torso"`: along the spine, then out the front). `scale` and
`upperLength` shorten arms that point toward the viewer. `curl` bends the spine above the waist
and `shrug` lifts the shoulders. The far arm and leg copy the near ones a little behind them,
unless `farArm`, `farAnkle` or `farThigh` and `farShin` say otherwise. A kneeling pose can pin its
knee (`kneeAt`) so the hip follows the thigh's angle.

Movements out to the sides use the front view (`view: "front"`): the figure faces the viewer, and
angles work as in the side view for the arm and leg on the right of the picture, which the other
side mirrors unless `otherArm` says otherwise. `turn` turns the whole figure (a side plank),
`lean` bends it sideways at the waist, `torsoLength`, `thighLength`, `shinLength`, `upperLength`
and `foreLength` shorten parts that point toward the viewer, `headHeight` lowers the head for a
bent-over body, and `legsFront` draws sitting legs over the body.

## Muscle map

`Kitae/Exercises/Muscles/ExerciseMuscles.json` holds the muscle map under an exercise's
animation: a body from the front and from the back, each muscle a shape on it, and for every
starter exercise the muscles it works most (primary) and the ones that help (secondary).
`muscles.js` writes it; edit the shapes and the exercises' muscles there. `muscle-sheet.js` draws
maps the way the app does, in its colours:

    node muscles.js
    node muscle-sheet.js ../../Kitae/Exercises/Muscles/ExerciseMuscles.json muscles.png light barbell-bench-press,barbell-deadlift
    node muscle-sheet.js ../../Kitae/Exercises/Muscles/ExerciseMuscles.json muscles.png dark all-muscles

The picture is 100 units wide and 222 tall, facing the viewer, with y growing downward. Each
shape is a list of points that a smooth closed curve (Catmull-Rom) passes through, written out as
cubic Béziers, given for the right half of the picture and mirrored for the left. Muscles are
drawn over the body's outline with a thin gap round each. People's own exercises show their
muscle group instead (`MuscleGroup.muscles` in Swift). Changing an exercise's muscles here
changes its animation too.
