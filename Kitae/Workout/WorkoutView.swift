// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The workout in progress: every exercise's sets, the rest timer and Finish.
struct WorkoutView: View {
    @Environment(AppModel.self) private var model
    let workout: Workout

    @FocusState private var focus: SetField?
    @State private var optionsFor: SetOptionsTarget?
    @State private var finishQuestion: FinishQuestion?
    @State private var confirmsDiscard = false
    /// The workout's plan, being edited over the workout.
    @State private var editing: PlanEditor?

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    ForEach(workout.exercises) { exercise in
                        exerciseSection(exercise)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 16)
            }
            // Tapping anywhere but a field finishes typing. Fields and buttons take their own taps.
            .onTapGesture { focus = nil }
            .scrollDismissesKeyboard(.interactively)
            .scrollIndicators(.hidden)
            .background(Color.paper)
            .safeAreaBar(edge: .bottom) {
                // The bottom of the safe area sits on top of the keyboard while one is up.
                if focus == nil {
                    bottomBar
                } else {
                    keyboardBar
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { mainToolbar }
            .confirmationDialog("Discard workout?", isPresented: $confirmsDiscard, titleVisibility: .visible) {
                Button("Discard workout", role: .destructive) { model.discardWorkout() }
            } message: {
                Text("This workout and its sets will be removed. Your plan stays the same.")
            }
            .navigationDestination(item: $editing) { editor in
                PlanEditView(editor: editor)
            }
            .appRoutes()
        }
        .sheet(item: $optionsFor) { target in
            SetOptionsSheet(workout: workout, target: target, defaultRestSeconds: model.defaultRestSeconds)
        }
        .sensoryFeedback(.success, trigger: workout.doneCount) { old, new in new > old }
        .sensoryFeedback(.warning, trigger: workout.restsFinished)
        .task { await openDebugScreen() }
    }

    // MARK: Exercises and sets

    private func exerciseSection(_ exercise: WorkoutExercise) -> some View {
        let labels = setLabels(for: exercise.sets.map(\.type))
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    NavigationLink(value: AppRoute.exercise(exercise.id)) {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(exercise.exercise.name)
                                .font(.kitaeDisplay(26, relativeTo: .title2))
                                .foregroundStyle(.olive)
                                .multilineTextAlignment(.leading)
                            Image(systemName: "chevron.forward")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(.inkFaint)
                        }
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityHint("Shows how to do it and how it's gone")
                    if let work = ExerciseMuscles.work(for: exercise.exercise) {
                        MuscleLine(work: work)
                    }
                }
                Spacer()
                Button {
                    focus = nil
                    workout.addSet(to: exercise.id)
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 15, weight: .bold))
                        .frame(width: 36, height: 36)
                        .background(.oliveSoft, in: .circle)
                        .foregroundStyle(.onOliveSoft)
                        .frame(width: 44, height: 44)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Add a set to \(exercise.exercise.name)")
            }
            .padding(.leading, 8)

            ForEach(Array(exercise.sets.enumerated()), id: \.element.id) { index, set in
                SetRow(
                    set: set,
                    label: labels[index],
                    formatter: model.weightFormatter,
                    focus: $focus,
                    onReps: { reps in workout.update(set.id) { $0.reps = reps } },
                    onWeight: { kg in workout.update(set.id) { $0.weightKg = kg } },
                    onToggleDone: {
                        focus = nil
                        workout.toggleDone(set.id)
                    },
                    onOptions: {
                        focus = nil
                        optionsFor = SetOptionsTarget(id: set.id)
                    }
                )
            }
        }
    }

    // MARK: Bottom bar

    private var bottomBar: some View {
        WorkoutBottomLayout {
            if workout.rest.isActive {
                RestTimerBar(workout: workout)
                    .transition(.move(edge: .leading).combined(with: .opacity))
            }
            Button {
                startFinishing()
            } label: {
                // Just the checkmark when the rest timer leaves too little room for the word.
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) {
                        Text("Finish")
                        finishCheckmark
                    }
                    .padding(.horizontal, 8)
                    finishCheckmark
                }
                .font(.kitaeMono(16, relativeTo: .headline, bold: true))
                .frame(maxWidth: .infinity, minHeight: 58)
            }
            .buttonStyle(MauveButtonStyle())
            .accessibilityLabel("Finish")
            .finishQuestions($finishQuestion, workout: workout) { mode, addNewSets in
                model.finishWorkout(mode, addNewSets: addNewSets)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .animation(.snappy, value: workout.rest.isActive)
    }

    private var finishCheckmark: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 15, weight: .bold))
    }

    // MARK: Toolbars

    @ToolbarContentBuilder
    private var mainToolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button("Minimize workout", systemImage: "chevron.down") {
                model.isWorkoutPresented = false
            }
        }
        ToolbarItem(placement: .principal) {
            VStack(spacing: 0) {
                Text(workout.dayName)
                    .font(.kitaeDisplay(24, relativeTo: .headline))
                    .foregroundStyle(.ink)
                HStack(spacing: 0) {
                    Text(timerInterval: workout.startedAt...Date.distantFuture, countsDown: false)
                    Text(" · \(workout.doneCount)/\(workout.setCount) sets")
                }
                .font(.kitaeMono(12, relativeTo: .caption))
                .foregroundStyle(.inkSoft)
            }
            .accessibilityElement(children: .combine)
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            if let planID = workout.planID {
                Button("Edit \(workout.dayName)", systemImage: "pencil") {
                    editing = model.planEditor(for: PlanEditRoute(planID: planID, dayID: workout.dayID))
                }
            }
            Menu("More", systemImage: "ellipsis") {
                Button("Discard workout", systemImage: "trash", role: .destructive) {
                    confirmsDiscard = true
                }
            }
        }
    }

    /// Steps for the field being typed in, and Done, just above the keyboard.
    private var keyboardBar: some View {
        HStack(spacing: 8) {
            HStack(spacing: 0) {
                switch focus {
                case .weight(let id)?:
                    ForEach(model.weightFormatter.unit.steps, id: \.self) { step in
                        stepButton((step < 0 ? "−" : "+") + stepNumber(abs(step))) { changeWeight(id, by: step) }
                            .accessibilityLabel("\(step < 0 ? "Minus" : "Plus") \(stepNumber(abs(step))) \(model.weightFormatter.unit.symbol)")
                    }
                case .reps(let id)?:
                    stepButton("−1") { workout.update(id) { $0.reps = max($0.reps - 1, 0) } }
                        .accessibilityLabel("One rep less")
                    stepButton("+1") { workout.update(id) { $0.reps += 1 } }
                        .accessibilityLabel("One rep more")
                case nil:
                    EmptyView()
                }
            }
            .padding(.horizontal, 4)
            .glassEffect(.regular.interactive(), in: .capsule)

            Spacer(minLength: 0)

            Button {
                focus = nil
            } label: {
                Text("Done")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.olive)
                    .padding(.horizontal, 20)
                    .frame(minHeight: 48)
                    .contentShape(.capsule)
            }
            .buttonStyle(.plain)
            .glassEffect(.regular.interactive(), in: .capsule)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private func stepButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.kitaeMono(15, relativeTo: .body))
                .foregroundStyle(.ink)
                .frame(minWidth: 52, minHeight: 48)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    /// 5 or 2.5, written with the decimal separator from Settings.
    private func stepNumber(_ size: Double) -> String {
        if size == size.rounded() { return "\(Int(size))" }
        let formatter = model.weightFormatter
        let separator = formatter.decimals.separator(for: formatter.locale)
        return "\(Int(size))\(separator)\(Int((size * 10).rounded()) % 10)"
    }

    private func changeWeight(_ id: WorkoutSet.ID, by step: Double) {
        let unit = model.weightFormatter.unit
        workout.update(id) { set in
            let shown = unit.fromKilograms(set.weightKg)
            set.weightKg = unit.toKilograms(max(shown + step, 0))
        }
    }

    // MARK: Finishing

    /// Asks what to do with sets that weren't done or were added, or finishes straight away.
    private func startFinishing() {
        if let question = FinishQuestion.first(for: workout, startingAnotherDay: false) {
            finishQuestion = question
        } else {
            model.finishWorkout(.keepSkipped, addNewSets: false)
        }
    }

    // MARK: Debug

    private func openDebugScreen() async {
        #if DEBUG
        DebugLaunch.note("workout appeared, screen \(DebugLaunch.screen ?? "none")")
        guard let screen = DebugLaunch.screen, DebugLaunch.consume(screen) else { return }
        try? await Task.sleep(for: .milliseconds(700))
        let firstUnfinished = workout.exercises.flatMap(\.sets).first { !$0.isDone }
        DebugLaunch.note("opening \(screen), first unfinished set \(firstUnfinished?.id ?? -1)")
        switch screen {
        case "typing":
            if let set = firstUnfinished {
                workout.update(set.id) { $0.weightKg = 18.75 }
                focus = .weight(set.id)
            }
        case "finish":
            startFinishing()
        case "options":
            if let set = firstUnfinished { optionsFor = SetOptionsTarget(id: set.id) }
        case "workoutedit":
            if let planID = workout.planID {
                editing = model.planEditor(for: PlanEditRoute(planID: planID, dayID: workout.dayID))
            }
        default:
            break
        }
        #endif
    }
}
