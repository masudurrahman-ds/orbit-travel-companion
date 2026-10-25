import Foundation

/// ORBIT Brain: the app's own travel agent. No external AI service and no network.
/// Pipeline: normalise -> extract entities -> detect intent -> apply defaults and memory -> call the planner -> write a short reply.
/// This is a line-by-line port of backend/src/brain.js (which has the tests) and shares brain-data.json.
struct Lexicon: Decodable {
    let interests: [String: [String]]
    let cheapWords, qualityWords, fancyWords: [String]
    let numberWords: [String: Int]
    let seasons: [String: Int]
    let monthNames: [String: Int]
    let triggers: [String: [String]]
    let safetyTags: [String]
    let dailyPerPerson: [String: Int]
    let firstWordStoplist: [String]
    let bangla: [String: String]
    let banglish: [String: String]
    let placeAliases: [String: String]

    static func load() -> Lexicon {
        guard let url = Bundle.main.url(forResource: "brain-data", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let lex = try? JSONDecoder().decode(Lexicon.self, from: data) else { fatalError("brain-data.json is missing from the app bundle") }
        return lex
    }
}

struct BrainQuery { var budget: Int; var days: Int; var travellers: Int; var month: Int; var interests: [String]; var tier: Tier }
struct PlanItem { let day: Int; let hour: Int; let activityId: Int }
struct BrainPlan { let destinationId: Int; let destinationName: String; let district: String; let tier: Tier; let days: Int; let items: [PlanItem]; let totalBdt: Int }
struct BrainState { var lastQuery: BrainQuery?; var lastPlaceId: Int?; var offset = 0; var lastPlan: BrainPlan? }
struct BrainLocation { let lat: Double; let lon: Double; var targetId: Int? }

enum BrainAction: Equatable { case remember(String), forgetAll, savePlan }

struct BrainContext {
    let destinations: [Destination]
    let activities: [ActivityOption]
    let visitedIds: [Int]        // oldest first
    let memory: [String]
    let month: Int
    var location: BrainLocation?
    var advisories: [BrainAdvisory] = []
}

struct BrainAdvisory { let scope: String; let target: String?; let severity: String; let title: String; let text: String }

struct BrainReply { let reply: String; let state: BrainState; let plan: BrainPlan?; let actions: [BrainAction]; var understood = true }

private struct Entities { var budget: Int?; var days: Int?; var travellers: Int?; var month: Int?; var interests: [String]; var tier: Tier? }
private struct Match { let dest: Destination; let key: String; let pos: Int; let byDistrict: Bool }
private struct Prefs { let likes: [String]; let dislikes: [String]; let tier: Tier? }

final class Brain {
    private let lex: Lexicon
    init(lexicon: Lexicon = .load()) { lex = lexicon }

    private let monthsLong = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
    private let interestOrder = ["beach", "hills", "nature", "food", "culture", "adventure"]
    private let suggestions = "Try: \"3 days beach trip under 20k\", \"cheap hills trip for 4 people\", \"affordable but good quality trip in December\", \"fancy weekend in Sylhet\", \"itinerary for Jaflong\", \"best time to visit Sajek\", \"compare Sylhet and Bandarban\", \"something I have not visited\"."

    // MARK: text helpers
    private lazy var bnWords: [(String, String)] = lex.bangla.sorted { $0.key.count > $1.key.count }.map { ($0.key, $0.value) }

    /// Bangla script and Banglish are understood offline with the word lists in brain-data.json: no model needed.
    private func prepareBangla(_ text: String) -> String {
        let digits = Array("০১২৩৪৫৬৭৮৯")
        var s = String(text.map { ch -> Character in
            if let i = digits.firstIndex(of: ch) { return Character(String(i)) }
            return ch
        })
        guard s.range(of: "[\\u0980-\\u09FF]", options: .regularExpression) != nil else { return s }
        func rx(_ pattern: String, _ template: String) { s = s.replacingOccurrences(of: pattern, with: template, options: .regularExpression) }
        rx("(\\d+(?:\\.\\d+)?)\\s*হাজার", "$1k ")
        rx("(\\d+(?:\\.\\d+)?)\\s*(?:লাখ|লক্ষ)", "$1 lakh ")
        rx("(\\d+)\\s*জন", "$1 people ")
        rx("(\\d+)\\s*দিন", "$1 days ")
        rx("(\\d+)\\s*রাত", "$1 nights ")
        rx("(\\d+)\\s*সপ্তাহ", "$1 weeks ")
        for (bn, en) in bnWords { s = s.replacingOccurrences(of: bn, with: " \(en) ") }
        return s
    }

    func normalize(_ text: String) -> String {
        var s = prepareBangla(text).lowercased().replacingOccurrences(of: "\u{2019}", with: "").replacingOccurrences(of: "'", with: "")
        s = s.replacingOccurrences(of: "৳", with: " bdt ")
        s = s.replacingOccurrences(of: "\\b(taka|tk)\\b", with: " bdt ", options: .regularExpression)
        s = s.replacingOccurrences(of: "(\\d),(\\d{3})", with: "$1$2", options: .regularExpression)
        s = s.replacingOccurrences(of: "[^a-z0-9. ]+", with: " ", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?<!\\d)\\.|\\.(?!\\d)", with: " ", options: .regularExpression)
        s = s.split(separator: " ").map { lex.numberWords[String($0)].map(String.init) ?? lex.banglish[String($0)] ?? String($0) }.joined(separator: " ")
        s = s.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
        s = " " + s + " "
        for (from, to) in lex.placeAliases { s = s.replacingOccurrences(of: " \(from) ", with: " \(to) ") }
        return s
    }

    private func has(_ norm: String, _ phrase: String) -> Bool { norm.contains(" \(phrase) ") }
    private func hasAny(_ norm: String, _ list: [String]) -> Bool { list.contains { has(norm, $0) } }
    private func trig(_ name: String) -> [String] { lex.triggers[name] ?? [] }
    private func bdt(_ n: Int) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = Locale(identifier: "en_US")
        return "BDT " + (f.string(from: NSNumber(value: n)) ?? "\(n)")
    }
    private func clamp(_ v: Double, _ lo: Int, _ hi: Int) -> Int { min(hi, max(lo, Int(v.rounded()))) }
    private func cap(_ s: String) -> String { s.prefix(1).uppercased() + s.dropFirst() }
    private func defaultBudget(_ t: Tier, _ days: Int, _ travellers: Int) -> Int {
        Int((Double((lex.dailyPerPerson[t.rawValue] ?? 3000) * days * travellers) / 500).rounded()) * 500
    }
    private func first(_ pattern: String, _ s: String) -> [String]? {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return nil }
        let ns = s as NSString
        guard let m = re.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return (0..<m.numberOfRanges).map { i in m.range(at: i).location == NSNotFound ? "" : ns.substring(with: m.range(at: i)) }
    }

    // MARK: entity extraction
    func extractBudget(_ norm: String) -> Int? {
        if let m = first(" (\\d+(?:\\.\\d+)?) ?(k|thousand|lakh|lac) ", norm), let v = Double(m[1]) {
            return clamp(v * ((m[2] == "k" || m[2] == "thousand") ? 1000 : 100000), 1000, 10_000_000)
        }
        if let m = first(" bdt (\\d{4,7}) ", norm) ?? first(" (\\d{4,7}) bdt ", norm), let v = Double(m[1]) { return clamp(v, 1000, 10_000_000) }
        if let m = first(" (?:under|below|within|budget of|budget|max|upto|up to|around|about) (\\d{4,7}) ", norm), let v = Double(m[1]) { return clamp(v, 1000, 10_000_000) }
        return nil
    }

    func extractDays(_ norm: String) -> Int? {
        if let m = first(" (\\d+) (?:day|days) ", norm), let v = Double(m[1]) { return clamp(v, 1, 14) }
        if let m = first(" (\\d+) (?:night|nights) ", norm), let v = Double(m[1]) { return clamp(v + 1, 1, 14) }
        if let m = first(" (\\d+) (?:week|weeks) ", norm), let v = Double(m[1]) { return clamp(v * 7, 1, 14) }
        if has(norm, "long weekend") { return 3 }
        if has(norm, "weekend") { return 2 }
        if has(norm, "day trip") || has(norm, "a day") || has(norm, "one day") { return 1 }
        if has(norm, "a week") || has(norm, "week long") || has(norm, "weeklong") { return 7 }
        return nil
    }

    func extractTravellers(_ norm: String) -> Int? {
        if let m = first(" family of (\\d+) ", norm) ?? first(" (\\d+) (?:people|persons|person|travellers|travelers|adults|pax) ", norm) ?? first(" (\\d+) of us ", norm), let v = Double(m[1]) { return clamp(v, 1, 30) }
        if let m = first(" for (\\d{1,2}) (?!(?:day|days|night|nights|week|weeks|k|thousand|lakh|lac|bdt)\\b)", norm), let v = Double(m[1]) { return clamp(v, 1, 30) }
        if let m = first(" with (\\d+) (?:friends|friend|colleagues|cousins) ", norm), let v = Double(m[1]) { return clamp(v + 1, 1, 30) }
        if hasAny(norm, ["solo", "alone", "myself", "by myself", "just me"]) { return 1 }
        if hasAny(norm, ["couple", "honeymoon", "wife", "husband", "partner", "girlfriend", "boyfriend", "two of us"]) { return 2 }
        if has(norm, "family") { return 4 }
        if hasAny(norm, ["friends", "group"]) { return 4 }
        return nil
    }

    func extractMonth(_ norm: String, _ current: Int) -> Int? {
        if has(norm, "this month") { return current }
        if has(norm, "next month") { return (current % 12) + 1 }
        for (name, n) in lex.monthNames where has(norm, name) { return n }
        if norm.contains(" in may ") || norm.contains(" this may ") || norm.contains(" next may ") { return 5 }
        if has(norm, "march") { return 3 }
        for (name, n) in lex.seasons where has(norm, name) { return n }
        return nil
    }

    func extractInterests(_ norm: String) -> [String] { interestOrder.filter { hasAny(norm, lex.interests[$0] ?? []) } }

    func extractTier(_ norm: String) -> Tier? {
        let cheap = hasAny(norm, lex.cheapWords), quality = hasAny(norm, lex.qualityWords), fancy = hasAny(norm, lex.fancyWords)
        if fancy { return .fancy }
        if cheap && quality { return .value }
        if quality { return .value }
        if cheap { return .budget }
        return nil
    }

    private func wordsOf(_ s: String) -> String { normalize(s).trimmingCharacters(in: .whitespaces) }
    private func baseName(_ d: Destination) -> String { d.name.components(separatedBy: CharacterSet(charactersIn: "(,")).first ?? d.name }

    private func destinationKeys(_ d: Destination) -> [String] {
        let base = wordsOf(baseName(d))
        var keys: [String] = []
        func add(_ k: String) { if !keys.contains(k) { keys.append(k) } }
        add(wordsOf(d.name)); add(base); add(wordsOf(d.district))
        let parts = base.split(separator: " ").map(String.init)
        if parts.count >= 3 {
            let two = parts.prefix(2).joined(separator: " ")
            add(two)
            if two.hasSuffix("s") { add(String(two.dropLast())) }
        }
        if parts.count >= 2, parts[0].count >= 5, !lex.firstWordStoplist.contains(parts[0]) { add(parts[0]) }
        return keys.filter { $0.count >= 4 }
    }

    private func findDestinations(_ norm: String, _ destinations: [Destination]) -> [Match] {
        let cleaned = norm.replacingOccurrences(of: " from [a-z0-9]+ ", with: " ", options: .regularExpression)
        var found: [Match] = []
        for d in destinations {
            var best: String?
            for k in destinationKeys(d) where has(cleaned, k) && (best == nil || k.count > best!.count) { best = k }
            if let b = best {
                let pos = (cleaned as NSString).range(of: " \(b) ").location
                found.append(Match(dest: d, key: b, pos: pos, byDistrict: b == wordsOf(d.district) && b != wordsOf(baseName(d))))
            }
        }
        return found.sorted { $0.key.count != $1.key.count ? $0.key.count > $1.key.count : $0.dest.id < $1.dest.id }
    }

    // MARK: memory and defaults
    private func readPreferences(_ memory: [String]) -> Prefs {
        var likes: [String] = [], dislikes: [String] = []
        var tier: Tier?
        for note in memory {
            let n = normalize(note)
            let negative = hasAny(n, ["hate", "dislike", "avoid", "not into", "no"])
            for t in extractInterests(n) {
                if negative { if !dislikes.contains(t) { dislikes.append(t) } } else if !likes.contains(t) { likes.append(t) }
            }
            if !negative { tier = extractTier(n) ?? tier }
        }
        return Prefs(likes: likes, dislikes: dislikes, tier: tier)
    }

    private func historyInterests(_ visited: [Int], _ destinations: [Destination]) -> [String] {
        var counts: [String: Int] = [:]
        let byId = Dictionary(uniqueKeysWithValues: destinations.map { ($0.id, $0) })
        for id in visited { for t in byId[id]?.tagSet ?? [] { counts[t, default: 0] += 1 } }
        return counts.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }.prefix(2).map(\.key)
    }

    private func resolveQuery(_ ent: Entities, _ ctx: BrainContext, _ last: BrainQuery?, _ prefs: Prefs) -> (BrainQuery, [String]) {
        var assumed: [String] = []
        let tier = ent.tier ?? last?.tier ?? prefs.tier ?? .value
        if ent.tier == nil && last?.tier == nil && prefs.tier == nil { assumed.append("\(tier.label) tour") }
        let travellers = ent.travellers ?? last?.travellers ?? 2
        if ent.travellers == nil && last == nil { assumed.append("\(travellers) travellers") }
        let days = ent.days ?? last?.days ?? 3
        if ent.days == nil && last == nil { assumed.append("\(days) days") }
        let month = ent.month ?? last?.month ?? ctx.month
        if ent.month == nil && last == nil { assumed.append(monthsLong[month - 1]) }
        var interests = !ent.interests.isEmpty ? ent.interests : (last?.interests ?? [])
        if ent.interests.isEmpty && last == nil {
            let learned = !prefs.likes.isEmpty ? prefs.likes : historyInterests(ctx.visitedIds, ctx.destinations)
            interests = learned
            if !learned.isEmpty { assumed.append("interests: \(learned.joined(separator: ", "))") }
        }
        interests = interests.filter { !prefs.dislikes.contains($0) }
        var budget: Int? = ent.budget ?? ((last != nil && ent.tier == nil && ent.days == nil && ent.travellers == nil) ? last?.budget : nil)
        if budget == nil {
            budget = defaultBudget(tier, days, travellers)
            assumed.append("budget about \(bdt(budget!))")
        }
        return (BrainQuery(budget: budget!, days: days, travellers: travellers, month: month, interests: interests, tier: tier), assumed)
    }

    // MARK: planner glue
    private func planQuery(_ q: BrainQuery) -> PlanQuery { PlanQuery(budget: q.budget, travellers: q.travellers, days: q.days, month: q.month, interests: Set(q.interests), tier: q.tier) }
    private func rank(_ pool: [Destination], _ q: BrainQuery) -> [RankedDestination] { ScoringEngine.rank(pool, planQuery(q)) }
    private func estimate(_ d: Destination, _ q: BrainQuery) -> Int { Int((Double(d.costPerDayBdt) * q.tier.costFactor * Double(q.days * q.travellers)).rounded()) }

    private func planFor(_ d: Destination, _ q: BrainQuery, _ ctx: BrainContext) -> BrainPlan {
        let acts = ctx.activities.filter { $0.destinationId == d.id }
        let items = ItineraryGenerator.generate(tripId: UUID(), days: q.days, activities: acts, interests: Set(q.interests), tier: q.tier)
            .map { PlanItem(day: $0.dayNo, hour: $0.startHour, activityId: $0.activityId) }
        return BrainPlan(destinationId: d.id, destinationName: d.name, district: d.district, tier: q.tier, days: q.days, items: items, totalBdt: estimate(d, q))
    }

    private func itineraryText(_ plan: BrainPlan, _ ctx: BrainContext) -> String {
        let byId = Dictionary(uniqueKeysWithValues: ctx.activities.map { ($0.id, $0) })
        return (1...max(plan.days, 1)).compactMap { day -> String? in
            let items = plan.items.filter { $0.day == day }
            if items.isEmpty { return nil }
            return "Day \(day): " + items.map { String(format: "%02d:00 %@", $0.hour, byId[$0.activityId]?.name ?? "") }.joined(separator: "; ")
        }.joined(separator: "\n")
    }

    private func pickFor(_ m: Match, _ q: BrainQuery, _ ctx: BrainContext) -> Destination {
        m.byDistrict ? rank(ctx.destinations.filter { $0.district == m.dest.district }, q)[0].destination : m.dest
    }

    private func safetyNote(_ d: Destination) -> String {
        d.tagSet.contains { lex.safetyTags.contains($0) } ? " Check weather and local advisories before you go." : ""
    }

    /// The most severe active advisory that applies to this spot (national, its division or its district).
    private func advisoryNote(_ d: Destination, _ ctx: BrainContext) -> String {
        let rank = ["danger": 3, "warning": 2, "info": 1]
        let list = ctx.advisories.filter { $0.scope == "national" || ($0.scope == "division" && $0.target == d.division) || ($0.scope == "district" && $0.target == d.district) }
        guard let a = list.enumerated().max(by: { (rank[$0.element.severity] ?? 0, -$0.offset) < (rank[$1.element.severity] ?? 0, -$1.offset) })?.element else { return "" }
        return " Advisory (\(a.severity)): \(a.title).\(a.text.isEmpty ? "" : " " + a.text)"
    }

    private func whyLine(_ row: RankedDestination, _ q: BrainQuery) -> String {
        let f = row.score
        var bits: [String] = []
        bits.append(f.weather == 1.0 ? "in season in \(monthsLong[q.month - 1])" : "off-season in \(monthsLong[q.month - 1]), so expect rain")
        if !q.interests.isEmpty && f.interest >= 0.99 { bits.append("matches \(q.interests.joined(separator: " and "))") }
        else if !q.interests.isEmpty && f.interest > 0 { bits.append("partly matches \(q.interests.joined(separator: ", "))") }
        bits.append(f.affordable ? "within your budget" : "about \(bdt(f.estimatedCost - q.budget)) over budget")
        return bits.joined(separator: ", ")
    }

    // MARK: the agent
    func respond(_ utterance: String, _ ctx: BrainContext, _ state: BrainState = BrainState()) -> BrainReply {
        let norm = normalize(utterance)
        let month = ctx.month
        let prefs = readPreferences(ctx.memory)
        var ent = Entities(budget: extractBudget(norm), days: extractDays(norm), travellers: extractTravellers(norm), month: extractMonth(norm, month), interests: extractInterests(norm), tier: extractTier(norm))
        let dests = findDestinations(norm, ctx.destinations)
        let visitedIds = Set(ctx.visitedIds)
        let anyEntity = ent.budget != nil || ent.days != nil || ent.travellers != nil || ent.month != nil || !ent.interests.isEmpty || ent.tier != nil
        let shortText = norm.split(separator: " ").count <= 4
        func out(_ reply: String, _ st: BrainState? = nil, plan: BrainPlan? = nil, actions: [BrainAction] = []) -> BrainReply { BrainReply(reply: reply, state: st ?? state, plan: plan, actions: actions) }

        // housekeeping intents
        if hasAny(norm, trig("forget")) {
            var st = state; st.lastQuery = nil; st.lastPlaceId = nil; st.offset = 0
            return out("Done. I cleared your saved preferences.", st, actions: [.forgetAll])
        }
        if hasAny(norm, trig("remember")) {
            var note = utterance
            if let re = try? NSRegularExpression(pattern: "(?:remember(?: that)?|note that|keep in mind(?: that)?)\\s+(.+)", options: .caseInsensitive),
               let m = re.firstMatch(in: utterance, range: NSRange(location: 0, length: (utterance as NSString).length)), m.numberOfRanges > 1 {
                note = (utterance as NSString).substring(with: m.range(at: 1))
            }
            note = String(note.unicodeScalars.map { $0.value < 32 ? " " : Character($0) }.map(String.init).joined().trimmingCharacters(in: .whitespaces).prefix(200))
            return out("Saved: \"\(note)\". I will use it in future plans.", actions: [.remember(note)])
        }
        if hasAny(norm, trig("save")), let plan = state.lastPlan { return out("Saved the \(plan.destinationName) plan as your trip.", actions: [.savePlan]) }
        if shortText && hasAny(norm, trig("thanks")) { return out("You are welcome. Tell me when you want the next plan.") }
        if shortText && hasAny(norm, trig("greeting")) && !anyEntity { return out("Hello. Tell me what kind of trip you want, or say \"something I have not visited\". I will fill in the details.") }
        if hasAny(norm, trig("help")) && !hasAny(norm, trig("plan")) { return out("I can plan trips by budget, days, interests and season, pick Budget, Value or Fancy tours, build itineraries, estimate costs, compare places, suggest places you have not visited, and remember your preferences. \(suggestions)") }

        // progress
        if hasAny(norm, trig("progress")) {
            let districts = Set(ctx.destinations.filter { visitedIds.contains($0.id) }.map(\.district))
            let total = Set(ctx.destinations.map(\.district)).count
            if visitedIds.isEmpty { return out("You have not marked any spots yet. Open the Map tab and tap a district to mark places you have visited.") }
            let recent = ctx.visitedIds.suffix(3).compactMap { id in ctx.destinations.first { $0.id == id }?.name }
            return out("You have visited \(visitedIds.count) of \(ctx.destinations.count) spots across \(districts.count) of \(total) districts. Latest: \(recent.joined(separator: ", ")).")
        }

        // navigation with live location
        if hasAny(norm, trig("nav")) {
            let loc = ctx.location
            let target = dests.first?.dest
                ?? loc?.targetId.flatMap { id in ctx.destinations.first { $0.id == id } }
                ?? state.lastPlaceId.flatMap { id in ctx.destinations.first { $0.id == id } }
            guard let loc else { return out("Open the Live map and start tracking, then ask again. I need your location to measure the distance.") }
            guard let target else { return out("Plan a trip first, or name the place, and I will measure the distance from where you are.") }
            let km = BangladeshMap.distanceKm(loc.lat, loc.lon, target.latitude, target.longitude)
            let roadKm = km * 1.3, hours = roadKm / 40
            let time = hours < 1 ? "\(max(5, Int((hours * 60).rounded()))) minutes" : String(format: "%.1f hours", hours)
            let near = km <= 10 ? " You are inside \(target.district). You can mark \(target.name) as visited." : ""
            var st = state; st.lastPlaceId = target.id
            return out(String(format: "%@: about %.0f km straight-line, roughly %.0f km by road, about %@ at 40 km/h. Spot positions are district-level, so this is rough.%@", target.name, km, roadKm, time, near), st)
        }

        // compare: one mention per named place or district, in the order written
        var seen = Set<String>()
        let mentions = dests.filter { seen.insert($0.byDistrict ? "d:" + $0.dest.district : "n:\($0.dest.id)").inserted }
            .sorted { $0.pos != $1.pos ? $0.pos < $1.pos : $0.dest.id < $1.dest.id }
        if mentions.count >= 2 && (hasAny(norm, trig("compare")) || has(norm, "and")) {
            let (query, _) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            let a = pickFor(mentions[0], query, ctx), b = pickFor(mentions[1], query, ctx)
            let ra = RankedDestination(destination: a, score: ScoringEngine.score(a, planQuery(query)))
            let rb = RankedDestination(destination: b, score: ScoringEngine.score(b, planQuery(query)))
            let winner = ra.score.total >= rb.score.total ? ra : rb
            let other = winner.destination.id == ra.destination.id ? rb : ra
            func line(_ r: RankedDestination) -> String {
                "\(r.destination.name): \(bdt(r.score.estimatedCost)), \(r.destination.monthSet.contains(query.month) ? "in season" : "off-season") in \(monthsLong[query.month - 1]), \(r.destination.tagSet.sorted().joined(separator: "/"))"
            }
            var st = state; st.lastQuery = query; st.lastPlaceId = winner.destination.id
            return out("\(winner.destination.name) fits better for \(query.days) days, \(query.travellers) travellers, \(query.tier.label) tour.\n\(line(winner))\n\(line(other))\nCosts are estimates.", st)
        }

        // when to go
        if hasAny(norm, trig("when")) && (!dests.isEmpty || state.lastPlaceId != nil) {
            let d = !dests.isEmpty ? pickFor(dests[0], resolveQuery(ent, ctx, state.lastQuery, prefs).0, ctx) : (ctx.destinations.first { $0.id == state.lastPlaceId } ?? ctx.destinations[0])
            let ms = d.monthSet.sorted { ($0 + 8) % 12 < ($1 + 8) % 12 }.map { monthsLong[$0 - 1] }
            let now = d.monthSet.contains(month) ? "\(monthsLong[month - 1]) is in season." : "\(monthsLong[month - 1]) is off-season, so expect rain."
            var st = state; st.lastPlaceId = d.id
            return out("Best months for \(d.name): \(ms.joined(separator: ", ")). \(now)\(safetyNote(d))\(advisoryNote(d, ctx))", st)
        }

        // cost
        if hasAny(norm, trig("cost")) && (!dests.isEmpty || state.lastPlaceId != nil) {
            let (query, _) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            let d = !dests.isEmpty ? pickFor(dests[0], query, ctx) : (ctx.destinations.first { $0.id == state.lastPlaceId } ?? ctx.destinations[0])
            let total = estimate(d, query)
            func share(_ f: Double) -> Int { Int((Double(total) * f).rounded()) }
            let fits = total <= query.budget ? "within" : "over"
            var st = state; st.lastQuery = query; st.lastPlaceId = d.id
            return out("\(d.name), \(query.days) days, \(query.travellers) travellers, \(query.tier.label) tour: about \(bdt(total)). Stay \(bdt(share(0.4))), food \(bdt(share(0.25))), transport \(bdt(share(0.2))), activities \(bdt(share(0.15))). That is \(fits) a \(bdt(query.budget)) budget. Estimates only.", st)
        }

        // itinerary for a named place (or the last one)
        if hasAny(norm, trig("itinerary")) && (!dests.isEmpty || state.lastPlaceId != nil) {
            let (query, assumed) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            let d = !dests.isEmpty ? pickFor(dests[0], query, ctx) : (ctx.destinations.first { $0.id == state.lastPlaceId } ?? ctx.destinations[0])
            let plan = planFor(d, query, ctx)
            let text = itineraryText(plan, ctx)
            let thin = plan.items.count < query.days * 3 ? "\nOnly \(plan.items.count) activities are listed for this spot so far." : ""
            let assumedLine = assumed.isEmpty ? "" : "\nAssumed: \(assumed.joined(separator: ", "))."
            return out("\(d.name), \(query.days) days, \(query.tier.label) tour, about \(bdt(plan.totalBdt)) for \(query.travellers).\n\(text.isEmpty ? "No suitable activities found." : text)\(thin)\(assumedLine)\(safetyNote(d))\(advisoryNote(d, ctx))",
                       BrainState(lastQuery: query, lastPlaceId: d.id, offset: 0, lastPlan: plan), plan: plan)
        }

        // info about a place
        if !dests.isEmpty && (hasAny(norm, trig("info")) || (!anyEntity && !hasAny(norm, trig("plan")))) {
            let (query, _) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            let d = pickFor(dests[0], query, ctx)
            let months = d.monthSet.sorted().map { String(monthsLong[$0 - 1].prefix(3)) }.joined(separator: ", ")
            var st = state; st.lastPlaceId = d.id
            return out("\(d.name) (\(d.district)): \(d.blurb) Tags: \(d.tagSet.sorted().joined(separator: ", ")). Best months: \(months). About \(bdt(estimate(d, query))) for \(query.days) days, \(query.travellers) travellers, \(query.tier.label) tour (estimate).\(safetyNote(d))\(advisoryNote(d, ctx))", st)
        }

        // follow-ups: "another one", "cheaper", "what about fancy", "make it 5 days"
        let tokens = norm.split(separator: " ").count
        let modifier = dests.isEmpty && anyEntity && !hasAny(norm, trig("plan")) && tokens <= 8
        let followUp = state.lastQuery != nil && (hasAny(norm, trig("alt")) || hasAny(norm, trig("cheaper")) || hasAny(norm, trig("fancier")) || modifier)
        if followUp {
            if hasAny(norm, trig("cheaper")) { ent.tier = .budget }
            if hasAny(norm, trig("fancier")) { ent.tier = .fancy }
        }

        // planning: the default for anything travel-shaped
        let wantsNew = hasAny(norm, trig("nextNew"))
        if hasAny(norm, trig("plan")) || anyEntity || wantsNew || followUp || !dests.isEmpty {
            var q: BrainQuery
            var assumed: [String] = []
            if followUp, let last = state.lastQuery {
                q = last
                if let t = ent.tier { q.tier = t }
                if let b = ent.budget { q.budget = b }
                if let d = ent.days { q.days = d }
                if let t = ent.travellers { q.travellers = t }
                if let m = ent.month { q.month = m }
                if !ent.interests.isEmpty { q.interests = ent.interests }
                if ent.budget == nil && (ent.tier != nil || ent.days != nil || ent.travellers != nil) { q.budget = defaultBudget(q.tier, q.days, q.travellers) }
            } else {
                let r = resolveQuery(ent, ctx, nil, prefs); q = r.0; assumed = r.1
            }
            var pool = ctx.destinations
            if !dests.isEmpty {
                // Naming a specific spot plans that spot. Naming only a district considers every spot in it.
                let specific = Set(dests.filter { !$0.byDistrict }.map(\.dest.id))
                if !specific.isEmpty { pool = pool.filter { specific.contains($0.id) } }
                else { let ds = Set(dests.map(\.dest.district)); pool = pool.filter { ds.contains($0.district) } }
            }
            if wantsNew { pool = pool.filter { !visitedIds.contains($0.id) } }
            let rows = rank(pool, q)
            if rows.isEmpty { return out(wantsNew ? "You have visited every spot in the list. Add new spots from More, Add a spot." : "I found nothing for that. \(suggestions)") }
            let offset = hasAny(norm, trig("alt")) ? ((state.offset + 1) % min(rows.count, 5)) : 0
            let top = rows[offset]
            let others = rows.enumerated().filter { $0.offset != offset }.prefix(2).map(\.element)
            let plan = planFor(top.destination, q, ctx)
            let seenNote = visitedIds.contains(top.destination.id) ? " You have visited it before." : ""
            let also = others.isEmpty ? "" : "\nAlso good: " + others.map { "\($0.destination.name) (\(bdt($0.score.estimatedCost)))" }.joined(separator: ", ") + "."
            let head = "\(q.tier.label) tour pick: \(top.destination.name) (\(top.destination.district)), about \(bdt(top.score.estimatedCost)) for \(q.travellers) \(q.travellers == 1 ? "person" : "people"), \(q.days) \(q.days == 1 ? "day" : "days"). \(cap(whyLine(top, q))).\(seenNote)"
            let assumedLine = assumed.isEmpty ? "" : "\nAssumed: \(assumed.joined(separator: ", "))."
            return out("\(head)\(also)\(assumedLine)\nCosts are estimates.\(safetyNote(top.destination))\(advisoryNote(top.destination, ctx))",
                       BrainState(lastQuery: q, lastPlaceId: top.destination.id, offset: offset, lastPlan: plan), plan: plan)
        }

        // nothing matched: stay calm, give examples, do not interrogate
        var fallback = out("I did not catch a trip request in that. \(suggestions)")
        fallback.understood = false
        return fallback
    }
}
