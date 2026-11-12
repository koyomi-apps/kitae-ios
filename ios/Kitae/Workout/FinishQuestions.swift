// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// What Finish asks before a workout ends, in the same order as Kitae for Android: first
/// what happens to the sets that weren't done, then whether sets added during the workout
/// join the day for next time.
enum FinishQuestion: Hashable {
    /// Nothing was done: keep the workout as skipped, or discard it.
    case nothingDone
    /// Some sets weren't done: keep them as skipped, or remove them from the plan too.
    case unfinished(Int)
    /// Sets were added and done: add them to the day, or keep them in this workout only.
    case addedSets(Int, mode: FinishMode, isFirst: Bool)
    /// Everything planned was done, but another day is being started.
    case confirm

    /// The first question for this workout. nil when there's nothing to ask.
    static func first(for workout: Workout, startingAnotherDay: Bool) -> FinishQuestion? {
        if workout.notDoneCount > 0 {
            return workout.doneCount == 0 ? .nothingDone : .unfinished(workout.notDoneCount)
        }
        if workout.addedDoneCount > 0 {
            return .addedSets(workout.addedDoneCount, mode: .keepSkipped, isFirst: true)
        }
        return startingAnotherDay ? .confirm : nil
    }

    /// Whether this is the first question asked, rather than a follow-up.
    var isFirst: Bool {
        if case .addedSets(_, _, let isFirst) = self { return isFirst }
        return true
    }
}

extension View {
    /// Asks the finish questions while `question` is set, then calls `onFinish`.
    /// `nextDayName` is the day that starts afterwards when switching days on Today.
    func finishQuestions(
        _ question: Binding<FinishQuestion?>,
        workout: Workout?,
        nextDayName: String? = nil,
        onContinue: (() -> Void)? = nil,
        onFinish: @escaping (FinishMode, Bool) -> Void
    ) -> some View {
        modifier(FinishQuestions(
            question: question,
            workout: workout,
            nextDayName: nextDayName,
            onContinue: onContinue,
            onFinish: onFinish
        ))
    }
}

private struct FinishQuestions: ViewModifier {
    @Binding var question: FinishQuestion?
    let workout: Workout?
    let nextDayName: String?
    let onContinue: (() -> Void)?
    let onFinish: (FinishMode, Bool) -> Void

    func body(content: Content) -> some View {
        content.confirmationDialog(
            title,
            isPresented: Binding(get: { question != nil }, set: { if !$0 { question = nil } }),
            titleVisibility: .visible,
            presenting: question
        ) { question in
            actions(question)
        } message: { question in
            Text(message(question))
        }
    }

    private var dayName: String { workout?.dayName ?? "this day" }

    private var title: String {
        if nextDayName != nil { return "Workout in progress" }
        if case .addedSets = question { return "Keep the new sets?" }
        return "Finish workout?"
    }

    private func message(_ question: FinishQuestion) -> String {
        var parts: [String] = []
        if let nextDayName, question.isFirst {
            parts.append("\(dayName) isn't finished yet. Finish it and start \(nextDayName)?")
        }
        switch question {
        case .nothingDone:
            parts.append("Nothing was done in this workout. Keep it as skipped, or discard it? Your plan stays the same either way.")
        case .unfinished(1):
            parts.append("1 set isn't done. Skipping it keeps \(dayName) as it is. Removing it also takes it out of the plan.")
        case .unfinished(let count):
            parts.append("\(count) sets aren't done. Skipping them keeps \(dayName) as it is. Removing them also takes them out of the plan.")
        case .addedSets(1, _, _):
            parts.append("You added 1 set. Add it to \(dayName) for next time?")
        case .addedSets(let count, _, _):
            parts.append("You added \(count) sets. Add them to \(dayName) for next time?")
        case .confirm:
            break
        }
        return parts.joined(separator: "\n\n")
    }

    @ViewBuilder
    private func actions(_ question: FinishQuestion) -> some View {
        switch question {
        case .nothingDone:
            Button("Keep as skipped") { onFinish(.keepSkipped, false) }
            Button("Discard workout", role: .destructive) { onFinish(.discard, false) }
        case .unfinished:
            Button("Keep as skipped") { askAboutAddedSets(after: .keepSkipped) }
            Button("Remove from plan", role: .destructive) { askAboutAddedSets(after: .removeFromPlan) }
        case .addedSets(_, let mode, _):
            Button("Add to \(dayName)") { onFinish(mode, true) }
            Button("Only this workout") { onFinish(mode, false) }
        case .confirm:
            Button("Finish and start \(nextDayName ?? "")") { onFinish(.keepSkipped, false) }
        }
        if let onContinue, question.isFirst {
            Button("Continue \(dayName)", action: onContinue)
        }
    }

    /// The second question comes once the first has closed.
    private func askAboutAddedSets(after mode: FinishMode) {
        let added = workout?.addedDoneCount ?? 0
        guard added > 0 else {
            onFinish(mode, false)
            return
        }
        Task {
            try? await Task.sleep(for: .milliseconds(400))
            question = .addedSets(added, mode: mode, isFirst: false)
        }
    }
}
