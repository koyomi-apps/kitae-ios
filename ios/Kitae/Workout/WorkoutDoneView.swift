// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Which workout's summary is showing.
struct FinishedWorkout: Identifiable, Hashable {
    let id: Int64
}

/// After a workout: its day, how long it took, its sets, the energy it used, the records it beat
/// (unless Settings leave them out) and the muscles it worked. It opens when a workout with a set
/// done is finished, unless Settings turn it off.
struct WorkoutDoneView: View {
    let workoutID: Int64

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var summary: WorkoutSummary?
    @State private var sets: [DoneSet] = []
    @State private var bests: [NewBest] = []

    var body: some View {
        NavigationStack {
            ScrollView {
                if let summary {
                    VStack(alignment: .leading, spacing: 14) {
                        header(summary)
                        tiles(summary)
                        if model.activeKilocalories(summary, sets: sets) != nil {
                            energyNote(hasHeight: model.bodyLog.profile.heightCm != nil)
                        }
                        if !bests.isEmpty {
                            NewBestsCard(bests: bests, formatter: model.weightFormatter)
                        }
                        MuscleVolumeCard(
                            subtitle: "This workout",
                            volume: MuscleVolume(doneSets: sets),
                            formatter: model.weightFormatter
                        )
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
            .scrollIndicators(.hidden)
            .background(Color.paper)
            .safeAreaInset(edge: .bottom) {
                Button {
                    dismiss()
                } label: {
                    Text("Done")
                        .frame(maxWidth: .infinity, minHeight: 58)
                }
                .buttonStyle(MauveButtonStyle())
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .padding(.bottom, 8)
                .background(Color.paper)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .task { load() }
    }

    private func header(_ summary: WorkoutSummary) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Caption(".workout done", size: 14)
            Text(summary.title)
                .font(.kitaeDisplay(72))
                .foregroundStyle(.olive)
                .lineLimit(2)
                .minimumScaleFactor(0.4)
                // Darker Grotesque's line height leaves a lot of air at this size.
                .padding(.top, -12)
                .padding(.bottom, -8)
                .padding(.leading, -2)
                .accessibilityAddTraits(.isHeader)
            Caption(HistoryDates.long(summary.date, today: EpochDay.of(.now)), size: 14)
        }
        .padding(.horizontal, 8)
        .padding(.top, 28)
        .padding(.bottom, 10)
    }

    private func tiles(_ summary: WorkoutSummary) -> some View {
        let kilocalories = model.activeKilocalories(summary, sets: sets)
        return HStack(spacing: 10) {
            DoneTile(title: "Time", value: summary.minutes.map { durationLabel(minutes: $0) } ?? "–")
            DoneTile(title: "Sets", value: "\(summary.doneCount)/\(summary.setCount)")
            if let kilocalories {
                DoneTile(title: "Energy", value: "≈ \(roundedKilocalories(kilocalories))", unit: "kcal")
                    .accessibilityLabel("Energy, about \(roundedKilocalories(kilocalories)) kilocalories")
            } else {
                NavigationLink {
                    BodyView()
                } label: {
                    DoneTile(title: "Energy", value: "–", note: "Add your weight")
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens your weigh-ins, to count the energy your workouts use")
            }
        }
    }

    /// Under the figures, once there's an energy figure: what it's worked out from, that height
    /// makes it closer, and that it's an estimate all the same.
    private func energyNote(hasHeight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(hasHeight
                 ? "Energy is an estimate, from your sets, weight and height. Your tempo and range of motion change it too, and Kitae can't see those."
                 : "Energy is an estimate, from your sets and weight. Your height makes it closer.")
                .font(.footnote)
                .foregroundStyle(.inkSoft)
                .fixedSize(horizontal: false, vertical: true)
            if !hasHeight {
                NavigationLink {
                    BodyView()
                } label: {
                    HStack(spacing: 4) {
                        Text("Add your height")
                        Image(systemName: "chevron.right")
                            .imageScale(.small)
                    }
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.olive)
                    .frame(minHeight: 44)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens Body, for a closer energy estimate")
            }
        }
        .padding(.horizontal, 8)
    }

    private func load() {
        let id = workoutID
        let showsBests = model.settings.showsNewBests
        do {
            let loaded = try model.database.read { db -> (WorkoutSummary?, [DoneSet], [NewBest]) in
                guard let summary = try Sessions.summary(db, id: id) else { return (nil, [], []) }
                return (
                    summary,
                    try Sessions.doneSets(db, from: summary.date, to: summary.date)[id] ?? [],
                    showsBests ? try Exercises.newBests(db, workoutID: id) : []
                )
            }
            summary = loaded.0
            sets = loaded.1
            bests = loaded.2
        } catch {
            model.problem = "Couldn't read the workout. \(error.localizedDescription)"
        }
    }
}

/// The records the workout's sets beat, each with the best it beat.
private struct NewBestsCard: View {
    let bests: [NewBest]
    let formatter: WeightFormatter

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text("New bests")
                    .font(.kitaeDisplay(24, relativeTo: .title3))
                    .foregroundStyle(.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                Text(counted(bests.count, "record", "records"))
                    .font(.footnote)
                    .foregroundStyle(.inkSoft)
            }
            ForEach(bests) { best in
                row(best)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.card, in: .rect(cornerRadius: 24))
    }

    private func row(_ best: NewBest) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Image(systemName: "star.fill")
                .font(.footnote)
                .foregroundStyle(.olive)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(best.exercise.name)
                        .font(.callout.weight(.semibold))
                        .foregroundStyle(.ink)
                    Spacer(minLength: 8)
                    Text(value(best.set, of: best))
                        .font(.kitaeMono(15, relativeTo: .subheadline, bold: true))
                        .foregroundStyle(.ink)
                        .layoutPriority(1)
                }
                Text("\(best.record.name), was \(value(best.previous, of: best))")
                    .font(.footnote)
                    .foregroundStyle(.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// As the exercise's page shows its records: weight first for the heaviest set.
    private func value(_ set: SetDraft, of best: NewBest) -> String {
        let exercise = best.exercise
        let kilograms = exercise.weight(set.weightKg)
        switch best.record {
        case .heaviest, .leastHelp:
            return formatter.weightFirst(reps: set.reps, kilograms: kilograms, isIsometric: exercise.isIsometric)
        case .mostReps, .longestHold:
            return formatter.summary(reps: set.reps, kilograms: kilograms, isIsometric: exercise.isIsometric)
        }
    }
}

/// One of the summary's figures: Time, Sets and Energy.
private struct DoneTile: View {
    let title: String
    let value: String
    var unit: String?
    var note: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.kitaeDisplay(20, relativeTo: .headline))
                .foregroundStyle(.inkSoft)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.vertical, -2)
            Spacer(minLength: 0)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value)
                    .font(.kitaeMono(24, relativeTo: .title2, bold: true))
                    .tracking(-1)
                    .foregroundStyle(.ink)
                if let unit {
                    Text(unit)
                        .font(.kitaeMono(12, relativeTo: .caption))
                        .foregroundStyle(.inkSoft)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            if let note {
                Text(note)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.olive)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 88, alignment: .leading)
        .padding(14)
        .background(.card, in: .rect(cornerRadius: 22))
        .accessibilityElement(children: .combine)
    }
}
