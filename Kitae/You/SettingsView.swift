// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI
import UIKit
import UserNotifications

/// Units, decimals, rest and alerts, text size and theme, backups, starting over, and about
/// Kitae. Changes are saved straight away.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    /// Whether alerts are turned off for Kitae in the Settings app, so the rest timer can't
    /// set one off.
    @State private var alertsDenied = false
    @State private var backupFlow = BackupFlow()
    @State private var confirmsReset = false
    /// Why resetting didn't work.
    @State private var resetProblem: String?

    static let sourceCode = URL(string: "https://github.com/koyomi-apps/kitae-ios")!

    var body: some View {
        @Bindable var model = model
        List {
            Section {
                WeightRows(settings: $model.settings)

                DefaultRestRow(seconds: $model.settings.defaultRestSeconds)

                Toggle("Rest timer alerts", isOn: $model.settings.restAlerts)
                    .listRowBackground(Color.card)

                Toggle("Summary after each workout", isOn: $model.settings.showsWorkoutSummary)
                    .listRowBackground(Color.card)

                Toggle("New bests in the summary", isOn: $model.settings.showsNewBests)
                    .disabled(!model.settings.showsWorkoutSummary)
                    .listRowBackground(Color.card)

                if model.settings.restAlerts, alertsDenied {
                    Button("Allow alerts for Kitae in Settings") {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                            openURL(url)
                        }
                    }
                    .listRowBackground(Color.card)
                }
            } header: {
                SectionCaption(".workouts")
            } footer: {
                Text("You can type a point or a comma either way. This sets how weights are shown.")
                    .foregroundStyle(.inkSoft)
            }

            Section {
                AppearanceRows(settings: $model.settings)
            } header: {
                SectionCaption(".appearance")
            }

            Section {
                BackupRows(flow: backupFlow)
            } header: {
                SectionCaption(".backups")
            } footer: {
                VStack(alignment: .leading, spacing: 6) {
                    if let problem = model.backups.problem {
                        Text("The last backup didn't work. \(problem)")
                            .foregroundStyle(.red)
                    }
                    Text(backupSummary)
                        .foregroundStyle(.inkSoft)
                }
            }

            Section {
                Button("Reset Kitae", role: .destructive) {
                    confirmsReset = true
                }
                .disabled(model.backups.isWorking)
                .listRowBackground(Color.card)
            } header: {
                SectionCaption(".reset")
            } footer: {
                Text("Deletes all plans, workouts, exercises, weigh-ins and settings, like a fresh install. Backups are kept.")
                    .foregroundStyle(.inkSoft)
            }

            Section {
                LabeledContent("Version") {
                    Text(Self.version)
                        .font(.kitaeMono(15, relativeTo: .body))
                        .foregroundStyle(.inkSoft)
                }
                .listRowBackground(Color.card)

                Link(destination: Self.sourceCode) {
                    HStack {
                        Text("Source code")
                            .foregroundStyle(.ink)
                        Spacer()
                        Image(systemName: "arrow.up.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.inkFaint)
                    }
                    .contentShape(.rect)
                }
                .accessibilityHint("Opens Kitae's code on GitHub")
                .listRowBackground(Color.card)
            } header: {
                SectionCaption(".about")
            }
        }
        .listSectionSpacing(20)
        .environment(\.defaultMinListRowHeight, 52)
        .scrollContentBackground(.hidden)
        .background(Color.paper)
        .navigationTitle("Settings")
        .backupFlow(backupFlow)
        .confirmationDialog("Reset Kitae?", isPresented: $confirmsReset, titleVisibility: .visible) {
            Button("Reset", role: .destructive) {
                // Once the question has gone, as every screen is made again.
                Task {
                    try? await Task.sleep(for: .milliseconds(350))
                    reset()
                }
            }
        } message: {
            Text("This permanently deletes all your plans, workouts, exercises, weigh-ins and settings, and Kitae starts again with its starter plan and exercises. Backups in your backup folder are kept.")
        }
        .alert(
            "Couldn't reset Kitae",
            isPresented: Binding(get: { resetProblem != nil }, set: { if !$0 { resetProblem = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(resetProblem ?? "")
        }
        .sensoryFeedback(.success, trigger: model.backups.lastBackup)
        .task { await checkAlerts() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await checkAlerts() }
            }
        }
    }

    /// "Last backup: today at 6:31 PM. Kitae on Android can restore these backups too."
    private var backupSummary: String {
        let android = "Kitae on Android can restore these backups too."
        guard let last = model.backups.lastBackup else { return "No backups yet. \(android)" }
        let format = DateFormatter()
        format.dateStyle = .medium
        format.timeStyle = .short
        format.doesRelativeDateFormatting = true
        format.formattingContext = .middleOfSentence
        return "Last backup: \(format.string(from: last)). \(android)"
    }

    /// Starts over. Kitae's screens are made again, so this one goes away.
    private func reset() {
        do {
            try model.reset()
        } catch {
            resetProblem = error.localizedDescription
        }
    }

    /// "1.0 (1)": the version, and the build TestFlight counts.
    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        guard let build = info?["CFBundleVersion"] as? String else { return version }
        return "\(version) (\(build))"
    }

    private func checkAlerts() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        alertsDenied = settings.authorizationStatus == .denied
    }
}
