import SwiftUI
import SwiftData

// MARK: More menu
struct MoreView: View {
    let onOpenGuide: () -> Void

    var body: some View {
        NavigationStack {
            List {
                row("Itinerary", "Day-by-day plan for your current trip") { ItineraryView(embedded: true) }
                row("Budget", "Track spending against your budget") { BudgetView(embedded: true) }
                row("Explore", "Browse and rank all spots") { ExploreView(embedded: true, onPlanned: {}) }
                row("Live map", "Trace your journey on a live map") { LiveMapView(embedded: true, onOpenGuide: onOpenGuide) }
                row("Photo editor", "Edit your travel photos instantly") { PhotoEditorView(embedded: true) }
                row("Rankings", "See who has visited the most") { RankingView() }
                row("Add a spot", "Suggest a spot we are missing") { SubmitSpotView() }
                row("Account", "Sign in, privacy and your data") { AccountView() }
            }
            .navigationTitle("More")
        }
    }

    private func row<D: View>(_ title: String, _ hint: String, @ViewBuilder _ destination: @escaping () -> D) -> some View {
        NavigationLink { destination() } label: {
            VStack(alignment: .leading) { Text(title).font(.headline); Text(hint).font(.footnote).foregroundStyle(.secondary) }
        }
    }
}

// MARK: Rankings
struct RankingView: View {
    @Environment(AppSession.self) private var session
    @State private var rows: [[String: Any]] = []
    @State private var me: [String: Any]?
    @State private var optedIn = false
    @State private var status = "Loading..."

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if !session.signedIn {
                    InfoCard(title: "Sign in to compete", message: "Rankings need an account. Create one under More, Account.")
                } else {
                    Text("Ranked by number of different spots visited, then districts. Visits are self-reported.").font(.footnote)
                    if !optedIn {
                        InfoCard(title: "You are not on the board", message: "Join to appear with your display name and visit counts. You can leave any time.")
                        Button("Join the leaderboard") { join() }.buttonStyle(.borderedProminent).tint(Theme.navy)
                    } else if let me {
                        InfoCard(title: "Your rank: #\(me["rank"] as? Int ?? 0)", message: "\(me["spots"] as? Int ?? 0) spots in \(me["districts"] as? Int ?? 0) districts")
                    }
                    if !status.isEmpty { Text(status) }
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, r in
                        let isMe = r["isMe"] as? Bool ?? false
                        HStack {
                            Text("#\(r["rank"] as? Int ?? 0)  \(r["displayName"] as? String ?? "")" + (isMe ? "  (you)" : "")).fontWeight(isMe ? .bold : .regular)
                            Spacer()
                            Text("\(r["spots"] as? Int ?? 0) spots | \(r["districts"] as? Int ?? 0) districts").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle("Rankings")
        .task { await load() }
    }

    private func load() async {
        guard session.signedIn else { return }
        let r = await session.request("GET", "/api/leaderboard")
        guard r.ok else { status = r.status == 0 ? "Cannot reach the server." : "Could not load rankings."; return }
        rows = r.json?["top"] as? [[String: Any]] ?? []
        me = r.json?["me"] as? [String: Any]
        optedIn = r.json?["optedIn"] as? Bool ?? false
        status = rows.isEmpty ? "No travellers on the board yet." : ""
    }

    private func join() {
        guard var c = session.user?.consent else { return }
        c.leaderboard = true
        Task { _ = await session.updateConsent(c); await load() }
    }
}

// MARK: Submit a spot
struct SubmitSpotView: View {
    @Environment(AppSession.self) private var session
    @Query private var destinations: [Destination]
    @State private var name = ""
    @State private var district = ""
    @State private var tags: Set<String> = []
    @State private var cost = ""
    @State private var desc = ""
    @State private var busy = false
    @State private var result: String?

    private var districts: [String] { Array(Set(destinations.map(\.district))).sorted() }

    var body: some View {
        Form {
            if !session.signedIn {
                Text("Spot suggestions need an account so we can limit spam. Sign in under More, Account.")
            } else {
                Section { Text("Suggest a place worth visiting. New spots are reviewed before they appear for everyone.").font(.footnote) }
                Section {
                    TextField("Spot name", text: $name)
                    Picker("District", selection: $district) {
                        Text("Choose district").tag("")
                        ForEach(districts, id: \.self) { Text($0).tag($0) }
                    }
                    TextField("Typical cost per person per day (BDT)", text: $cost).keyboardType(.numberPad)
                    TextField("What makes it special?", text: $desc, axis: .vertical).lineLimit(1...4)
                }
                Section("Tags (1 to 3)") {
                    ForEach(interestOptions, id: \.self) { t in
                        Button { toggle(t) } label: { HStack { Text(t.capitalized); Spacer(); if tags.contains(t) { Image(systemName: "checkmark") } } }.foregroundStyle(.primary)
                    }
                }
                if let result { Section { Text(result).fontWeight(.semibold) } }
                Section {
                    Button(busy ? "Sending..." : "Submit for review") { submit() }
                        .disabled(busy || name.trimmingCharacters(in: .whitespaces).count < 3 || district.isEmpty || tags.isEmpty || Int(cost) == nil)
                }
            }
        }
        .navigationTitle("Add a spot")
    }

    private func toggle(_ t: String) { if tags.contains(t) { tags.remove(t) } else if tags.count < 3 { tags.insert(t) } }

    private func submit() {
        busy = true; result = nil
        let body: [String: Any] = ["name": name.trimmingCharacters(in: .whitespaces), "district": district, "tags": Array(tags), "costPerDayBdt": Int(cost) ?? 0, "description": desc]
        Task {
            let r = await session.request("POST", "/api/destinations/submit", body: body)
            busy = false
            if r.ok { result = "Thanks. Your spot will appear after review."; name = ""; desc = ""; tags = []; cost = "" }
            else { result = r.message ?? (r.error == "duplicate" ? "That spot is already listed." : r.status == 0 ? "Cannot reach the server." : "Could not submit. Check the fields.") }
        }
    }
}

// MARK: Account & privacy
struct AccountView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSession.self) private var session
    @Environment(GuideState.self) private var guide
    @Query(sort: \MemoryNote.createdAt) private var notes: [MemoryNote]
    @State private var confirmDelete = false
    @State private var info: String?
    @State private var exportText: String?
    @AppStorage("orbit.llm.enabled") private var llmEnabled = false

    var body: some View {
        List {
            if let u = session.user {
                Section { VStack(alignment: .leading) { Text(u.displayName).font(.headline); Text(u.identifier).font(.footnote).foregroundStyle(.secondary) } }
                Section("Data and permissions") {
                    if Config.useCloudAI {
                        consent("Cloud AI guide", "Allow chat messages, visit history and saved notes to be processed by the optional cloud AI provider.", u.consent.ai) { var c = u.consent; c.ai = $0; set(c) }
                        consent("Share location with the cloud guide", "Sent only when you ask the guide for trip help. Never stored on our server.", u.consent.location) { var c = u.consent; c.location = $0; set(c) }
                    }
                    consent("Public leaderboard", "Show your display name and visit counts to other travellers.", u.consent.leaderboard) { var c = u.consent; c.leaderboard = $0; set(c) }
                }
                Section("Your data") {
                    Button("Download my data") { export() }
                    Button("Sign out") { session.signOut(); guide.reset() }
                    Button("Delete my account", role: .destructive) { confirmDelete = true }
                }
            } else {
                Section {
                    Text("You are using ORBIT as a guest. Everything stays on this phone, including the guide. Sign in to sync visits, join rankings and submit spots.")
                    Button("Sign in or create account") { session.signOut() }
                }
            }
            Section("What the guide remembers (on this phone)") {
                if notes.isEmpty { Text("Nothing saved yet. Tell the guide things like \"remember that I prefer quiet beaches\".").font(.footnote) }
                ForEach(notes) { n in
                    HStack { Text(n.text); Spacer(); Button("Delete") { context.delete(n); try? context.save() } }
                }
                if !notes.isEmpty { Button("Forget everything") { notes.forEach { context.delete($0) }; try? context.save() } }
            }
            Section("On-device language model (optional)") {
                Text("The built-in guide already works without it. When your iPhone supports it, Apple's on-device model can add Bangla and looser wording: it only translates your message into a request the guide understands, and the guide still gives all prices and places from the app's own data. Nothing is uploaded.")
                    .font(.footnote).foregroundStyle(.secondary)
                if LanguageAssist.available {
                    Toggle("Use it for Bangla and unfamiliar messages", isOn: $llmEnabled)
                } else {
                    Text("Not available on this device. It needs iOS 26 and Apple Intelligence turned on.").font(.footnote)
                }
            }
            Section("Policies") {
                Link("Privacy Policy", destination: URL(string: session.baseURL + "/privacy")!)
                Link("Terms of Use", destination: URL(string: session.baseURL + "/terms")!)
            }
            if let info { Section { Text(info).foregroundStyle(Theme.rose) } }
        }
        .navigationTitle("Account")
        .alert("Delete account?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) {
                Task {
                    let r = await session.deleteAccount()
                    if r.ok { try? context.delete(model: Visit.self); try? context.save(); guide.reset() } else { info = "Could not delete the account. Try again." }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: { Text("This permanently removes your account, visits and saved guide notes from our server and clears visits on this phone. It cannot be undone.") }
        .sheet(item: Binding(get: { exportText.map { ExportItem(text: $0) } }, set: { if $0 == nil { exportText = nil } })) { item in
            ShareSheet(text: item.text)
        }
    }

    private struct ExportItem: Identifiable { let id = UUID(); let text: String }

    private func consent(_ title: String, _ detail: String, _ value: Bool, _ onChange: @escaping (Bool) -> Void) -> some View {
        Toggle(isOn: Binding(get: { value }, set: onChange)) {
            VStack(alignment: .leading) { Text(title).fontWeight(.semibold); Text(detail).font(.caption).foregroundStyle(.secondary) }
        }
    }

    private func set(_ c: Consent) { Task { _ = await session.updateConsent(c) } }

    private func export() {
        Task {
            let r = await session.request("GET", "/api/me/export")
            if r.ok, let j = r.json, let d = try? JSONSerialization.data(withJSONObject: j, options: [.prettyPrinted]) { exportText = String(data: d, encoding: .utf8) }
            else { info = "Could not download your data." }
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let text: String
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: [text], applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
