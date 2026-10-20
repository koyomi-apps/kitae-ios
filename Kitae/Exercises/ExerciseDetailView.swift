// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Charts
import Foundation
import SwiftUI

/// What an exercise's page shows.
struct ExercisePage {
    var exercise: Exercise
    /// Newest first.
    var history: [ExerciseSession]
    var progress: ExerciseProgress
    var records: ExerciseRecords

    init(exercise: Exercise, history: [ExerciseSession]) {
        self.exercise = exercise
        self.history = history
        progress = ExerciseProgress(history: history, isIsometric: exercise.isIsometric, usesWeight: exercise.usesWeight,
                                    assisted: exercise.isAssisted)
        records = ExerciseRecords(history: history, assisted: exercise.isAssisted)
    }
}

/// An exercise's page, as in the design: how it's gone lately, its best sets, how to do it,
/// and the workouts it was in.
struct ExerciseDetailView: View {
    let id: Exercise.ID

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var page: ExercisePage?
    @State private var hasLoaded = false
    @State private var isEditing = false

    /// How many of the exercise's workouts are listed.
    private static let recentCount = 10

    var body: some View {
        ScrollView {
            if let page {
                VStack(alignment: .leading, spacing: 0) {
                    header(page.exercise)
                    if page.progress.points.count > 1 {
                        progressCard(page)
                            .padding(.top, 24)
                    }
                    if !page.history.isEmpty {
                        recordTiles(page)
                            .padding(.top, page.progress.points.count > 1 ? 10 : 24)
                    }
                    howTo(page.exercise)
                        .padding(.top, 32)
                    recent(page)
                        .padding(.top, 32)
                }
                .padding(.bottom, 24)
            }
        }
        .scrollIndicators(.hidden)
        .background(Color.paper)
        .overlay {
            if hasLoaded, page == nil {
                ContentUnavailableView("This exercise no longer exists", systemImage: "dumbbell")
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if page != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Edit") { isEditing = true }
                }
            }
        }
        .sheet(isPresented: $isEditing) {
            if let exercise = page?.exercise {
                ExerciseEditor(exercise: exercise, onDelete: { dismiss() })
                    .environment(model)
            }
        }
        .task(id: model.historyRevision) { load() }
    }

    // MARK: Header

    private func header(_ exercise: Exercise) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(exercise.name)
                .font(.kitaeDisplay(54))
                .foregroundStyle(.olive)
                .lineLimit(3)
                .minimumScaleFactor(0.5)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 8) {
                chip(exercise.muscle?.name ?? exercise.target)
                chip("rest \(restSetting(seconds: exercise.restSeconds ?? model.defaultRestSeconds))")
                if exercise.isIsometric {
                    chip("held")
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 8)
    }

    private func chip(_ text: String) -> some View {
        Text(text)
            .font(.kitaeMono(13, relativeTo: .footnote))
            .foregroundStyle(.ink)
            .padding(.horizontal, 12)
            .frame(minHeight: 30)
            .background(.field, in: .capsule)
    }

    // MARK: Progress

    private func progressCard(_ page: ExercisePage) -> some View {
        let progress = page.progress
        let values = progress.points.map { display($0.value, progress.measure) }
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Caption(caption(progress.measure), size: 12)
                Spacer(minLength: 8)
                Text(changeLabel(progress))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.olive)
            }
            ProgressChart(values: values, minimumStep: progress.measure.isWeight ? 0.5 : 1) { value in
                axisLabel(value, progress.measure)
            }
            .frame(height: 132)
            .accessibilityElement()
            .accessibilityLabel(chartDescription(progress))
            if let first = progress.points.first, let last = progress.points.last {
                HStack {
                    Text(HistoryDates.dayAndMonth(first.date))
                    Spacer()
                    Text(HistoryDates.dayAndMonth(last.date))
                }
                .font(.kitaeMono(10, relativeTo: .caption2))
                .foregroundStyle(.inkSoft)
                .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 12)
        .background(.card, in: .rect(cornerRadius: 24))
        .padding(.horizontal, 16)
    }

    private func caption(_ measure: ExerciseProgress.Measure) -> String {
        ".\(measureName(measure))"
    }

    private func measureName(_ measure: ExerciseProgress.Measure) -> String {
        switch measure {
        case .weight: "heaviest set"
        case .help: "least help"
        case .reps: "most reps"
        case .seconds: "longest hold"
        }
    }

    /// A value in the unit people see: pounds when Settings says so.
    private func display(_ value: Double, _ measure: ExerciseProgress.Measure) -> Double {
        measure.isWeight ? model.weightFormatter.unit.fromKilograms(value) : value
    }

    private func axisLabel(_ value: Double, _ measure: ExerciseProgress.Measure) -> String {
        if measure == .seconds { return "\(Int(value.rounded()))s" }
        if value == value.rounded() { return "\(Int(value))" }
        return model.weightFormatter.number(value)
    }

    /// "+7.5 kg since August", or on an assisted machine "5 kg less help since August".
    private func changeLabel(_ progress: ExerciseProgress) -> String {
        guard let first = progress.points.first, let last = progress.points.last else { return "" }
        let since = sinceLabel(first.date, last.date)
        let change = display(progress.change, progress.measure)
        guard abs(change) >= 0.01 else { return "No change since \(since)" }
        let amount: String
        switch progress.measure {
        case .weight, .help:
            amount = "\(model.weightFormatter.number(abs(change))) \(model.weightFormatter.unit.symbol)"
        case .reps:
            amount = counted(Int(abs(change).rounded()), "rep", "reps")
        case .seconds:
            amount = "\(Int(abs(change).rounded()))s"
        }
        if progress.measure == .help {
            return "\(amount) \(change < 0 ? "less" : "more") help since \(since)"
        }
        let sign = change > 0 ? "+" : "−"
        return "\(sign)\(amount) since \(since)"
    }

    /// "August", or "Oct 3" when the chart starts in the month it ends.
    private func sinceLabel(_ first: Int, _ last: Int) -> String {
        let start = HistoryDates.yearAndMonth(first)
        if start == HistoryDates.yearAndMonth(last) {
            return HistoryDates.dayAndMonth(first)
        }
        return HistoryDates.monthTitle(year: start.year, month: start.month, today: EpochDay.of(.now))
    }

    /// "Your heaviest set went from 15 kg on Aug 3 to 22.5 kg on Oct 7".
    private func chartDescription(_ progress: ExerciseProgress) -> String {
        guard let first = progress.points.first, let last = progress.points.last else { return "" }
        let unit = progress.measure.isWeight ? " \(model.weightFormatter.unit.symbol)" : ""
        let from = axisLabel(display(first.value, progress.measure), progress.measure)
        let to = axisLabel(display(last.value, progress.measure), progress.measure)
        return "Your \(measureName(progress.measure)) went from \(from)\(unit) on \(HistoryDates.dayAndMonth(first.date)) to \(to)\(unit) on \(HistoryDates.dayAndMonth(last.date))"
    }

    // MARK: Best sets

    private func recordTiles(_ page: ExercisePage) -> some View {
        let formatter = model.weightFormatter
        let isIsometric = page.exercise.isIsometric
        let records = page.records
        return HStack(spacing: 10) {
            switch page.progress.measure {
            case .weight, .help:
                if let best = records.best {
                    let isHelp = page.progress.measure == .help
                    tile(
                        isHelp ? "Least help" : "Heaviest set",
                        formatter.weightFirst(reps: best.reps, kilograms: best.weightKg, isIsometric: isIsometric),
                        note: isHelp ? nil : bodyWeightNote(best.weightKg, on: records.bestDate)
                    )
                }
                if let most = records.mostReps {
                    tile(isIsometric ? "Longest hold" : "Most reps", formatter.summary(reps: most.reps, kilograms: most.weightKg, isIsometric: isIsometric))
                }
            case .reps, .seconds:
                if let most = records.mostReps {
                    tile(isIsometric ? "Longest hold" : "Most reps",
                         formatter.summary(reps: most.reps, kilograms: page.exercise.weight(most.weightKg), isIsometric: isIsometric))
                }
                tile("Workouts", "\(records.workoutCount)")
            }
        }
        .padding(.horizontal, 16)
    }

    private func tile(_ title: String, _ value: String, note: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.footnote)
                .foregroundStyle(.inkSoft)
            Text(value)
                .font(.kitaeMono(17, relativeTo: .headline, bold: true))
                .foregroundStyle(.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            if let note {
                Text(note)
                    .font(.footnote)
                    .foregroundStyle(.olive)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
        .padding(.horizontal, 16)
        .background(.card, in: .rect(cornerRadius: 22))
        .accessibilityElement(children: .combine)
    }

    /// "1.25 × body weight": a weight against the body weight of the day it was lifted, once
    /// people have weighed in.
    private func bodyWeightNote(_ kilograms: Double, on day: Int?) -> String? {
        guard kilograms > 0, let day, let body = model.bodyLog.weightKg(on: day), body > 0 else { return nil }
        return "\(model.weightFormatter.number(kilograms / body)) × body weight"
    }

    // MARK: How to do it

    private func howTo(_ exercise: Exercise) -> some View {
        let steps = exercise.steps
        return VStack(alignment: .leading, spacing: 14) {
            Text("How to do it")
                .font(.kitaeDisplay(28, relativeTo: .title2))
                .foregroundStyle(.ink)
                .accessibilityAddTraits(.isHeader)
            if let motion = ExerciseMotions.motion(for: exercise) {
                // Lined up with the cards above, 16 points from the edges.
                ExerciseMotionView(motion: motion)
                    .padding(.horizontal, -8)
            }
            if let work = ExerciseMuscles.work(for: exercise) {
                MuscleMapView(work: work)
                    .padding(.horizontal, -8)
                    .padding(.bottom, 4)
            }
            if steps.isEmpty {
                Text("No instructions yet. You can write your own with Edit.")
                    .font(.callout)
                    .foregroundStyle(.inkSoft)
            }
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .top, spacing: 12) {
                    Text("\(index + 1)")
                        .font(.kitaeMono(13, relativeTo: .footnote, bold: true))
                        .foregroundStyle(.onOliveSoft)
                        .frame(width: 26, height: 26)
                        .background(.oliveSoft, in: .circle)
                        .accessibilityHidden(true)
                    Text(step)
                        .font(.callout)
                        .foregroundStyle(.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 2)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Step \(index + 1): \(step)")
            }
            if let url = exercise.referenceURL {
                Link(destination: url) {
                    Label("See it done", systemImage: "arrow.up.right")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.olive)
                }
                .padding(.top, 4)
            }
        }
        .padding(.horizontal, 24)
    }

    // MARK: Workouts

    @ViewBuilder
    private func recent(_ page: ExercisePage) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Caption(".recent workouts")
                .padding(.leading, 24)
            if page.history.isEmpty {
                Text("Sets you do of this exercise show up here.")
                    .font(.callout)
                    .foregroundStyle(.inkSoft)
                    .padding(.horizontal, 24)
            } else {
                let sessions = Array(page.history.prefix(Self.recentCount))
                VStack(spacing: 0) {
                    ForEach(Array(sessions.enumerated()), id: \.element.id) { index, session in
                        NavigationLink(value: AppRoute.workout(session.id)) {
                            HStack(spacing: 10) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(sessionTitle(session))
                                        .font(.body.weight(.semibold))
                                        .foregroundStyle(.ink)
                                    Text(sessionSets(session, of: page.exercise))
                                        .font(.kitaeMono(12, relativeTo: .caption))
                                        .foregroundStyle(.inkSoft)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)
                                }
                                Spacer(minLength: 8)
                                Image(systemName: "chevron.forward")
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(.inkFaint)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .frame(minHeight: 60)
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                        if index < sessions.count - 1 {
                            Hairline()
                                .padding(.leading, 16)
                        }
                    }
                }
                .background(.card, in: .rect(cornerRadius: 24))
                .padding(.horizontal, 16)
            }
        }
    }

    /// "Wed, Oct 7 · Push A".
    private func sessionTitle(_ session: ExerciseSession) -> String {
        let date = HistoryDates.short(session.date, today: EpochDay.of(.now))
        guard let day = session.dayName else { return date }
        return "\(date) · \(day)"
    }

    /// "10 × 22.5 kg, 9 × 22.5 kg".
    private func sessionSets(_ session: ExerciseSession, of exercise: Exercise) -> String {
        session.sets
            .map { model.weightFormatter.summary(reps: $0.reps, kilograms: exercise.weight($0.weightKg), isIsometric: exercise.isIsometric) }
            .joined(separator: ", ")
    }

    private func load() {
        let id = id
        do {
            page = try model.database.read { db -> ExercisePage? in
                guard let exercise = try Exercises.exercise(db, id: id) else { return nil }
                return ExercisePage(exercise: exercise, history: try Exercises.history(db, exerciseID: id))
            }
        } catch {
            model.problem = "Couldn't read the exercise. \(error.localizedDescription)"
        }
        hasLoaded = true
    }
}

/// The line of an exercise's progress, with the area under it and a dot for each workout. The
/// Body page draws weigh-ins with it too, placed by their days.
struct ProgressChart: View {
    /// Oldest first, in the unit people see.
    let values: [Double]
    /// The smallest step between the axis's lines, 1 for reps and seconds.
    let minimumStep: Double
    /// Where each value sits along the line, like its day. Evenly spaced without them.
    var positions: [Double]?
    let label: (Double) -> String

    var body: some View {
        let scale = ChartScale(values: values, minimumStep: minimumStep)
        let xs = positions ?? values.indices.map(Double.init)
        let start = xs.first ?? 0
        Chart {
            ForEach(Array(values.enumerated()), id: \.offset) { index, value in
                AreaMark(
                    x: .value("Workout", xs[index]),
                    yStart: .value("Bottom", scale.lower),
                    yEnd: .value("Value", value)
                )
                .foregroundStyle(Color.olive.opacity(0.12))
                LineMark(x: .value("Workout", xs[index]), y: .value("Value", value))
                    .foregroundStyle(Color.olive)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                PointMark(x: .value("Workout", xs[index]), y: .value("Value", value))
                    .symbol {
                        if index == values.count - 1 {
                            Circle()
                                .fill(Color.olive)
                                .frame(width: 10, height: 10)
                        } else {
                            Circle()
                                .fill(Color.card)
                                .overlay(Circle().stroke(Color.olive, lineWidth: 2))
                                .frame(width: 7, height: 7)
                        }
                    }
            }
        }
        .chartXScale(domain: start...max(xs.last ?? start, start + 1))
        .chartXAxis(.hidden)
        .chartYScale(domain: scale.lower...scale.upper)
        .chartYAxis {
            AxisMarks(position: .leading, values: scale.ticks) { value in
                AxisGridLine()
                    .foregroundStyle(Color.rule)
                AxisValueLabel {
                    if let number = value.as(Double.self) {
                        Text(label(number))
                            .font(.kitaeMono(10, relativeTo: .caption2))
                            .foregroundStyle(.inkSoft)
                    }
                }
            }
        }
    }
}

/// Round numbers for a chart's axis: at most three steps of 1, 2, 2.5 or 5 times a power of
/// ten, so 15 to 22.5 kg has lines at 15, 17.5, 20 and 22.5, as in the design. A flat line sits
/// in the middle.
nonisolated struct ChartScale: Hashable, Sendable {
    var lower: Double
    var upper: Double
    var step: Double

    init(values: [Double], minimumStep: Double = 0) {
        let low = values.min() ?? 0
        let high = values.max() ?? 0
        var range = high - low
        if range < 0.001 {
            range = max(abs(high) / 2, 1)
        }
        let magnitude = pow(10, floor(log10(range / 3)))
        let candidates: [Double] = [1, 2, 2.5, 5, 10]
        let step = max(candidates.map { $0 * magnitude }.first { range / $0 <= 3 } ?? magnitude * 10, minimumStep)
        self.step = step
        var lower = (low / step).rounded(.down) * step
        var upper = (high / step).rounded(.up) * step
        if upper - lower < step {
            lower -= step
            upper += step
        }
        self.lower = low >= 0 ? max(lower, 0) : lower
        self.upper = upper
    }

    var ticks: [Double] {
        Array(stride(from: lower, through: upper + step / 2, by: step))
    }
}
