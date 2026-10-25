import SwiftUI

struct PlanView: View {
    @Environment(PlannerState.self) private var planner
    let onFind: () -> Void

    var body: some View {
        @Bindable var planner = planner
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SectionLabel("TOUR STYLE")
                    Picker("Tour style", selection: $planner.query.tier) {
                        ForEach(Tier.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Text(planner.query.tier.blurb).font(.footnote).foregroundStyle(.secondary)
                    Stepper("Days: \(planner.query.days)", value: $planner.query.days, in: 1...7)
                    Stepper("Travellers: \(planner.query.travellers)", value: $planner.query.travellers, in: 1...10)

                    SectionLabel("BUDGET: \(bdt(planner.query.budget))")
                    Slider(value: Binding(
                        get: { Double(planner.query.budget) },
                        set: { planner.query.budget = Int(($0 / 500).rounded()) * 500 }
                    ), in: 5_000...100_000)

                    SectionLabel("TRAVEL MONTH")
                    ChipFlow(items: Array(1...12)) { m in
                        Chip(label: monthName(m), selected: planner.query.month == m) { planner.query.month = m }
                    }

                    SectionLabel("INTERESTS")
                    ChipFlow(items: interestOptions) { tag in
                        Chip(label: tag.capitalized, selected: planner.query.interests.contains(tag)) {
                            if planner.query.interests.contains(tag) { planner.query.interests.remove(tag) }
                            else { planner.query.interests.insert(tag) }
                        }
                    }

                    Button(action: onFind) {
                        Text("Find destinations").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).tint(Theme.navy).controlSize(.large)
                }
                .padding(16)
            }
            .navigationTitle("Plan your trip")
        }
    }
}
