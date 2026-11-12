// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// What Kitae shows first: how big its text is, light or dark, how weights are written, the
/// usual rest and backups, with two example sets to judge them by. A backup can be restored
/// from here, for someone coming from another phone or from Kitae for Android. It's all in
/// Settings too. Once it's gone, a plan is chosen.
struct WelcomeView: View {
    @Environment(AppModel.self) private var model
    @State private var backupFlow = BackupFlow()

    var body: some View {
        @Bindable var model = model
        List {
            Section {
                header
                    .listRowBackground(Color.clear)
            }

            Section {
                ForEach(Example.all) { example in
                    exampleRow(example)
                        .listRowBackground(Color.card)
                }
            } header: {
                SectionCaption(".example")
            }

            Section {
                AppearanceRows(settings: $model.settings)
            } header: {
                SectionCaption(".appearance")
            }

            Section {
                WeightRows(settings: $model.settings)
                DefaultRestRow(seconds: $model.settings.defaultRestSeconds)
            } header: {
                SectionCaption(".workouts")
            } footer: {
                Text("You can type a point or a comma either way. This sets how weights are shown.")
                    .foregroundStyle(.inkSoft)
            }

            Section {
                BackupRows(flow: backupFlow, offersBackUpNow: false)
            } header: {
                SectionCaption(".backups")
            } footer: {
                Text("Coming from another iPhone, or from Kitae on Android? Restore a backup to bring your plans and workouts with you.")
                    .foregroundStyle(.inkSoft)
            }
        }
        .listSectionSpacing(20)
        .environment(\.defaultMinListRowHeight, 52)
        .scrollContentBackground(.hidden)
        .background(Color.paper)
        .backupFlow(backupFlow)
        .safeAreaBar(edge: .bottom) {
            Button {
                model.finishWelcome()
            } label: {
                Text("Get started")
                    .frame(maxWidth: .infinity, minHeight: 58)
            }
            .buttonStyle(MauveButtonStyle())
            // Not while a backup is being restored, so its answer isn't lost.
            .disabled(model.backups.isWorking)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
        .tint(.olive)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            Caption(".welcome to", size: 14)
                .accessibilityHidden(true)
            Text("Kitae")
                .font(.kitaeDisplay(112))
                .foregroundStyle(.olive)
                .lineLimit(1)
                .minimumScaleFactor(0.4)
                // Darker Grotesque's line height leaves a lot of air at this size.
                .padding(.top, -22)
                .padding(.bottom, -12)
                .padding(.leading, -4)
                .accessibilityLabel("Welcome to Kitae")
                .accessibilityAddTraits(.isHeader)
            Text("Set Kitae up the way you like it. You can change all of this later in You › Settings.")
                .foregroundStyle(.inkSoft)
                .padding(.top, 16)
        }
    }

    private func exampleRow(_ example: Example) -> some View {
        HStack(spacing: 12) {
            Text(example.name)
            Spacer(minLength: 8)
            Text(example.text(model.weightFormatter))
                .font(.kitaeMono(15, relativeTo: .body))
                .foregroundStyle(.inkSoft)
        }
    }
}

/// A set to show weights by, with a round weight in each unit.
private struct Example: Identifiable {
    let name: String
    let kilograms: Double
    let pounds: Double
    let reps: Int

    var id: String { name }

    static let all = [
        Example(name: "Bench press", kilograms: 62.5, pounds: 137.5, reps: 8),
        Example(name: "Squat", kilograms: 102.5, pounds: 225, reps: 5),
    ]

    /// "62.5 kg × 8", written as Settings say.
    func text(_ formatter: WeightFormatter) -> String {
        let weight = formatter.unit == .kilograms ? kilograms : pounds
        return "\(formatter.number(weight)) \(formatter.unit.symbol) × \(reps)"
    }
}
