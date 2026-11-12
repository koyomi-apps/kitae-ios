// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The first tab: the day that's up next in the current plan, and a button to start it.
struct TodayView: View {
    @Environment(AppModel.self) private var model
    @State private var line = TodayLines.random()
    /// The day to start once the workout in progress is finished.
    @State private var switchingTo: TrainingDay?
    @State private var finishQuestion: FinishQuestion?
    /// The plan being edited, pushed over Today.
    @State private var editing: PlanEditor?
    /// Workouts finished today, and their done sets for the energy they used.
    @State private var doneToday: [WorkoutSummary] = []
    @State private var doneTodaySets: [Int64: [DoneSet]] = [:]

    var body: some View {
        NavigationStack {
            ScrollView {
                if let day = model.selectedDay {
                    VStack(alignment: .leading, spacing: 0) {
                        header(day)
                        dayChips
                            .padding(.top, 28)
                        startButton(day)
                            .padding(.top, 18)
                            .padding(.horizontal, 24)
                        Text(line)
                            .font(.kitaeMono(12, relativeTo: .caption))
                            .foregroundStyle(.inkSoft)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 14)
                        if !doneToday.isEmpty {
                            doneTodayList
                                .padding(.top, 36)
                                .padding(.horizontal, 16)
                        }
                        if !day.exercises.isEmpty {
                            exerciseList(day)
                                .padding(.top, 44)
                                .padding(.horizontal, 16)
                        }
                    }
                    .padding(.bottom, 24)
                } else if model.currentPlan == nil {
                    ContentUnavailableView {
                        Label("No plan yet", systemImage: "calendar")
                    } description: {
                        Text("Choose a plan to follow, and Kitae lines up its days for you.")
                    } actions: {
                        Button("Choose a plan") { model.isChoosingPlan = true }
                            .buttonStyle(.borderedProminent)
                    }
                    .padding(.top, 80)
                } else {
                    ContentUnavailableView {
                        Label("No days yet", systemImage: "calendar")
                    } description: {
                        Text("Add a training day to this plan to start.")
                    } actions: {
                        Button("Edit plan") { edit(nil) }
                            .buttonStyle(.borderedProminent)
                    }
                    .padding(.top, 80)
                }
            }
            .scrollIndicators(.hidden)
            .background(Color.paper)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { planMenu }
                if let day = model.selectedDay {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Edit \(day.name)", systemImage: "pencil") {
                            edit(day)
                        }
                    }
                }
            }
            .navigationDestination(item: $editing) { editor in
                PlanEditView(editor: editor)
            }
            .appRoutes()
        }
        .task { await openDebugScreen() }
        .task(id: model.historyRevision) { loadDoneToday() }
    }

    private func header(_ day: TrainingDay) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Caption(status(for: day), size: 14)
            Text(day.name)
                .font(.kitaeDisplay(112))
                .foregroundStyle(.olive)
                .lineLimit(2)
                .minimumScaleFactor(0.4)
                // Darker Grotesque's line height leaves a lot of air at this size.
                .padding(.top, -22)
                .padding(.bottom, -12)
                .padding(.leading, -4)
                .accessibilityAddTraits(.isHeader)
            Caption(detail(for: day), size: 14)
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
    }

    private func isInProgress(_ day: TrainingDay) -> Bool {
        model.workout?.dayID == day.id
    }

    private func status(for day: TrainingDay) -> String {
        if isInProgress(day) { return ".in progress" }
        return model.hasDoneSets ? ".up next" : ".first session"
    }

    private func detail(for day: TrainingDay) -> String {
        if isInProgress(day), let workout = model.workout {
            return "\(workout.doneCount)/\(workout.setCount) sets done"
        }
        if day.exercises.isEmpty { return "no exercises yet" }
        return "\(counted(day.exercises.count, "exercise", "exercises")) · \(counted(day.setCount, "set", "sets"))"
    }

    private var dayChips: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(model.days) { day in
                    DayChip(title: day.name, isSelected: day.id == model.selectedDay?.id) {
                        model.pickedDayID = day.id
                    }
                }
            }
        }
        .contentMargins(.horizontal, 24, for: .scrollContent)
        .scrollIndicators(.hidden)
    }

    private func startButton(_ day: TrainingDay) -> some View {
        let continues = isInProgress(day)
        let isEmpty = day.exercises.isEmpty && !continues
        return Button {
            if isEmpty {
                edit(day)
            } else if let workout = model.workout, workout.dayID != day.id {
                switchingTo = day
                finishQuestion = FinishQuestion.first(for: workout, startingAnotherDay: true)
            } else {
                model.startWorkout(day)
            }
        } label: {
            HStack(spacing: 10) {
                Text(continues ? "Continue" : isEmpty ? "Edit \(day.name)" : "Start")
                Image(systemName: isEmpty ? "pencil" : "arrow.up.right")
                    .font(.system(size: 16, weight: .semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 58)
        }
        .buttonStyle(MauveButtonStyle())
        .finishQuestions(
            $finishQuestion,
            workout: model.workout,
            nextDayName: switchingTo?.name,
            onContinue: { model.isWorkoutPresented = true }
        ) { mode, addNewSets in
            guard let next = switchingTo else { return }
            model.finishWorkout(mode, addNewSets: addNewSets, showsSummary: false)
            model.startWorkout(next)
        }
    }

    /// Today's finished workouts: how long they took, their sets, and the energy they used.
    private var doneTodayList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Caption(".done today")
                .padding(.leading, 8)
            ForEach(doneToday) { workout in
                NavigationLink(value: AppRoute.workout(workout.id)) {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(workout.title)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.ink)
                            Text(workoutDetails(workout))
                                .font(.footnote)
                                .foregroundStyle(.inkSoft)
                        }
                        Spacer(minLength: 8)
                        if let kilocalories = model.activeKilocalories(workout, sets: doneTodaySets[workout.id] ?? []) {
                            VStack(alignment: .trailing, spacing: 0) {
                                Text("≈ \(roundedKilocalories(kilocalories))")
                                    .font(.kitaeMono(20, relativeTo: .title3, bold: true))
                                    .foregroundStyle(.olive)
                                Text("kcal")
                                    .font(.kitaeMono(11, relativeTo: .caption2))
                                    .foregroundStyle(.inkSoft)
                            }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("About \(roundedKilocalories(kilocalories)) kilocalories")
                        }
                        Image(systemName: "chevron.forward")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.inkFaint)
                    }
                    .padding(.horizontal, 18)
                    .frame(minHeight: 68)
                    .background(.card, in: .rect(cornerRadius: 24))
                    .contentShape(.rect(cornerRadius: 24))
                }
                .buttonStyle(.plain)
                .accessibilityHint("Shows its sets")
            }
        }
    }

    private func loadDoneToday() {
        let today = EpochDay.of(.now)
        let loaded = try? model.database.read { db in
            (try Sessions.finished(db, from: today, to: today), try Sessions.doneSets(db, from: today, to: today))
        }
        doneToday = loaded?.0 ?? []
        doneTodaySets = loaded?.1 ?? [:]
    }

    private func exerciseList(_ day: TrainingDay) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Caption(".exercises")
                .padding(.leading, 8)
            VStack(spacing: 0) {
                ForEach(Array(day.exercises.enumerated()), id: \.element.id) { index, item in
                    HStack {
                        Text(item.exercise.name)
                        Spacer()
                        Text(counted(item.sets.count, "set", "sets"))
                            .font(.kitaeMono(13, relativeTo: .footnote))
                            .foregroundStyle(.inkSoft)
                    }
                    .padding(.horizontal, 18)
                    .frame(minHeight: 54)
                    if index < day.exercises.count - 1 {
                        Hairline().padding(.leading, 18)
                    }
                }
            }
            .background(.card, in: .rect(cornerRadius: 24))
        }
    }

    /// Opens the plan editor on a day of the current plan.
    private func edit(_ day: TrainingDay?) {
        guard let plan = model.currentPlan else { return }
        editing = model.planEditor(for: PlanEditRoute(planID: plan.id, dayID: day?.id))
    }

    private var planSelection: Binding<Plan.ID?> {
        Binding(
            get: { model.currentPlan?.id },
            set: { id in if let id { model.selectPlan(id) } }
        )
    }

    private var planPicker: some View {
        Picker("Plan", selection: planSelection) {
            ForEach(model.plans) { plan in
                Text(plan.name).tag(Plan.ID?.some(plan.id))
            }
        }
        .disabled(model.workout != nil)
    }

    private var planMenu: some View {
        Menu {
            planPicker
            Section {
                Button("All plans", systemImage: "list.bullet") {
                    model.isChoosingPlan = true
                }
            }
        } label: {
            HStack(spacing: 6) {
                Text(model.currentPlan?.name ?? "Plans")
                    .fontWeight(.semibold)
                Image(systemName: "chevron.down")
                    .font(.footnote.weight(.bold))
            }
            .foregroundStyle(.ink)
        }
        .accessibilityLabel("Plan: \(model.currentPlan?.name ?? "none")")
    }
}

extension TodayView {
    /// `-KitaeScreen switch`: picks another day while the example workout is going and taps Start.
    /// `planedit`, `planset`, `addexercise` and `newday` open the plan editor on Today's day, and
    /// `firstday` opens it on a new plan.
    private func openDebugScreen() async {
        #if DEBUG
        if DebugLaunch.screen == "firstday", DebugLaunch.consume("firstday"),
           let id = try? model.createPlan(named: "Strength") {
            try? await Task.sleep(for: .milliseconds(500))
            editing = model.planEditor(for: PlanEditRoute(planID: id))
            return
        }
        if DebugLaunch.opensPlanEditor, DebugLaunch.consume("editor") {
            try? await Task.sleep(for: .milliseconds(500))
            edit(model.selectedDay)
            return
        }
        guard DebugLaunch.screen == "switch", DebugLaunch.consume("switch"), let workout = model.workout,
              let next = model.days.first(where: { $0.id != workout.dayID }) else { return }
        try? await Task.sleep(for: .milliseconds(700))
        model.pickedDayID = next.id
        switchingTo = next
        finishQuestion = FinishQuestion.first(for: workout, startingAnotherDay: true)
        #endif
    }
}

/// Short lines under the Start button, one per launch.
enum TodayLines {
    static let all = [
        "show up, tick it off",
        "forged one set at a time",
        "small plates add up",
        "rest, breathe, go again",
        "the next rep is the one that counts",
        "consistency beats intensity",
    ]

    static func random() -> String {
        all.randomElement() ?? all[0]
    }
}
