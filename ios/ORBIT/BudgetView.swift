import SwiftUI
import SwiftData

struct BudgetView: View {
    @Environment(\.modelContext) private var context
    @Environment(PlannerState.self) private var planner
    @Query(sort: \Trip.createdAt, order: .reverse) private var trips: [Trip]
    @Query private var activities: [ActivityOption]
    @Query private var allItems: [ItineraryItem]
    @Query(sort: \Expense.createdAt, order: .reverse) private var allExpenses: [Expense]
    @State private var showAdd = false
    var embedded = false

    var body: some View {
        NavWrap(embedded: embedded) {
            ScrollView {
                if let trip = currentTrip(trips, planner) {
                    content(for: trip)
                } else {
                    InfoCard(title: "No trip yet", message: "Plan a trip to start tracking expenses.").padding(16)
                }
            }
            .navigationTitle("Budget")
        }
    }

    @ViewBuilder
    private func content(for trip: Trip) -> some View {
        let expenses = allExpenses.filter { $0.tripId == trip.id }
        let byId = Dictionary(uniqueKeysWithValues: activities.map { ($0.id, $0) })
        let planned = allItems.filter { $0.tripId == trip.id }
            .reduce(0) { $0 + (byId[$1.activityId]?.estimatedCost ?? 0) } * trip.travellers
        let spent = expenses.reduce(0) { $0 + $1.amount }
        let remaining = trip.budget - spent

        VStack(alignment: .leading, spacing: 10) {
            InfoCard(title: "Budget \(bdt(trip.budget))",
                     message: "Planned activities: \(bdt(planned))\nSpent: \(bdt(spent))\nRemaining: \(bdt(remaining))")
            ProgressView(value: trip.budget == 0 ? 0 : min(max(Double(spent) / Double(trip.budget), 0), 1))
                .tint(remaining < 0 ? Theme.rose : Theme.orange)

            SectionLabel("BY CATEGORY")
            ForEach(expenseCategories, id: \.self) { c in
                HStack {
                    Text(c); Spacer()
                    Text(bdt(expenses.filter { $0.category == c }.reduce(0) { $0 + $1.amount })).fontWeight(.semibold)
                }
            }
            Button { showAdd = true } label: { Text("Add expense").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).tint(Theme.navy)

            SectionLabel("EXPENSES")
            ForEach(expenses.indices, id: \.self) { i in
                InfoCard(title: "\(expenses[i].category): \(bdt(expenses[i].amount))",
                         message: expenses[i].note.isEmpty ? "No note" : expenses[i].note)
            }
        }
        .padding(16)
        .sheet(isPresented: $showAdd) {
            AddExpenseSheet { category, amount, note in
                context.insert(Expense(tripId: trip.id, category: category, amount: amount, note: note))
                try? context.save()
            }
        }
    }
}

private struct AddExpenseSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var category = expenseCategories[0]
    @State private var amount = ""
    @State private var note = ""
    let onSave: (String, Int, String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Picker("Category", selection: $category) {
                    ForEach(expenseCategories, id: \.self) { Text($0) }
                }
                TextField("Amount (BDT)", text: $amount).keyboardType(.numberPad)
                TextField("Note (optional)", text: $note)
            }
            .navigationTitle("Add expense")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if let v = Int(amount), v > 0 { onSave(category, v, String(note.prefix(60))); dismiss() }
                    }
                    .disabled((Int(amount) ?? 0) <= 0)
                }
            }
        }
    }
}
