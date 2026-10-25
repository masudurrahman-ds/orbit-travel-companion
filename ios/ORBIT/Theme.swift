import SwiftUI

enum Theme {
    static let navy = Color(red: 0x12 / 255, green: 0x35 / 255, blue: 0x5B / 255)
    static let teal = Color(red: 0x0E / 255, green: 0x8A / 255, blue: 0x9B / 255)
    static let orange = Color(red: 0xF2 / 255, green: 0x8C / 255, blue: 0x28 / 255)
    static let purple = Color(red: 0x6C / 255, green: 0x4D / 255, blue: 0xC4 / 255)
    static let green = Color(red: 0x2E / 255, green: 0x9E / 255, blue: 0x5B / 255)
    static let rose = Color(red: 0xD9 / 255, green: 0x45 / 255, blue: 0x5F / 255)
    static let cyan = Color(red: 0x1F / 255, green: 0xB5 / 255, blue: 0xD1 / 255)
    static let gold = Color(red: 0xC8 / 255, green: 0x96 / 255, blue: 0x2E / 255)
}

let interestOptions = ["beach", "hills", "nature", "food", "culture", "adventure"]
let expenseCategories = ["Transport", "Stay", "Food", "Activities", "Other"]
private let monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

func monthName(_ m: Int) -> String { monthNames[min(max(m, 1), 12) - 1] }
func bdt(_ amount: Int) -> String {
    let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = Locale(identifier: "en_US")
    return "BDT " + (f.string(from: NSNumber(value: amount)) ?? "\(amount)")
}

/// Offline placeholder: a real weather API is planned for a later phase.
func weatherLabel(_ dest: Destination?, month: Int) -> String {
    (dest?.monthSet.contains(month) ?? false) ? "Dry and pleasant" : "Rain possible"
}

struct InfoCard: View {
    let title: String
    let message: String
    var background: Color = Color(.secondarySystemBackground)
    var foreground: Color = .primary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.headline)
            Text(message).font(.subheadline)
        }
        .foregroundStyle(foreground)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(background, in: RoundedRectangle(cornerRadius: 14))
    }
}

struct SectionLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.footnote.weight(.semibold)).foregroundStyle(.secondary) }
}

/// Simple wrapping layout for chips.
struct ChipFlow<Item: Hashable, Content: View>: View {
    let items: [Item]
    let content: (Item) -> Content
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: 8)], alignment: .leading, spacing: 8) {
            ForEach(items, id: \.self) { content($0) }
        }
    }
}

struct Chip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.subheadline.weight(.medium))
                .padding(.horizontal, 12).padding(.vertical, 7)
                .frame(maxWidth: .infinity)
                .background(selected ? Theme.navy : Color(.systemGray5), in: Capsule())
                .foregroundStyle(selected ? Color.white : Color.primary)
        }
        .buttonStyle(.plain)
    }
}

/// Wraps a screen in its own NavigationStack unless it is already shown inside one.
struct NavWrap<Content: View>: View {
    let embedded: Bool
    @ViewBuilder let content: () -> Content
    var body: some View {
        if embedded { content() } else { NavigationStack { content() } }
    }
}
