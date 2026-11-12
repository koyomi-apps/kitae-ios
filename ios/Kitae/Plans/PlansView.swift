// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Every plan: choose the one to follow, make a new one, or edit or delete one. It opens by
/// itself while no plan is followed, as on Kitae for Android's first launch.
struct PlansView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var editing: PlanEditor?
    @State private var namesNewPlan = false
    /// A plan that was just made, to open once the name sheet is gone.
    @State private var madePlan: PlanEditor?
    @State private var deleting: PlanSummary?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(model.plans) { plan in
                        row(plan)
                    }
                } header: {
                    if model.currentPlan == nil {
                        Text("Pick a plan to follow, and Kitae lines up its days one after another. You can switch plans or make your own any time.")
                            .font(.subheadline)
                            .foregroundStyle(.inkSoft)
                            .textCase(nil)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.bottom, 8)
                    }
                } footer: {
                    if model.workout != nil {
                        Text("Finish your workout to switch plans.")
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .navigationTitle(model.currentPlan == nil ? "Choose a plan" : "Plans")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("New plan", systemImage: "plus") { namesNewPlan = true }
                }
            }
            .navigationDestination(item: $editing) { editor in
                PlanEditView(editor: editor)
            }
        }
        .sheet(isPresented: $namesNewPlan, onDismiss: openMadePlan) {
            NameSheet("New plan", placeholder: "Plan name", actionTitle: "Create") { name in
                let id = try model.createPlan(named: name)
                madePlan = model.planEditor(for: PlanEditRoute(planID: id))
            }
        }
        .confirmationDialog(
            "Delete \(deleting?.name ?? "this plan")?",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible,
            presenting: deleting
        ) { plan in
            Button("Delete plan", role: .destructive) {
                model.deletePlan(plan.id)
            }
        } message: { _ in
            Text("Its days and planned sets will be removed. Workouts you did stay.")
        }
        .task { await openDebugScreen() }
    }

    private func row(_ plan: PlanSummary) -> some View {
        let isCurrent = plan.id == model.currentPlan?.id
        return HStack(spacing: 4) {
            Button {
                model.selectPlan(plan.id)
                dismiss()
            } label: {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(plan.name)
                            .font(.kitaeDisplay(28, relativeTo: .title2))
                            .foregroundStyle(.ink)
                        Text(plan.sizeLabel)
                            .font(.kitaeMono(12, relativeTo: .caption))
                            .foregroundStyle(.inkSoft)
                    }
                    Spacer(minLength: 8)
                    if isCurrent {
                        Image(systemName: "checkmark")
                            .fontWeight(.semibold)
                            .foregroundStyle(.olive)
                    }
                }
                .padding(.vertical, 10)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .disabled(model.workout != nil && !isCurrent)
            .accessibilityAddTraits(isCurrent ? .isSelected : [])

            Button {
                edit(plan)
            } label: {
                Image(systemName: "pencil")
                    .fontWeight(.semibold)
                    .foregroundStyle(.olive)
                    .frame(width: 44, height: 44)
                    .contentShape(.rect)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Edit \(plan.name)")
        }
        .listRowBackground(Color.card)
        .swipeActions(edge: .trailing) {
            if model.canDelete(plan.id) {
                Button("Delete", systemImage: "trash", role: .destructive) {
                    deleting = plan
                }
            }
            Button("Edit", systemImage: "pencil") {
                edit(plan)
            }
            .tint(.olive)
        }
        .contextMenu {
            Button("Edit", systemImage: "pencil") { edit(plan) }
            if model.canDelete(plan.id) {
                Button("Delete", systemImage: "trash", role: .destructive) { deleting = plan }
            }
        }
    }

    private func edit(_ plan: PlanSummary) {
        let dayID = plan.id == model.currentPlan?.id ? model.selectedDay?.id : nil
        editing = model.planEditor(for: PlanEditRoute(planID: plan.id, dayID: dayID))
    }

    private func openMadePlan() {
        guard let madePlan else { return }
        editing = madePlan
        self.madePlan = nil
    }

    /// `-KitaeScreen newplan`: asks for a new plan's name.
    private func openDebugScreen() async {
        #if DEBUG
        guard DebugLaunch.screen == "newplan", DebugLaunch.consume("newplan") else { return }
        try? await Task.sleep(for: .milliseconds(700))
        namesNewPlan = true
        #endif
    }
}
