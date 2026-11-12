// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI
import UniformTypeIdentifiers

/// Backups as rows of a list, in Settings and in the welcome: backing up after each workout,
/// the backup folder, backing up now and restoring. What they open and ask is put on the
/// list with `backupFlow(_:)`.
struct BackupRows: View {
    @Environment(AppModel.self) private var model
    let flow: BackupFlow
    /// Settings has it. On first launch there's nothing to back up yet.
    var offersBackUpNow = true

    var body: some View {
        @Bindable var model = model
        Toggle("Back up after each workout", isOn: $model.settings.backUpAfterWorkouts)
            .listRowBackground(Color.card)

        Button {
            flow.chooseFolder()
        } label: {
            HStack(spacing: 8) {
                Text("Backup folder")
                    .foregroundStyle(.ink)
                Spacer(minLength: 8)
                Text(model.backups.folderName)
                    .foregroundStyle(.inkSoft)
                    .lineLimit(1)
                Image(systemName: "chevron.forward")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.inkFaint)
            }
            .contentShape(.rect)
        }
        .disabled(model.backups.isWorking)
        .accessibilityLabel("Backup folder")
        .accessibilityValue(model.backups.folderName)
        .accessibilityHint("Chooses where backups are saved")
        .listRowBackground(Color.card)

        if offersBackUpNow {
            actionRow("Back up now", isRunning: flow.activity == .backingUp) {
                Task { await flow.backUpNow(model) }
            }
        }
        actionRow("Restore from a backup", isRunning: flow.activity == .restoring) {
            flow.chooseBackup(model)
        }
    }

    private func actionRow(_ title: String, isRunning: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                Spacer(minLength: 8)
                if isRunning {
                    ProgressView()
                }
            }
            .contentShape(.rect)
        }
        .disabled(model.backups.isWorking)
        .listRowBackground(Color.card)
    }
}

/// Picking a backup folder or a backup in Files, asking before a backup is restored, and
/// saying how backing up or restoring went.
@Observable
final class BackupFlow {
    /// Whether Files is open, to pick `importing`.
    var isImporting = false
    private(set) var importing = ImportKind.backup
    /// A backup picked in Files, waiting for an answer before it's restored.
    var restoring: URL?
    /// How it went, to show in an alert.
    var notice: Notice?
    private(set) var activity: BackupActivity?

    func chooseFolder() {
        importing = .folder
        isImporting = true
    }

    /// Opens Files to pick a backup, unless a workout is going.
    func chooseBackup(_ model: AppModel) {
        guard model.workout == nil else {
            notice = Notice(title: "Can't restore a backup now", message: BackupError.workoutInProgress.localizedDescription)
            return
        }
        importing = .backup
        isImporting = true
    }

    func picked(_ result: Result<URL, any Error>, model: AppModel) {
        switch result {
        case .success(let url):
            switch importing {
            case .folder:
                do {
                    try model.backups.choose(url)
                } catch {
                    notice = Notice(title: "Couldn't use that folder", message: error.localizedDescription)
                }
            case .backup:
                // Asked once Files has gone, as a question asked while it's leaving doesn't show.
                Task {
                    try? await Task.sleep(for: .milliseconds(400))
                    restoring = url
                }
            }
        case .failure(let error):
            notice = Notice(title: "Couldn't open that", message: error.localizedDescription)
        }
    }

    func backUpNow(_ model: AppModel) async {
        activity = .backingUp
        defer { activity = nil }
        guard !(await model.backUp()), let problem = model.backups.problem else { return }
        notice = Notice(title: "Couldn't back up", message: problem)
    }

    func restore(_ url: URL, model: AppModel) async {
        activity = .restoring
        defer { activity = nil }
        do {
            try await model.restoreBackup(from: url)
            notice = Notice(title: "Backup restored", message: "Kitae now has the plans, workouts and exercises from \(url.lastPathComponent).")
        } catch {
            notice = Notice(title: "Couldn't restore the backup", message: error.localizedDescription)
        }
    }
}

extension View {
    /// What the backup rows open and ask: Files, "Restore this backup?", and how it went.
    func backupFlow(_ flow: BackupFlow) -> some View {
        modifier(BackupFlowPresentations(flow: flow))
    }
}

private struct BackupFlowPresentations: ViewModifier {
    @Environment(AppModel.self) private var model
    @Bindable var flow: BackupFlow

    func body(content: Content) -> some View {
        content
            .fileImporter(isPresented: $flow.isImporting, allowedContentTypes: flow.importing.contentTypes) { result in
                flow.picked(result, model: model)
            }
            .confirmationDialog(
                "Restore this backup?",
                isPresented: Binding(get: { flow.restoring != nil }, set: { if !$0 { flow.restoring = nil } }),
                titleVisibility: .visible,
                presenting: flow.restoring
            ) { url in
                Button("Restore", role: .destructive) {
                    Task { await flow.restore(url, model: model) }
                }
            } message: { url in
                Text("Your plans, workouts, exercises, weigh-ins and settings will be replaced by the ones in \(url.lastPathComponent). What's in Kitae now is saved first, as \(Backups.beforeRestoreName) in On My iPhone › Kitae.")
            }
            .alert(
                flow.notice?.title ?? "",
                isPresented: Binding(get: { flow.notice != nil }, set: { if !$0 { flow.notice = nil } }),
                presenting: flow.notice
            ) { _ in
                Button("OK", role: .cancel) {}
            } message: { notice in
                Text(notice.message)
            }
    }
}

/// What Files is picking: a folder for backups, or a backup to restore.
enum ImportKind {
    case folder
    case backup

    var contentTypes: [UTType] {
        switch self {
        case .folder: [.folder]
        // A backup saved by some Android apps has no .zip at the end of its name.
        case .backup: [.zip, .data]
        }
    }
}

enum BackupActivity {
    case backingUp
    case restoring
}

/// An alert after backing up or restoring, or when something didn't work.
struct Notice {
    let title: String
    let message: String
}
