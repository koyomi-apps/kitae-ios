// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing

/// What App Store Connect checks in every build, so a TestFlight upload isn't turned away.
struct AppBundleTests {
    @Test func theAppHasItsIcon() throws {
        let icons = try #require(Bundle.main.object(forInfoDictionaryKey: "CFBundleIcons") as? [String: Any])
        let primary = try #require(icons["CFBundlePrimaryIcon"] as? [String: Any])
        #expect(primary["CFBundleIconName"] as? String == "AppIcon")
    }

    @Test func theAppSaysWhatItDoesWithPrivateData() throws {
        let url = try #require(Bundle.main.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"))
        let manifest = try #require(
            try PropertyListSerialization.propertyList(from: Data(contentsOf: url), format: nil) as? [String: Any]
        )
        #expect(manifest["NSPrivacyTracking"] as? Bool == false)
        #expect((manifest["NSPrivacyCollectedDataTypes"] as? [Any])?.isEmpty == true)
        let apis = try #require(manifest["NSPrivacyAccessedAPITypes"] as? [[String: Any]])
        #expect(apis.map { $0["NSPrivacyAccessedAPIType"] as? String } == ["NSPrivacyAccessedAPICategoryUserDefaults"])
    }

    @Test func itUsesNoEncryptionToDeclare() {
        #expect(Bundle.main.object(forInfoDictionaryKey: "ITSAppUsesNonExemptEncryption") as? Bool == false)
    }
}
