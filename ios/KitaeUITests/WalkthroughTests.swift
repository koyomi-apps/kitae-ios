// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import XCTest

/// A first workout from start to finish, as someone new would do it: the welcome, choosing a plan,
/// ticking sets with the rest timer, a heavier set, one more set than planned, finishing, and then
/// History, Exercises and You.
///
/// GitHub's Mac records it as a video (`.github/workflows/walkthrough.yml`). That sets
/// `KAMAE_WALKTHROUGH_PAUSE`, the seconds to wait after each step so the video can be followed,
/// and `KAMAE_FRESH_SIMULATOR`, as its simulator has never had the app, so the app starts as it
/// would for someone new. Anywhere else the test goes straight through, with example data in
/// memory (`-KitaeScreen welcome`), so it leaves the simulator's own workouts alone.
final class WalkthroughTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testFirstWorkout() throws {
        let environment = ProcessInfo.processInfo.environment
        let fresh = environment["KAMAE_FRESH_SIMULATOR"] == "1"
        let app = XCUIApplication()
        if !fresh {
            app.launchArguments = ["-KitaeScreen", "welcome"]
        }
        let walk = Walk(app: app, pause: Double(environment["KAMAE_WALKTHROUGH_PAUSE"] ?? "") ?? 0)
        app.launch()

        walk.step("The welcome")
        walk.find("the welcome", app.staticTexts["Welcome to Kamae"], timeout: 40)
        walk.wait(2)
        walk.scrollDown()
        walk.scrollUp()
        walk.tap("Get started", walk.button("Get started"))

        walk.step("Choosing the plan Kamae comes with")
        walk.find("the plans", app.navigationBars["Choose a plan"], app.staticTexts["Choose a plan"])
        walk.wait()
        walk.tap("Push Pull Leg", walk.button(startingWith: "Push Pull Leg"))

        walk.step("Today: the plan's first day")
        walk.find("Start", walk.button(startingWith: "Start"))
        walk.wait()
        walk.scrollDown()
        walk.scrollUp()
        walk.tap("Start", walk.button(startingWith: "Start"))

        walk.step("A set done starts the rest timer")
        walk.find("the workout", walk.button("Finish"))
        walk.wait()
        walk.tap("set 01", walk.button("Mark set 01 done"))
        if fresh {
            // The first rest asks to send alerts for when it's over.
            let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.buttons["Allow"]
            if allow.waitForExistence(timeout: 8) {
                walk.wait()
                allow.tap()
                walk.wait()
            } else {
                walk.note("no question about alerts")
            }
        }
        walk.find("the rest timer", walk.button("Skip rest"))
        walk.wait(2)
        walk.tap("15 seconds more", walk.button("15 seconds more"))
        walk.tap("Pause rest", walk.button("Pause rest"))
        walk.tap("Resume rest", walk.button("Resume rest"))

        walk.step("The rest goes on outside the app")
        XCUIDevice.shared.press(.home)
        walk.wait(3)
        app.activate()
        walk.find("the workout again", walk.button("Skip rest"))
        walk.wait()
        walk.skipRest()

        walk.step("A heavier second set, with the steps above the keyboard")
        // The weight is just left of the set's tick.
        let tick = walk.find("set 02", walk.button("Mark set 02 done"))
        tick.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.5)).withOffset(CGVector(dx: -60, dy: 0)).tap()
        if let plus = walk.optional(walk.button(startingWith: "Plus 2.5")) {
            walk.wait()
            plus.tap()
            walk.wait()
            walk.tap("Done typing", walk.button("Done"))
        } else {
            walk.note("no steps above the keyboard")
            walk.showScreen()
        }
        walk.tap("set 02", walk.button("Mark set 02 done"))
        walk.skipRest()
        walk.tap("set 03", walk.button("Mark set 03 done"))
        walk.skipRest()

        walk.step("One more set than planned")
        walk.tap("a new set", walk.button("Add a set to Incline Bench"))
        walk.tap("set 04", walk.button("Mark set 04 done"))
        walk.skipRest()

        walk.step("How to do it, and how it's gone, from the exercise's name")
        walk.tap("Incline Bench", walk.button(startingWith: "Incline Bench"))
        walk.find("the exercise", app.staticTexts["How to do it"])
        walk.wait(2)
        walk.scrollDown()
        walk.wait()
        walk.back(to: "Back")

        walk.step("The next exercise")
        walk.scrollDown()
        // Incline Bench's first set is done, so this is Chest Fly's.
        walk.tap("Chest Fly's set 01", walk.button("Mark set 01 done"))
        walk.skipRest()

        walk.step("The workout keeps going under the tabs")
        walk.tap("Minimize workout", walk.button("Minimize workout"))
        walk.find("the workout under the tabs", walk.button(containing: " in progress"))
        walk.wait(2)
        walk.tap("the workout under the tabs", walk.button(containing: " in progress"))

        walk.step("Finishing, with sets left and one added")
        walk.tap("Finish", walk.button("Finish"))
        walk.tap("Keep as skipped", walk.button("Keep as skipped"))
        walk.tap("Only this workout", walk.button("Only this workout"))

        walk.step("The summary")
        walk.find("the summary", walk.button("Done"))
        walk.wait(2)
        walk.scrollDown()
        walk.tap("Done", walk.button("Done"))

        walk.step("Today: the next day is up")
        walk.wait(2)
        walk.scrollDown()
        walk.scrollUp()

        walk.step("History")
        walk.tab("History")
        walk.tap("the workout", walk.button(containing: "Push A"))
        walk.wait(2)
        walk.scrollDown()
        walk.back(to: "History")

        walk.step("Exercises")
        walk.tab("Exercises")
        let search = walk.find("the search", app.searchFields.firstMatch)
        walk.wait()
        search.tap()
        search.typeText("incline")
        walk.wait()
        walk.tap("Incline Bench", walk.row(startingWith: "Incline Bench"))
        walk.find("Incline Bench's page", app.staticTexts["How to do it"])
        walk.wait(2)
        walk.scrollDown()
        walk.scrollDown()
        walk.back(to: "Exercises")
        search.tap()
        search.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 12) + "deadlift")
        walk.wait()
        walk.tap("Deadlift", walk.row(named: "Deadlift"))
        walk.find("Deadlift's page", app.staticTexts["How to do it"])
        walk.wait(3)
        walk.scrollDown()
        walk.wait()
        walk.back(to: "Exercises")

        walk.step("You and Settings")
        walk.tab("You")
        walk.find("You", walk.button("Settings"))
        walk.wait(2)
        walk.scrollDown()
        walk.scrollUp()
        walk.tap("Settings", walk.button("Settings"))
        walk.find("Settings", app.navigationBars["Settings"])
        walk.wait()
        walk.scrollDown()
        walk.scrollDown()
        walk.wait()
        walk.step("The end")
    }
}

/// Finds things on screen, taps them and waits a moment after each, and says what's missing
/// when something doesn't show up, with everything that was on screen instead.
@MainActor
private struct Walk {
    let app: XCUIApplication
    /// Seconds to wait after each step.
    let pause: Double
    private let started = Date.now

    init(app: XCUIApplication, pause: Double) {
        self.app = app
        self.pause = pause
    }

    func step(_ name: String) {
        print(String(format: "WALKTHROUGH %6.1f s  %@", Date.now.timeIntervalSince(started), name))
    }

    func note(_ text: String) {
        print("WALKTHROUGH note: \(text)")
    }

    func showScreen() {
        print("--- on screen ---\n\(app.debugDescription)")
    }

    func wait(_ times: Double = 1) {
        if pause > 0 {
            Thread.sleep(forTimeInterval: pause * times)
        }
    }

    func button(_ label: String) -> XCUIElement {
        app.buttons[label].firstMatch
    }

    func button(startingWith prefix: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    func button(containing text: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    /// A list row, as a button or a cell, whose label starts with this.
    func row(startingWith prefix: String) -> [XCUIElement] {
        let predicate = NSPredicate(format: "label BEGINSWITH %@", prefix)
        return [
            app.buttons.matching(predicate).firstMatch,
            app.cells.containing(predicate).firstMatch,
            app.staticTexts.matching(predicate).firstMatch,
        ]
    }

    /// A list row, as a button or a cell, with just this name.
    func row(named name: String) -> [XCUIElement] {
        [app.buttons[name].firstMatch, app.cells.containing(.staticText, identifier: name).firstMatch,
         app.staticTexts[name].firstMatch]
    }

    /// The first of these to show up within the time.
    @discardableResult
    func find(_ what: String, _ candidates: XCUIElement..., timeout: TimeInterval = 15,
              file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        find(what, candidates, timeout: timeout, file: file, line: line)
    }

    @discardableResult
    func find(_ what: String, _ candidates: [XCUIElement], timeout: TimeInterval = 15,
              file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        if let found = optional(candidates, timeout: timeout) {
            return found
        }
        showScreen()
        XCTFail("\(what) didn't show up", file: file, line: line)
        return candidates[0]
    }

    func optional(_ candidates: XCUIElement..., timeout: TimeInterval = 5) -> XCUIElement? {
        optional(candidates, timeout: timeout)
    }

    func optional(_ candidates: [XCUIElement], timeout: TimeInterval) -> XCUIElement? {
        let deadline = Date.now.addingTimeInterval(timeout)
        repeat {
            if let found = candidates.first(where: { $0.exists }) {
                return found
            }
            Thread.sleep(forTimeInterval: 0.25)
        } while Date.now < deadline
        return nil
    }

    func tap(_ what: String, _ candidates: XCUIElement..., file: StaticString = #filePath, line: UInt = #line) {
        tap(what, candidates, file: file, line: line)
    }

    func tap(_ what: String, _ candidates: [XCUIElement], file: StaticString = #filePath, line: UInt = #line) {
        find(what, candidates, file: file, line: line).tap()
        wait()
    }

    /// Skips the rest that ticking a set started, if it did.
    func skipRest() {
        if let skip = optional(button("Skip rest"), timeout: 4) {
            wait(0.5)
            skip.tap()
            wait()
        } else {
            note("no rest to skip")
        }
    }

    func scrollDown() {
        app.swipeUp(velocity: .slow)
        wait(0.5)
    }

    func scrollUp() {
        app.swipeDown(velocity: .slow)
        wait(0.5)
    }

    /// Back to the page before, by its back button or a swipe from the edge.
    func back(to title: String) {
        if let button = optional(app.navigationBars.buttons[title].firstMatch,
                                 app.navigationBars.buttons["Back"].firstMatch, timeout: 3) {
            button.tap()
        } else {
            note("no back button called \(title), swiping back")
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.45))
                .press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.45)))
        }
        wait()
    }

    /// A tab, putting the keyboard away and bringing the tab bar back first if scrolling shrank it.
    func tab(_ name: String) {
        let tab = app.tabBars.buttons[name].firstMatch
        if app.keyboards.firstMatch.exists, app.buttons["Cancel"].firstMatch.exists {
            app.buttons["Cancel"].firstMatch.tap()
            wait(0.5)
        }
        if !(tab.exists && tab.isHittable) {
            app.swipeDown(velocity: .fast)
        }
        tap("the \(name) tab", tab, app.buttons[name].firstMatch)
    }
}
