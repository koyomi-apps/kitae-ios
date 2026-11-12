// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Charts
import SwiftUI

/// The fourth tab: the plan being followed, a few totals, this week's workouts and the energy
/// they used, the muscles worked in the last 7 days, the body, the last 16 weeks of training,
/// and Settings.
struct YouView: View {
    @Environment(AppModel.self) private var model
    @State private var totals = TrainingTotals()
    @State private var weeks: ActivityWeeks?
    /// This week's finished workouts, and their done sets.
    @State private var thisWeek: [WorkoutSummary] = []
    @State private var thisWeekSets: [Int64: [DoneSet]] = [:]
    /// The sets of the last 7 days, by muscle.
    @State private var muscles = MuscleVolume()
    @State private var showsBody = false
    @State private var line = YouLines.random()
    /// The current plan, being edited over You.
    @State private var editing: PlanEditor?
    @State private var showsSettings = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    planCard
                    HStack(spacing: 10) {
                        StatTile(title: "Lifts", value: totals.lifts)
                        StatTile(title: "Days", value: totals.days)
                        StatTile(title: "Exercises", value: totals.exercises)
                    }
                    WeekTile(totals: model.bodyLog.totals(thisWeek, sets: thisWeekSets)) {
                        showsBody = true
                    }
                    MuscleVolumeCard(
                        subtitle: "Last 7 days",
                        volume: muscles,
                        formatter: model.weightFormatter,
                        empty: "No sets in the last 7 days. Tick some off and the muscles they work fill in."
                    )
                    Button {
                        showsBody = true
                    } label: {
                        BodyCardLabel(log: model.bodyLog, formatter: model.weightFormatter)
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Opens your weigh-ins and details")
                    if let weeks {
                        ActivityCard(weeks: weeks)
                    }
                    Text(totals.lifts == 0 ? YouLines.beforeFirstSet : line)
                        .font(.kitaeMono(12, relativeTo: .caption))
                        .foregroundStyle(.inkSoft)
                        .multilineTextAlignment(.center)
                        .padding(.top, 24)
                        .padding(.horizontal, 8)
                }
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .background(Color.paper)
            .navigationTitle("You")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Settings", systemImage: "gearshape") {
                        showsSettings = true
                    }
                }
            }
            .navigationDestination(isPresented: $showsSettings) {
                SettingsView()
            }
            .navigationDestination(isPresented: $showsBody) {
                BodyView()
            }
            .navigationDestination(item: $editing) { editor in
                PlanEditView(editor: editor)
            }
        }
        // Read again after a workout, plan or exercise changes, and as sets are ticked off.
        .task(id: [model.historyRevision, model.workout?.doneCount ?? -1]) {
            load()
            await openDebugScreen()
        }
    }

    // MARK: Current plan

    @ViewBuilder
    private var planCard: some View {
        if let plan = model.currentPlan {
            Button {
                edit(plan)
            } label: {
                PlanCardLabel(caption: ".current plan", title: plan.name, detail: plan.sizeLabel)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens the plan to change its days and exercises")
            .contextMenu {
                Button("Edit plan", systemImage: "pencil") { edit(plan) }
                Button("All plans", systemImage: "list.bullet") { model.isChoosingPlan = true }
            }
        } else {
            Button {
                model.isChoosingPlan = true
            } label: {
                PlanCardLabel(caption: ".no plan yet", title: "Choose a plan", detail: "Pick one to follow, or make your own")
            }
            .buttonStyle(.plain)
        }
    }

    private func edit(_ plan: Plan) {
        editing = model.planEditor(for: PlanEditRoute(planID: plan.id, dayID: model.selectedDay?.id))
    }

    // MARK: Loading

    private func load() {
        let today = EpochDay.of(.now)
        let firstWeekday = Calendar.current.firstWeekday
        do {
            let loaded = try model.database.read { db in
                (
                    try Stats.totals(db),
                    try Stats.activity(db, today: today, firstWeekday: firstWeekday),
                    try Sessions.finished(db, from: startOfWeek(today, firstWeekday: firstWeekday), to: today),
                    try Sessions.doneSets(db, from: startOfWeek(today, firstWeekday: firstWeekday), to: today),
                    try Stats.exerciseSets(db, from: today - 6, to: today)
                )
            }
            totals = loaded.0
            weeks = loaded.1
            thisWeek = loaded.2
            thisWeekSets = loaded.3
            muscles = MuscleVolume(loaded.4)
        } catch {
            model.problem = "Couldn't count your workouts. \(error.localizedDescription)"
        }
    }

    /// `-KitaeScreen settings`: opens Settings, and `body` the Body page.
    private func openDebugScreen() async {
        #if DEBUG
        guard let screen = DebugLaunch.screen, ["settings", "body"].contains(screen), DebugLaunch.consume(screen)
        else { return }
        try? await Task.sleep(for: .milliseconds(500))
        if screen == "body" {
            showsBody = true
        } else {
            showsSettings = true
        }
        #endif
    }
}

/// The plan card's label: a caption, the plan's name and its size.
private struct PlanCardLabel: View {
    let caption: String
    let title: String
    let detail: String

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(caption)
                    .font(.kitaeMono(12, relativeTo: .caption))
                Text(title)
                    .font(.kitaeDisplay(34, relativeTo: .title))
                    .lineLimit(2)
                    .minimumScaleFactor(0.6)
                    // Darker Grotesque's line height leaves a lot of air above and below.
                    .padding(.vertical, -5)
                Text(detail)
                    .font(.footnote)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.forward")
                .font(.system(size: 15, weight: .bold))
        }
        .foregroundStyle(.onOliveSoft)
        .padding(.leading, 20)
        .padding(.trailing, 18)
        .padding(.vertical, 16)
        .frame(minHeight: 104)
        .background(.oliveSoft, in: .rect(cornerRadius: 24))
        .contentShape(.rect(cornerRadius: 24))
        .accessibilityElement(children: .combine)
    }
}

/// One of the three totals: Lifts, Days and Exercises.
private struct StatTile: View {
    let title: String
    let value: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.kitaeDisplay(20, relativeTo: .headline))
                .foregroundStyle(.inkSoft)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.vertical, -2)
            Spacer(minLength: 0)
            Text(value.formatted())
                .font(.kitaeMono(28, relativeTo: .title, bold: true))
                .tracking(-1)
                .foregroundStyle(.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .padding(.vertical, -5)
        }
        .frame(maxWidth: .infinity, minHeight: 88, alignment: .leading)
        .padding(14)
        .background(.card, in: .rect(cornerRadius: 22))
        .accessibilityElement(children: .combine)
    }
}

/// This week's workouts, their time, and the active energy they used once a weight is known.
private struct WeekTile: View {
    let totals: WorkoutTotals
    /// Opens the Body page, to add a weight.
    let addWeight: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("This week")
                    .font(.kitaeDisplay(20, relativeTo: .headline))
                    .foregroundStyle(.inkSoft)
                    .padding(.vertical, -2)
                Spacer(minLength: 8)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.inkSoft)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
            if let kilocalories = totals.kilocalories {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(roundedKilocalories(kilocalories).formatted())
                        .font(.kitaeMono(28, relativeTo: .title, bold: true))
                        .tracking(-1)
                        .foregroundStyle(.ink)
                        .padding(.vertical, -5)
                    Text("kcal burned")
                        .font(.kitaeMono(13, relativeTo: .footnote))
                        .foregroundStyle(.inkSoft)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("About \(roundedKilocalories(kilocalories)) kilocalories burned")
            } else {
                Button(action: addWeight) {
                    Text("Add your weight to count kcal")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.olive)
                        .frame(minHeight: 32)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 88, alignment: .leading)
        .padding(14)
        .background(.card, in: .rect(cornerRadius: 22))
        .accessibilityElement(children: .contain)
    }

    /// "3 workouts · 2 h 34 min".
    private var detail: String {
        guard totals.workouts > 0 else { return "No workouts yet" }
        return "\(counted(totals.workouts, "workout", "workouts")) · \(durationLabel(minutes: totals.minutes))"
    }
}

/// The Body card's label: the latest weight and how it's gone lately, or an invitation to add
/// one.
private struct BodyCardLabel: View {
    let log: BodyLog
    let formatter: WeightFormatter

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(".body")
                    .font(.kitaeMono(12, relativeTo: .caption))
                    .foregroundStyle(.inkSoft)
                if let latest = log.latestWeight, let kg = latest.weightKg {
                    Text(formatter.body(kilograms: kg))
                        .font(.kitaeDisplay(34, relativeTo: .title))
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .padding(.vertical, -5)
                    if let detail = detail(latest) {
                        Text(detail)
                            .font(.footnote)
                            .foregroundStyle(.inkSoft)
                    }
                } else {
                    Text("Track your weight")
                        .font(.kitaeDisplay(30, relativeTo: .title))
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .padding(.vertical, -5)
                    Text("Height, weight, body fat and more")
                        .font(.footnote)
                        .foregroundStyle(.inkSoft)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            let recent = recentWeights
            if recent.count >= 2 {
                Sparkline(values: recent)
                    .frame(width: 76, height: 40)
                    .accessibilityHidden(true)
            }
            Image(systemName: "chevron.forward")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.inkSoft)
        }
        .padding(.leading, 20)
        .padding(.trailing, 18)
        .padding(.vertical, 16)
        .frame(minHeight: 96)
        .background(.card, in: .rect(cornerRadius: 24))
        .contentShape(.rect(cornerRadius: 24))
        .accessibilityElement(children: .combine)
    }

    /// The weights of the last 12 weigh-ins, oldest first.
    private var recentWeights: [Double] {
        log.entries.compactMap(\.weightKg).suffix(12).map { $0 }
    }

    /// "18.5% body fat · −1.8 kg since Sep 16": the change since the first weigh-in of the last
    /// 30 days.
    private func detail(_ latest: BodyEntry) -> String? {
        var parts: [String] = []
        if let percent = log.latestBodyFat?.bodyFatPercent {
            parts.append("\(formatter.percent(percent)) body fat")
        }
        if let kg = latest.weightKg,
           let first = log.entries.first(where: { $0.weightKg != nil && $0.date >= latest.date - 30 }),
           first.date < latest.date, let firstKg = first.weightKg {
            let change = formatter.unit.fromKilograms(kg) - formatter.unit.fromKilograms(firstKg)
            let amount = formatter.tenthsOrWhole(abs(change))
            let sign = change > 0.05 ? "+" : change < -0.05 ? "−" : "±"
            parts.append("\(sign)\(amount) \(formatter.unit.symbol) since \(HistoryDates.dayAndMonth(first.date))")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

/// A small line of recent values, without axes.
private struct Sparkline: View {
    let values: [Double]

    var body: some View {
        let low = values.min() ?? 0
        let high = values.max() ?? 1
        let pad = max((high - low) * 0.15, 0.25)
        Chart {
            ForEach(Array(values.enumerated()), id: \.offset) { index, value in
                LineMark(x: .value("Weigh-in", index), y: .value("Weight", value))
                    .foregroundStyle(Color.olive)
                    .lineStyle(StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                    .interpolationMethod(.monotone)
            }
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartYScale(domain: (low - pad)...(high + pad))
        .chartXScale(domain: 0...max(values.count - 1, 1))
    }
}

/// The last 16 weeks: a square for each day, olive when a set was done.
private struct ActivityCard: View {
    let weeks: ActivityWeeks

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text("Activity")
                    .font(.kitaeDisplay(24, relativeTo: .title3))
                    .foregroundStyle(.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                Text("Last 16 weeks")
                    .font(.footnote)
                    .foregroundStyle(.inkSoft)
            }
            VStack(alignment: .leading, spacing: 8) {
                DayGrid(rows: 7, columns: ActivityWeeks.count, spacing: 4, labelWidth: 12, labelSpacing: 8) {
                    ForEach(Array(weeks.rowLetters().enumerated()), id: \.offset) { _, letter in
                        Text(letter)
                            .font(.kitaeMono(10, relativeTo: .caption2))
                            .foregroundStyle(.inkSoft)
                            .lineLimit(1)
                            .minimumScaleFactor(0.5)
                    }
                    ForEach(0..<ActivityWeeks.count, id: \.self) { week in
                        ForEach(0..<7, id: \.self) { row in
                            square(weeks.day(week: week, row: row))
                        }
                    }
                }
                HStack {
                    Text(HistoryDates.dayAndMonth(weeks.first))
                    Spacer(minLength: 8)
                    Text(HistoryDates.dayAndMonth(weeks.today))
                }
                .font(.kitaeMono(11, relativeTo: .caption2))
                .foregroundStyle(.inkSoft)
                .padding(.leading, 20)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Days trained in the last 16 weeks")
            .accessibilityValue("\(weeks.trainedCount)")
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.card, in: .rect(cornerRadius: 24))
    }

    private func square(_ day: Int) -> some View {
        RoundedRectangle(cornerRadius: 4)
            .fill(weeks.trained.contains(day) ? Color.olive : Color.dayOff)
            .opacity(day > weeks.today ? 0.4 : 1)
    }
}

/// Square cells in columns, filling the width, after a column of row labels. The labels come
/// first, then the cells a column at a time.
private struct DayGrid: Layout {
    let rows: Int
    let columns: Int
    let spacing: CGFloat
    let labelWidth: CGFloat
    let labelSpacing: CGFloat

    private func cellSize(width: CGFloat) -> CGFloat {
        let free = width - labelWidth - labelSpacing - CGFloat(columns - 1) * spacing
        return max(free / CGFloat(columns), 4)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? (labelWidth + labelSpacing + CGFloat(columns) * 14 + CGFloat(columns - 1) * spacing)
        let cell = cellSize(width: width)
        return CGSize(width: width, height: CGFloat(rows) * cell + CGFloat(rows - 1) * spacing)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let cell = cellSize(width: bounds.width)
        let size = ProposedViewSize(width: cell, height: cell)
        for (index, subview) in subviews.enumerated() {
            if index < rows {
                let y = bounds.minY + CGFloat(index) * (cell + spacing) + cell / 2
                subview.place(
                    at: CGPoint(x: bounds.minX + labelWidth / 2, y: y),
                    anchor: .center,
                    proposal: ProposedViewSize(width: labelWidth, height: cell)
                )
            } else {
                let column = (index - rows) / rows
                let row = (index - rows) % rows
                let x = bounds.minX + labelWidth + labelSpacing + CGFloat(column) * (cell + spacing)
                let y = bounds.minY + CGFloat(row) * (cell + spacing)
                subview.place(at: CGPoint(x: x, y: y), proposal: size)
            }
        }
    }
}

/// Short lines at the bottom of You, one each time Kitae opens.
enum YouLines {
    static let all = [
        "every filled square is a day you showed up",
        "strength is built on ordinary days",
        "the log remembers so you don't have to",
        "light days count too",
        "slow progress is still progress",
    ]

    /// Until the first set is done.
    static let beforeFirstSet = "tick off a set and the first square fills in"

    static func random() -> String {
        all.randomElement() ?? all[0]
    }
}
