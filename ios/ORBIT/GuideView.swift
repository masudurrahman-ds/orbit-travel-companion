import SwiftUI
import SwiftData

struct ChatMsg: Identifiable {
    let id = UUID()
    let fromUser: Bool
    let text: String
    var cloudPlan: [String: Any]? = nil
    var brainPlan: BrainPlan? = nil
    var brainQuery: BrainQuery? = nil
}

/// Chat state for the guide. The default guide is ORBIT Brain, which runs on this phone: no account, no network,
/// nothing leaves the device. The optional cloud guide is used only if the build enables it and the traveller opted in.
@MainActor
@Observable
final class GuideState {
    var messages: [ChatMsg] = [ChatMsg(fromUser: false, text: "I am your ORBIT guide. Tell me what you want, or tap a trip style below. I will use sensible defaults instead of asking.")]
    var busy = false
    private var brainState = BrainState()
    private let brain = Brain()

    func reset() { messages = Array(messages.prefix(1)); brainState = BrainState() }

    func ask(_ text: String, session: AppSession, context: ModelContext, planner: PlannerState,
             location: (Double, Double)? = nil, targetId: Int? = nil) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !busy else { return }
        messages.append(ChatMsg(fromUser: true, text: trimmed))
        busy = true
        defer { busy = false }

        if Config.useCloudAI, session.signedIn, session.user?.consent.ai == true,
           let reply = await cloudReply(trimmed, session: session, location: location, targetId: targetId) {
            messages.append(reply); return
        }
        messages.append(await nativeReply(trimmed, context: context, planner: planner, location: location, targetId: targetId))
    }

    private func nativeReply(_ text: String, context: ModelContext, planner: PlannerState, location: (Double, Double)?, targetId: Int?) async -> ChatMsg {
        let destinations = (try? context.fetch(FetchDescriptor<Destination>(sortBy: [SortDescriptor(\.id)]))) ?? []
        let activities = (try? context.fetch(FetchDescriptor<ActivityOption>(sortBy: [SortDescriptor(\.id)]))) ?? []
        let visits = (try? context.fetch(FetchDescriptor<Visit>(sortBy: [SortDescriptor(\.visitedAt)]))) ?? []
        let notes = (try? context.fetch(FetchDescriptor<MemoryNote>(sortBy: [SortDescriptor(\.createdAt)]))) ?? []
        let ctx = BrainContext(destinations: destinations, activities: activities, visitedIds: visits.map(\.destinationId),
                               memory: notes.map(\.text), month: Calendar.current.component(.month, from: Date()),
                               location: location.map { BrainLocation(lat: $0.0, lon: $0.1, targetId: targetId) })
        var r = brain.respond(text, ctx, brainState)
        // Optional language layer: only when ORBIT Brain did not understand, and only if the traveller turned it on.
        let priorContext: String = {
            var parts: [String] = []
            if let q = brainState.lastQuery {
                parts.append("Last request: \(q.days) days, BDT \(q.budget), \(q.travellers) travellers, interests \(q.interests.joined(separator: ", ")), style \(q.tier.rawValue).")
            }
            if let id = brainState.lastPlaceId, let destination = destinations.first(where: { $0.id == id }) {
                parts.append("Last destination: \(destination.name).")
            } else if let plan = brainState.lastPlan {
                parts.append("Last destination: \(plan.destinationName).")
            }
            return parts.joined(separator: " ")
        }()
        if !r.understood, LanguageAssist.enabled, LanguageAssist.available,
           let command = Translation.sanitize(await LanguageAssist.translate(text, context: priorContext)) {
            let second = brain.respond(command, ctx, brainState)
            if second.understood {
                // The model's own words are never shown.
                r = BrainReply(reply: Translation.notice + "\n" + second.reply, state: second.state, plan: second.plan, actions: second.actions)
            }
        }
        brainState = r.state
        for action in r.actions {
            switch action {
            case .remember(let note):
                if notes.count < 50 { context.insert(MemoryNote(text: note)) }
            case .forgetAll:
                notes.forEach { context.delete($0) }
            case .savePlan:
                if let plan = r.state.lastPlan { saveBrainPlan(plan, r.state.lastQuery, context: context, planner: planner) }
            }
        }
        try? context.save()
        return ChatMsg(fromUser: false, text: r.reply, brainPlan: r.plan, brainQuery: r.state.lastQuery)
    }

    /// Saves a plan from ORBIT Brain as a local trip.
    func saveBrainPlan(_ plan: BrainPlan, _ query: BrainQuery?, context: ModelContext, planner: PlannerState) {
        let q = query ?? BrainQuery(budget: planner.query.budget, days: plan.days, travellers: planner.query.travellers,
                                    month: planner.query.month, interests: planner.query.interests.sorted(), tier: plan.tier)
        let trip = Trip(title: plan.destinationName, destinationId: plan.destinationId, startMonth: q.month, days: plan.days,
                        budget: q.budget, travellers: q.travellers, interests: q.interests.joined(separator: ","), tier: plan.tier.rawValue)
        context.insert(trip)
        for item in plan.items { context.insert(ItineraryItem(tripId: trip.id, activityId: item.activityId, dayNo: item.day, startHour: item.hour)) }
        try? context.save()
        planner.currentTripId = trip.id
    }

    private func cloudReply(_ text: String, session: AppSession, location: (Double, Double)?, targetId: Int?) async -> ChatMsg? {
        let history: [[String: String]] = messages.dropFirst().dropLast().suffix(10).map { ["role": $0.fromUser ? "user" : "assistant", "content": $0.text] }
        var body: [String: Any] = ["message": text, "history": history]
        if let location {
            var loc: [String: Any] = ["lat": location.0, "lon": location.1]
            if let targetId { loc["targetId"] = targetId }
            body["location"] = loc
        }
        let r = await session.request("POST", "/api/agent/chat", body: body)
        guard r.ok else { return nil }
        return ChatMsg(fromUser: false, text: r.json?["reply"] as? String ?? "", cloudPlan: r.json?["plan"] as? [String: Any])
    }
}

struct GuideView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSession.self) private var session
    @Environment(GuideState.self) private var guide
    @Environment(PlannerState.self) private var planner
    @Query private var activities: [ActivityOption]
    @Query private var destinations: [Destination]
    let onPlanSaved: () -> Void
    @State private var input = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(guide.messages) { m in bubble(m).id(m.id) }
                            if guide.busy { Text("Thinking...").font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading) }
                        }
                        .padding(16)
                    }
                    .onChange(of: guide.messages.count) { _, _ in if let last = guide.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } } }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        chip("Budget trip", "Plan a budget-friendly trip within BDT \(planner.query.budget).")
                        chip("Affordable, quality", "Plan an affordable but high-quality trip within BDT \(planner.query.budget).")
                        chip("Fancy trip", "Plan a fancy, comfort-first trip.")
                        chip("Next new place", "Suggest the best place I have not visited yet.")
                    }.padding(.horizontal, 16)
                }
                HStack {
                    TextField("Ask your guide", text: $input, axis: .vertical).lineLimit(1...3).textFieldStyle(.roundedBorder)
                    Button("Send") { send(input); input = "" }.disabled(input.trimmingCharacters(in: .whitespaces).isEmpty || guide.busy)
                }
                .padding(12)
            }
            .navigationTitle("Guide")
        }
    }

    private func send(_ text: String) {
        Task { await guide.ask(text, session: session, context: context, planner: planner) }
    }

    private func chip(_ title: String, _ prompt: String) -> some View {
        Button(title) { send(prompt) }.buttonStyle(.bordered).tint(Theme.navy).disabled(guide.busy)
    }

    @ViewBuilder
    private func bubble(_ m: ChatMsg) -> some View {
        HStack {
            if m.fromUser { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 8) {
                Text(m.text).foregroundStyle(m.fromUser ? Color.white : Color.primary)
                if let plan = m.brainPlan {
                    Button("Save this plan as my trip") { guide.saveBrainPlan(plan, m.brainQuery, context: context, planner: planner); onPlanSaved() }
                        .buttonStyle(.borderedProminent).tint(Theme.navy)
                }
                if let plan = m.cloudPlan {
                    Button("Save this plan as my trip") { saveCloudPlan(plan) }.buttonStyle(.borderedProminent).tint(Theme.navy)
                }
            }
            .padding(14)
            .background(m.fromUser ? Theme.navy : Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18))
            if !m.fromUser { Spacer(minLength: 40) }
        }
    }

    /// Saves a plan from the optional cloud guide. Activity ids are checked against local data.
    private func saveCloudPlan(_ plan: [String: Any]) {
        guard let destId = (plan["destination"] as? [String: Any])?["id"] as? Int,
              let dest = destinations.first(where: { $0.id == destId }) else { return }
        let q = planner.query
        let tier = Tier(rawValue: (plan["tier"] as? String ?? "").lowercased()) ?? q.tier
        let days = min(max(plan["days"] as? Int ?? q.days, 1), 14)
        let trip = Trip(title: dest.name, destinationId: dest.id, startMonth: q.month, days: days, budget: q.budget,
                        travellers: q.travellers, interests: q.interests.sorted().joined(separator: ","), tier: tier.rawValue)
        context.insert(trip)
        let valid = Set(activities.filter { $0.destinationId == dest.id }.map(\.id))
        for item in (plan["items"] as? [[String: Any]]) ?? [] {
            guard let aid = item["activityId"] as? Int, valid.contains(aid), let day = item["day"] as? Int, let hour = item["hour"] as? Int else { continue }
            context.insert(ItineraryItem(tripId: trip.id, activityId: aid, dayNo: day, startHour: hour))
        }
        try? context.save()
        planner.currentTripId = trip.id
        onPlanSaved()
    }
}
