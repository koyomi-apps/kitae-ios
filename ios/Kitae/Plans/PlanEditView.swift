// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Changes a plan: its days, each day's exercises, and their planned sets. Every change is
/// saved straight away.
struct PlanEditView: View {
    let editor: PlanEditor

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var sheet: EditorSheet?
    @State private var confirmation: Confirmation?
    /// An exercise that was just added, to scroll to.
    @State private var scrollTarget: DayExercise.ID?

    var body: some View {
        ScrollViewReader { proxy in
            List {
                // The title and days are a header rather than a row, so the days can scroll
                // to the edges of the screen.
                Section {
                    if let day = editor.selectedDay, day.exercises.isEmpty {
                        emptyDay
                            .listRowBackground(Color.clear)
                    }
                } header: {
                    header
                        .textCase(nil)
                        .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 4, trailing: 0))
                }

                if let day = editor.selectedDay {
                    ForEach(day.exercises) { item in
                        exerciseSection(item, in: day)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .listSectionSpacing(14)
            .environment(\.defaultMinListRowHeight, 44)
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .onChange(of: scrollTarget) { _, target in
                guard let target else { return }
                Task {
                    // Give the list a moment to show the new exercise first.
                    try? await Task.sleep(for: .milliseconds(200))
                    withAnimation { proxy.scrollTo(EditRow.exercise(target), anchor: .top) }
                    scrollTarget = nil
                }
            }
        }
        .safeAreaBar(edge: .bottom) {
            if editor.plan != nil {
                addButton
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(asksBeforeLeaving)
        .interactiveDismissDisabled(asksBeforeLeaving)
        .toolbar(.hidden, for: .tabBar)
        .toolbar { toolbar }
        .sheet(item: $sheet) { sheet in
            sheetContent(sheet)
                .environment(model)
        }
        .confirmationDialog(
            confirmation?.title(editor) ?? "",
            isPresented: Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } }),
            titleVisibility: .visible,
            presenting: confirmation
        ) { confirmation in
            confirmationActions(confirmation)
        } message: { confirmation in
            Text(confirmation.message(editor))
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(get: { editor.problem != nil }, set: { if !$0 { editor.problem = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(editor.problem ?? "")
        }
        .task { await openDebugScreen() }
    }

    // MARK: Header and days

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(editor.plan?.name ?? "")
                .font(.kitaeDisplay(46))
                .foregroundStyle(.ink)
                .lineLimit(2)
                .minimumScaleFactor(0.5)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Caption(editor.plan?.sizeLabel ?? "")
            if !editor.days.isEmpty {
                dayChips
                    .padding(.top, 20)
            } else if editor.plan != nil {
                firstDay
                    .padding(.top, 28)
            }
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 2)
    }

    private var dayChips: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(editor.days) { day in
                        DayChip(title: day.name, isSelected: day.id == editor.selectedDayID) {
                            editor.selectedDayID = day.id
                        }
                        .id(day.id)
                    }
                    Button {
                        sheet = .name(.newDay)
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 15, weight: .semibold))
                            .frame(width: 44, height: 44)
                            .background(Color.field, in: .circle)
                            .foregroundStyle(.ink)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("New day")
                }
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
            .onChange(of: editor.selectedDayID) { _, id in
                guard let id else { return }
                withAnimation { proxy.scrollTo(id, anchor: .center) }
            }
        }
    }

    /// A new plan starts with naming its first day.
    private var firstDay: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Plan your days")
                .font(.kitaeDisplay(30, relativeTo: .title))
                .foregroundStyle(.olive)
            Text("Name the first day, like Push or Upper. You can add more days and change their order later.")
                .font(.subheadline)
                .foregroundStyle(.inkSoft)
                .fixedSize(horizontal: false, vertical: true)
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(editor.unusedDayNames, id: \.self) { name in
                        DayChip(title: name, isSelected: false) {
                            _ = try? editor.addDay(named: name)
                        }
                    }
                }
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
            .padding(.top, 6)
            Button("Another name…") {
                sheet = .name(.newDay)
            }
            .font(.body.weight(.semibold))
            .foregroundStyle(.olive)
            .buttonStyle(.plain)
            .frame(minHeight: 44)
        }
    }

    private var emptyDay: some View {
        VStack(spacing: 6) {
            Text("No exercises yet")
                .font(.kitaeDisplay(28, relativeTo: .title2))
                .foregroundStyle(.inkSoft)
            Text("Add one with the button below.")
                .font(.subheadline)
                .foregroundStyle(.inkSoft)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 36)
    }

    // MARK: Exercises and sets

    private func exerciseSection(_ item: DayExercise, in day: TrainingDay) -> some View {
        let labels = setLabels(for: item.sets.map(\.type))
        return Section {
            exerciseHeader(item, in: day)
            ForEach(Array(item.sets.enumerated()), id: \.element.id) { index, set in
                setRow(set, label: labels[index], of: item)
            }
            addSetRow(item)
        }
    }

    private func exerciseHeader(_ item: DayExercise, in day: TrainingDay) -> some View {
        let index = day.exercises.firstIndex { $0.id == item.id } ?? 0
        return HStack(spacing: 10) {
            Text(item.exercise.name)
                .font(.body.weight(.semibold))
                .foregroundStyle(.ink)
            Spacer(minLength: 8)
            Text(item.exercise.muscle?.name ?? item.exercise.target)
                .font(.footnote)
                .foregroundStyle(.inkSoft)
        }
        .frame(minHeight: 54)
        .alignmentGuide(.listRowSeparatorLeading) { _ in 28 }
        .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
        .listRowBackground(Color.card)
        .id(EditRow.exercise(item.id))
        .accessibilityElement(children: .combine)
        .swipeActions(edge: .trailing) {
            Button("Remove", systemImage: "trash", role: .destructive) {
                editor.removeExercise(item.id)
            }
        }
        .contextMenu {
            Button("Move up", systemImage: "arrow.up") {
                editor.moveExercises(from: [index], to: index - 1)
            }
            .disabled(index == 0)
            Button("Move down", systemImage: "arrow.down") {
                editor.moveExercises(from: [index], to: index + 2)
            }
            .disabled(index == day.exercises.count - 1)
            Divider()
            Button("Remove from \(day.name)", systemImage: "trash", role: .destructive) {
                editor.removeExercise(item.id)
            }
        }
    }

    private func setRow(_ set: PlannedSet, label: String, of item: DayExercise) -> some View {
        let summary = model.weightFormatter.summary(reps: set.reps, kilograms: item.exercise.weight(set.weightKg), isIsometric: item.exercise.isIsometric)
        return Button {
            sheet = .set(set.id)
        } label: {
            HStack(spacing: 12) {
                Text(label)
                    .font(.kitaeMono(15, relativeTo: .body, bold: true))
                    .foregroundStyle(.inkSoft)
                    .frame(width: 28, alignment: .leading)
                Text(summary)
                    .font(.kitaeMono(15, relativeTo: .body))
                    .foregroundStyle(.ink)
                Spacer(minLength: 8)
                if let rest = set.restSeconds {
                    Text("rest \(restSetting(seconds: rest))")
                        .font(.kitaeMono(12, relativeTo: .caption))
                        .foregroundStyle(.inkSoft)
                }
            }
            .frame(minHeight: 44)
            .contentShape(.rect)
        }
        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
        .listRowInsets(EdgeInsets(top: 0, leading: 44, bottom: 0, trailing: 16))
        .listRowBackground(Color.card)
        .accessibilityLabel("Set \(label), \(summary)")
        .accessibilityHint("Opens the set's options")
        .swipeActions(edge: .trailing) {
            Button("Delete", systemImage: "trash", role: .destructive) {
                editor.deleteSet(set.id)
            }
        }
    }

    private func addSetRow(_ item: DayExercise) -> some View {
        Button {
            editor.addSet(to: item.id)
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "plus")
                    .fontWeight(.semibold)
                Text("Add set")
            }
            .foregroundStyle(.olive)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .contentShape(.rect)
        }
        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
        .listRowInsets(EdgeInsets(top: 0, leading: 44, bottom: 0, trailing: 16))
        .listRowBackground(Color.card)
        .accessibilityLabel("Add a set to \(item.exercise.name)")
    }

    private var addButton: some View {
        Button {
            if editor.selectedDay != nil {
                sheet = .addExercise
            } else {
                sheet = .name(.newDay)
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "plus")
                    .fontWeight(.semibold)
                Text(editor.selectedDay.map { "Add exercise to \($0.name)" } ?? "Add a day")
                    .lineLimit(1)
            }
            .font(.body.weight(.semibold))
            .foregroundStyle(.olive)
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity, minHeight: 54)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .capsule)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    // MARK: Toolbar

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if asksBeforeLeaving {
            ToolbarItem(placement: .topBarLeading) {
                // Like the usual back button, which asks first while the plan is empty.
                Button("Back", systemImage: "chevron.backward") { leave() }
                    .tint(.ink)
            }
        }
        ToolbarItem(placement: .topBarTrailing) {
            optionsMenu
        }
        ToolbarSpacer(.fixed, placement: .topBarTrailing)
        ToolbarItem(placement: .topBarTrailing) {
            Button {
                leave()
            } label: {
                Text("Done")
                    .fontWeight(.semibold)
                    .foregroundStyle(.olive)
            }
        }
    }

    private var optionsMenu: some View {
        Menu {
            if let day = editor.selectedDay {
                Section(day.name) {
                    Button("Rename day", systemImage: "pencil") {
                        sheet = .name(.renameDay)
                    }
                    Button("Move earlier", systemImage: "arrow.left") {
                        editor.moveSelectedDay(by: -1)
                    }
                    .disabled(!editor.canMoveSelectedDay(by: -1))
                    Button("Move later", systemImage: "arrow.right") {
                        editor.moveSelectedDay(by: 1)
                    }
                    .disabled(!editor.canMoveSelectedDay(by: 1))
                    Button("Reorder exercises", systemImage: "arrow.up.arrow.down") {
                        sheet = .reorder
                    }
                    .disabled(day.exercises.count < 2)
                    Button("Delete day", systemImage: "trash", role: .destructive) {
                        confirmation = .deleteDay
                    }
                    .disabled(!editor.canDeleteSelectedDay)
                }
            }
            Section {
                Button("New day", systemImage: "plus") {
                    sheet = .name(.newDay)
                }
            }
            Section(editor.plan?.name ?? "Plan") {
                Button("Rename plan", systemImage: "character.cursor.ibeam") {
                    sheet = .name(.renamePlan)
                }
                Button("Delete plan", systemImage: "trash", role: .destructive) {
                    confirmation = .deletePlan
                }
                .disabled(!editor.canDeletePlan)
            }
        } label: {
            Label("Options", systemImage: "ellipsis")
        }
    }

    // MARK: Leaving

    /// A plan without exercises isn't worth keeping, so leaving asks about it, as on Android.
    /// The plan of the workout in progress can't be deleted, so that one never asks.
    private var asksBeforeLeaving: Bool {
        editor.plan != nil && !editor.hasExercises && editor.canDeletePlan
    }

    private func leave() {
        if asksBeforeLeaving {
            confirmation = .emptyPlan
        } else {
            dismiss()
        }
    }

    // MARK: Sheets and questions

    @ViewBuilder
    private func sheetContent(_ sheet: EditorSheet) -> some View {
        switch sheet {
        case .set(let id):
            PlannedSetSheet(
                editor: editor,
                target: PlannedSetTarget(id: id),
                formatter: model.weightFormatter,
                defaultRestSeconds: model.defaultRestSeconds
            )
        case .addExercise:
            if let day = editor.selectedDay {
                ExercisePicker(dayName: day.name, added: Set(day.exercises.map(\.exercise.id))) { exercise in
                    scrollTarget = editor.addExercise(exercise)
                }
            }
        case .reorder:
            ReorderExercisesSheet(editor: editor)
        case .name(.newDay):
            NameSheet("New day", placeholder: "Day name", actionTitle: "Add", suggestions: editor.unusedDayNames) { name in
                try editor.addDay(named: name)
            }
        case .name(.renameDay):
            NameSheet("Rename day", placeholder: "Day name", actionTitle: "Save", name: editor.selectedDay?.name ?? "") { name in
                try editor.renameSelectedDay(to: name)
            }
        case .name(.renamePlan):
            NameSheet("Rename plan", placeholder: "Plan name", actionTitle: "Save", name: editor.plan?.name ?? "") { name in
                try editor.rename(to: name)
            }
        }
    }

    @ViewBuilder
    private func confirmationActions(_ confirmation: Confirmation) -> some View {
        switch confirmation {
        case .deleteDay:
            Button("Delete day", role: .destructive) {
                editor.deleteSelectedDay()
            }
        case .deletePlan:
            Button("Delete plan", role: .destructive) {
                editor.delete()
                dismiss()
            }
        case .emptyPlan:
            Button("Delete plan", role: .destructive) {
                editor.delete()
                dismiss()
            }
            Button("Keep editing", role: .cancel) {}
        }
    }

    // MARK: Debug

    /// `-KitaeScreen planset`, `plansettyping`, `addexercise` or `newday`: opens a sheet once the
    /// editor is showing.
    private func openDebugScreen() async {
        #if DEBUG
        guard let screen = DebugLaunch.screen, ["planset", "plansettyping", "addexercise", "newday"].contains(screen),
              DebugLaunch.consume(screen) else { return }
        try? await Task.sleep(for: .milliseconds(700))
        switch screen {
        case "planset", "plansettyping":
            if let set = editor.selectedDay?.exercises.first?.sets.first {
                sheet = .set(set.id)
            }
        case "addexercise":
            sheet = .addExercise
        default:
            sheet = .name(.newDay)
        }
        #endif
    }
}

/// What the plan editor can open over itself.
private enum EditorSheet: Identifiable, Hashable {
    case set(PlannedSet.ID)
    case addExercise
    case reorder
    case name(NameRequest)

    var id: Self { self }
}

private enum NameRequest: Hashable {
    case newDay
    case renameDay
    case renamePlan
}

/// What the plan editor asks before deleting something.
private enum Confirmation: Hashable {
    case deleteDay
    case deletePlan
    /// Leaving a plan that has no exercises.
    case emptyPlan

    func title(_ editor: PlanEditor) -> String {
        switch self {
        case .deleteDay: "Delete \(editor.selectedDay?.name ?? "this day")?"
        case .deletePlan: "Delete \(editor.plan?.name ?? "this plan")?"
        case .emptyPlan: "This plan has no exercises"
        }
    }

    func message(_ editor: PlanEditor) -> String {
        switch self {
        case .deleteDay:
            "\(editor.selectedDay?.name ?? "This day") and its planned sets will be removed from this plan. Workouts you did stay."
        case .deletePlan:
            "Its days and planned sets will be removed. Workouts you did stay."
        case .emptyPlan:
            "Add an exercise to a day to keep it."
        }
    }
}

/// Rows the plan editor scrolls to.
private enum EditRow: Hashable {
    case exercise(DayExercise.ID)
}

/// The selected day's exercises with handles, to drag them into another order.
private struct ReorderExercisesSheet: View {
    let editor: PlanEditor
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(editor.selectedDay?.exercises ?? []) { item in
                    HStack {
                        Text(item.exercise.name)
                            .foregroundStyle(.ink)
                        Spacer(minLength: 8)
                        Text("\(item.sets.count)")
                            .font(.kitaeMono(13, relativeTo: .footnote))
                            .foregroundStyle(.inkSoft)
                            .accessibilityLabel(counted(item.sets.count, "set", "sets"))
                    }
                    .listRowBackground(Color.card)
                }
                .onMove { source, destination in
                    editor.moveExercises(from: source, to: destination)
                }
            }
            .environment(\.editMode, .constant(.active))
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .navigationTitle("Order of \(editor.selectedDay?.name ?? "the day")")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", systemImage: "checkmark") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
