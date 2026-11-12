// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
@testable import Kitae

struct SetLabelTests {
    @Test func standardSetsAreNumberedOnTheirOwn() {
        #expect(setLabels(for: [.warmup, .standard, .standard, .failure]) == ["W1", "01", "02", "F1"])
    }

    @Test func eachTypeHasItsOwnCount() {
        #expect(setLabels(for: [.warmup, .warmup, .standard, .drop, .drop, .restPause]) == ["W1", "W2", "01", "D1", "D2", "R1"])
    }

    @Test func tenthStandardSetHasNoLeadingZero() {
        let labels = setLabels(for: Array(repeating: .standard, count: 10))
        #expect(labels.first == "01")
        #expect(labels.last == "10")
    }
}

struct MoveTests {
    let items = ["a", "b", "c", "d", "e"]

    @Test func movesLikeAList() {
        #expect(moved(items, from: [0], to: 2) == ["b", "a", "c", "d", "e"])
        #expect(moved(items, from: [4], to: 0) == ["e", "a", "b", "c", "d"])
        #expect(moved(items, from: [1], to: 4) == ["a", "c", "d", "b", "e"])
        #expect(moved(items, from: [1, 3], to: 5) == ["a", "c", "e", "b", "d"])
        #expect(moved(items, from: [2], to: 2) == items)
        #expect(moved(items, from: [2], to: 3) == items)
    }
}

struct WeightFormatterTests {
    let point = WeightFormatter(decimals: .point)

    @Test func showsOneOrTwoDecimals() {
        #expect(point.string(kilograms: 20) == "20.0")
        #expect(point.string(kilograms: 22.5) == "22.5")
        #expect(point.string(kilograms: 18.75) == "18.75")
        #expect(point.string(kilograms: 1.05) == "1.05")
        #expect(point.string(kilograms: 0) == "0.0")
    }

    @Test func commaStyle() {
        #expect(WeightFormatter(decimals: .comma).string(kilograms: 22.5) == "22,5")
    }

    @Test func automaticFollowsTheRegion() {
        #expect(WeightFormatter(decimals: .automatic, locale: Locale(identifier: "de_DE")).string(kilograms: 22.5) == "22,5")
        #expect(WeightFormatter(decimals: .automatic, locale: Locale(identifier: "en_CA")).string(kilograms: 22.5) == "22.5")
    }

    @Test func keyboardStepsKeepTheDecimalStyle() {
        #expect(point.step(-2.5) == "−2.5")
        #expect(point.step(5) == "+5")
        #expect(WeightFormatter(decimals: .comma).step(2.5) == "+2,5")
    }

    @Test func readsAPointOrAComma() {
        #expect(point.kilograms(from: "22,5") == 22.5)
        #expect(point.kilograms(from: "22.5") == 22.5)
        #expect(point.kilograms(from: " 7 ") == 7)
        #expect(point.kilograms(from: "22.") == 22)
    }

    @Test func rejectsWhatIsntAWeight() {
        #expect(point.kilograms(from: "") == nil)
        #expect(point.kilograms(from: "-3") == nil)
        #expect(point.kilograms(from: "abc") == nil)
    }

    @Test func pounds() throws {
        let pounds = WeightFormatter(unit: .pounds, decimals: .point)
        #expect(pounds.string(kilograms: 45 * WeightUnit.kilogramsPerPound) == "45.0")
        let kg = try #require(pounds.kilograms(from: "45"))
        #expect(abs(kg - 20.411656) < 0.0001)
    }
}

struct RestTimerTests {
    let start = Date(timeIntervalSince1970: 1_000_000)

    @Test func countsDown() {
        var timer = RestTimer()
        timer.start(seconds: 90, at: start)
        #expect(timer.remaining(at: start.addingTimeInterval(30)) == 60)
        #expect(timer.remaining(at: start.addingTimeInterval(200)) == 0)
    }

    @Test func pauseKeepsTheTimeLeft() {
        var timer = RestTimer()
        timer.start(seconds: 90, at: start)
        timer.pause(at: start.addingTimeInterval(30))
        #expect(timer.isPaused)
        #expect(timer.remaining(at: start.addingTimeInterval(500)) == 60)
        timer.resume(at: start.addingTimeInterval(500))
        #expect(timer.remaining(at: start.addingTimeInterval(510)) == 50)
    }

    @Test func addsAndRemovesTime() {
        var timer = RestTimer()
        timer.start(seconds: 90, at: start)
        timer.add(15, at: start)
        #expect(timer.remaining(at: start) == 105)
        timer.add(-15, at: start.addingTimeInterval(10))
        #expect(timer.remaining(at: start.addingTimeInterval(10)) == 80)
    }

    @Test func removingMoreThanIsLeftEndsTheRest() {
        var timer = RestTimer()
        timer.start(seconds: 10, at: start)
        timer.add(-15, at: start)
        #expect(!timer.isActive)
    }

    @Test func pausedTimeCanChangeToo() {
        var timer = RestTimer()
        timer.start(seconds: 60, at: start)
        timer.pause(at: start)
        timer.add(15, at: start.addingTimeInterval(100))
        #expect(timer.remaining(at: start.addingTimeInterval(200)) == 75)
    }
}
