// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// A few choices side by side with the picked one highlighted, like a segmented control in
/// Kitae's colours. Unlike a segmented control, its text follows the text size.
struct ChoiceBar<Value: Hashable>: View {
    let title: String
    @Binding var selection: Value
    let choices: [Value]
    let name: (Value) -> String
    var spokenName: ((Value) -> String)?
    @Namespace private var namespace

    var body: some View {
        EqualWidths(spacing: 2) {
            ForEach(choices, id: \.self) { choice in
                segment(choice)
            }
        }
        .padding(4)
        .background(.field, in: .capsule)
        .animation(.snappy(duration: 0.25), value: selection)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(title)
    }

    private func segment(_ choice: Value) -> some View {
        let isSelected = choice == selection
        return Button {
            selection = choice
        } label: {
            Text(name(choice))
                .font(.kitaeMono(14, relativeTo: .subheadline, bold: isSelected))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.horizontal, 10)
                .frame(maxWidth: .infinity, minHeight: 36)
                .frame(minWidth: 44)
                .foregroundStyle(isSelected ? Color.onOliveSoft : Color.ink)
                .background {
                    if isSelected {
                        Capsule()
                            .fill(.oliveSoft)
                            .matchedGeometryEffect(id: "selection", in: namespace)
                    }
                }
                .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityLabel((spokenName ?? name)(choice))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Lays its views out in a row, all as wide as the widest. Offered more room, it fills it.
struct EqualWidths: Layout {
    var spacing: CGFloat = 0

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard !subviews.isEmpty else { return .zero }
        let gaps = spacing * CGFloat(subviews.count - 1)
        let widest = subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        let ideal = widest * CGFloat(subviews.count) + gaps
        let width = proposal.width.flatMap { $0.isFinite ? $0 : nil } ?? ideal
        let each = max((width - gaps) / CGFloat(subviews.count), 0)
        let height = subviews.map { $0.sizeThatFits(ProposedViewSize(width: each, height: nil)).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard !subviews.isEmpty else { return }
        let gaps = spacing * CGFloat(subviews.count - 1)
        let each = max((bounds.width - gaps) / CGFloat(subviews.count), 0)
        for (index, subview) in subviews.enumerated() {
            subview.place(
                at: CGPoint(x: bounds.minX + CGFloat(index) * (each + spacing), y: bounds.minY),
                proposal: ProposedViewSize(width: each, height: bounds.height)
            )
        }
    }
}
