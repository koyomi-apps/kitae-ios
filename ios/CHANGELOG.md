# Changelog

What's new in each build of Kitae for iPhone. Builds go to [TestFlight](https://testflight.apple.com/join/GTKbw4Hg), and from 1.0 (2) each one is also on [GitHub's Releases page](https://github.com/koyomi-apps/kitae-ios/releases) as an IPA for sideloading.

## 1.0 (4) · 2026-10-22

- **94 more exercises,** for 169 in all. Every muscle group gets new ones: machine, cable, Smith machine and bodyweight versions of the classics, more rows, pull-downs, lunges and hip thrusts, forearm curls, a farmer's carry, and 15 for the core. Each has its own steps, muscle map and animated figure, and counts in the energy estimate. If you'd already made one of them yourself under the same name, it isn't added twice: yours gets the figure and the muscle map. Asked for in issue #6.
- **Exercises without weight.** Exercises done with your body alone, like sit-ups, push-ups and pull-ups, now log just reps (or seconds, for holds), with no weight field. A new "Uses weight" switch in an exercise's editor sets it for any exercise. Weights you'd already logged for them are kept but not shown, and come back if you turn the switch on again.
- **Notes.** Add a note to any exercise from its page, like your seat height or grip. It shows there and under the exercise's name during workouts, where you can tap it to change it.
- **New bests.** The summary after a workout lists the records you beat: an exercise's heaviest set, most reps or longest hold, with what it was before. You can turn it off in Settings › Workouts.
- **Recent exercises first.** When you add an exercise to a day, the five you've done most recently are at the top.
- **Muscles during a workout.** Under each exercise's name, the muscles it works most show beside an olive square, and the ones that help beside a lighter one. The animated figures now shade the helping muscles lighter too, as the muscle map does.
- **Assisted machines.** On Assisted Pull-ups and Assisted Dips, the weight you log is the machine's help, so less help counts as progress: the chart and best sets follow the least help.
- **The Exercises title stays sharp.** Scrolling the Exercises list no longer smears the title into a blur. Reported in issue #8.
- Kitae on Android can't restore backups from this build until it's updated to match. Its backups still restore here.

## 1.0 (3) · 2026-10-17

- **The muscles each exercise works.** On an exercise's page, a body from the front and the back shows the muscles it works most in olive and the ones that help in a lighter shade, with their names. Your own exercises show their muscle group. Asked for in issue #5.
- **Body.** A new page in You for your weight and body fat, with a chart and your lean and fat mass, plus your height, date of birth, sex and activity level. All of it is optional, and it stays on your iPhone and in Kitae's backups. Asked for in issue #7.
- **The energy your workouts use.** Once you've added your weight, Kitae estimates it from the sets you did: each exercise, its reps and weight, and its set type, with sets to failure, drop sets and rest-pause sets counting more. Your height makes it closer. You'll see it on Today after a workout, in History and on a workout's page, and You adds up the week. It's still an estimate: Kitae can't see your tempo or range of motion.
- **A summary after each workout,** with how long it took, your sets, the energy it used and the muscles it worked. You can turn it off in Settings › Workouts.
- **This week on You:** your workouts, time and energy so far, and a map of the muscles you've worked in the last 7 days, the most worked in olive.
- An exercise's heaviest set also shows against your body weight, like "1.25 × body weight".

## 1.0 (2) · 2026-10-16

- **How to do it, shown.** Every starter exercise's page now has a figure doing the movement above its steps, with the muscles it works in olive. Movements out to the sides, like lateral raises, are shown from the front. With Reduce Motion on, it shows the start and the end of the movement together.
- **A welcome on first launch.** Before you choose a plan, Kitae asks how you'd like it: text size, theme, kilograms or pounds, how decimals are written, the default rest, and backups. You can also restore a backup from another iPhone or from Kitae on Android. Asked for in issue #4.
- **Text size.** Kitae can follow your iPhone's text size or use its own, from a slider in Settings › Appearance. Asked for in issue #4.
- **Rest timer on smaller iPhones.** The rest timer's time is no longer cut short, as in "2…", on iPhones narrower than the Max models or with bigger text. When it can't fit beside Finish, it moves above it. Reported in issue #3.
- Units, decimals and theme are now choices side by side that follow the text size.
- Settings' Source code link opens GitHub, where bug reports and requests now go.

## 1.0 (1) · 2026-10-11

The first build on TestFlight, with:

- **Today:** the day up next or in progress, and starting a workout.
- **Workouts:** reps and weight typed straight into each set, with steps on the keyboard; warm-ups and other set types; a rest timer that starts when you tick a set, with pause, on the Lock Screen and in the Dynamic Island, and a "Rest over" alert; and finishing with a choice of what to do with skipped sets.
- **Plans:** the starter Push Pull Leg plan, and making and changing your own days, exercises and sets.
- **History** of your workouts, **Exercises** with a page for each (its progress, best sets and how to do it), and **You**, with your totals and the last 16 weeks of training.
- **Backups** that Kitae on Android can restore, and restoring Android's; Reset Kitae; kilograms or pounds; light and dark.
