// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The second tab: every workout, newest first, a month at a time.
struct HistoryView: View {
    @Environment(AppModel.self) private var model
    @State private var months: [HistoryMonth] = []
    /// Each workout's done sets, for the energy it used.
    @State private var doneSets: [Int64: [DoneSet]] = [:]
    @State private var hasLoaded = false
    @State private var path: [AppRoute] = []
    @State private var deleting: WorkoutSummary?

    var body: some View {
        NavigationStack(path: $path) {
            List {
                ForEach(months) { month in
                    Section {
                        ForEach(month.workouts) { workout in
                            row(workout)
                        }
                    } header: {
                        monthHeader(month)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .listSectionSpacing(20)
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .overlay {
                if hasLoaded, months.isEmpty {
                    ContentUnavailableView {
                        Label("No workouts yet", systemImage: "clock.arrow.circlepath")
                    } description: {
                        Text("Workouts you do show up here, with every set.")
                    }
                }
            }
            .navigationTitle("History")
            .appRoutes()
            .confirmationDialog(
                "Delete this workout?",
                isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                titleVisibility: .visible,
                presenting: deleting
            ) { workout in
                Button("Delete workout", role: .destructive) {
                    model.deleteWorkout(workout.id)
                }
            } message: { workout in
                Text("\(workout.title) on \(HistoryDates.long(workout.date, today: today)) will be removed with its sets. Your plan stays the same.")
            }
        }
        .task(id: model.historyRevision) {
            load()
            await openDebugScreen()
        }
    }

    private var today: Int { EpochDay.of(.now) }

    private func monthHeader(_ month: HistoryMonth) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(HistoryDates.monthTitle(year: month.year, month: month.month, today: today))
                .font(.kitaeDisplay(26, relativeTo: .title2))
                .foregroundStyle(.ink)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            Text(month.totals)
                .font(.kitaeMono(12, relativeTo: .caption))
                .foregroundStyle(.inkSoft)
        }
        .textCase(nil)
        .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 8, trailing: 8))
    }

    private func row(_ workout: WorkoutSummary) -> some View {
        let isActive = workout.id == model.workout?.id
        return Button {
            if isActive {
                model.isWorkoutPresented = true
            } else {
                path.append(.workout(workout.id))
            }
        } label: {
            HStack(spacing: 14) {
                VStack(spacing: 0) {
                    Text(HistoryDates.dayOfMonth(workout.date))
                        .font(.kitaeMono(22, relativeTo: .title3, bold: true))
                        .foregroundStyle(.ink)
                    Text(HistoryDates.shortWeekday(workout.date))
                        .font(.kitaeMono(10, relativeTo: .caption2))
                        .tracking(1)
                        .foregroundStyle(.inkSoft)
                }
                .frame(width: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(workout.title)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(workout.isSkipped ? Color.inkSoft : Color.ink)
                    subtitle(workout, isActive: isActive)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.forward")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.inkFaint)
            }
            .frame(minHeight: 68)
            .contentShape(.rect)
        }
        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
        .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 14))
        .listRowBackground(Color.card)
        .accessibilityHint(isActive ? "Opens the workout" : "Shows its sets")
        .swipeActions(edge: .trailing) {
            if !isActive {
                Button("Delete", systemImage: "trash", role: .destructive) {
                    deleting = workout
                }
            }
        }
    }

    @ViewBuilder
    private func subtitle(_ workout: WorkoutSummary, isActive: Bool) -> some View {
        if isActive, let live = model.workout {
            Text(".in progress · \(live.doneCount)/\(live.setCount) sets")
                .font(.kitaeMono(12, relativeTo: .caption))
                .foregroundStyle(.olive)
        } else if workout.isSkipped {
            Text(".skipped")
                .font(.kitaeMono(12, relativeTo: .caption))
                .foregroundStyle(.inkSoft)
        } else {
            Text(workoutDetails(workout, kilocalories: model.activeKilocalories(workout, sets: doneSets[workout.id] ?? [])))
                .font(.footnote)
                .foregroundStyle(.inkSoft)
        }
    }

    private func load() {
        let activeID = model.workout?.id
        do {
            let (workouts, sets) = try model.database.read { db -> ([WorkoutSummary], [Int64: [DoneSet]]) in
                let workouts = try Sessions.history(db, activeID: activeID)
                let dates = workouts.map(\.date)
                return (workouts, try Sessions.doneSets(db, from: dates.min() ?? 0, to: dates.max() ?? 0))
            }
            months = historyMonths(workouts, activeID: activeID)
            doneSets = sets
        } catch {
            model.problem = "Couldn't read your workouts. \(error.localizedDescription)"
        }
        hasLoaded = true
    }

    /// `-KitaeScreen workoutdetail`: opens the newest finished workout.
    private func openDebugScreen() async {
        #if DEBUG
        guard DebugLaunch.screen == "workoutdetail", DebugLaunch.consume("workoutdetail"),
              let workout = months.lazy.flatMap(\.workouts).first(where: { $0.isFinished && $0.doneCount > 0 })
        else { return }
        try? await Task.sleep(for: .milliseconds(500))
        path = [.workout(workout.id)]
        #endif
    }
}
