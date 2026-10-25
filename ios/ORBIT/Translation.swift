import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

/// Optional language layer. An on-device language model only TRANSLATES a message ORBIT Brain did not understand
/// (Bangla, Banglish, loose wording) into one short English request. ORBIT Brain then does all the planning, so prices,
/// places and numbers always come from the app's own data, never from the model. The model's own words are never shown.
/// Mirrors backend/src/translation.js.
enum Translation {
    static let notice = "Read with the on-device language model."
    private static let maxWords = 25

    static let instructions = """
    You convert a traveller's message into ONE short English request for a Bangladesh trip planner.
    The message may be in English, Bangla or Banglish (Bangla written in English letters).
    Output exactly one line and nothing else. Keep every number, amount, place name and month. Write digits as 0-9.
    Use optional prior trip context only to resolve short follow-ups such as “make it cheaper”, “same place but longer”, or “for two people”. Preserve explicit changes in the latest message. Never invent a destination, amount, date, or preference; if the request cannot be safely resolved from the message and context, output NONE.
    Use these forms:
    - plan: "<N> days <interest> trip under <amount> for <N> people in <month>"; add cheap, affordable but good quality, or luxury when the traveller asks for a style
    - "itinerary for <place>"
    - "best time to visit <place>"
    - "how much for <N> people <N> days in <place>"
    - "compare <place> and <place>"
    - "something I have not visited"
    - "what have i visited"
    - "how far is <place>"
    - "remember that <short note>"
    - "forget everything"
    Interests are: beach, hills, nature, food, culture, adventure.
    If the message is not about travel, output NONE.
    Examples:
    Message: আমি ৩ দিনের জন্য সমুদ্র সৈকতে যেতে চাই, বাজেট ২০ হাজার
    Output: 3 days beach trip under 20k
    Message: cox bazar jete koto khoroch hobe 4 jon
    Output: how much for 4 people in Cox's Bazar
    Message: শীতে পাহাড়ে ঘুরতে চাই, সস্তায়
    Output: cheap hills trip in winter
    Message: what is 2+2
    Output: NONE
    """

    /// Cleans raw model output into a single safe command, or returns nil if it is unusable.
    static func sanitize(_ raw: String?) -> String? {
        guard let raw else { return nil }
        guard let line = raw.components(separatedBy: .newlines).map({ $0.trimmingCharacters(in: .whitespaces) }).first(where: { !$0.isEmpty }) else { return nil }
        var s = line
            .replacingOccurrences(of: "^(?:output|command|answer)\\s*:\\s*", with: "", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: "^[\\s\"'`*_>\\-]+|[\\s\"'`*_]+$", with: "", options: .regularExpression)
            .replacingOccurrences(of: "[\\u0000-\\u001f\\u007f]", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "?", with: "")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        s = s.trimmingCharacters(in: .whitespaces)
        if s.isEmpty || s.range(of: "^none\\.?$", options: [.regularExpression, .caseInsensitive]) != nil { return nil }
        if s.range(of: "[a-z]", options: [.regularExpression, .caseInsensitive]) == nil { return nil }
        if s.split(separator: " ").count > maxWords || s.count > 160 { return nil }
        return s
    }
}

/// Apple's on-device language model (Foundation Models, iOS 26 and Apple Intelligence devices). Nothing to download.
enum LanguageAssist {
    static var enabled: Bool { UserDefaults.standard.bool(forKey: "orbit.llm.enabled") }

    static var available: Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            if case .available = SystemLanguageModel.default.availability { return true }
        }
        #endif
        return false
    }

    static func translate(_ text: String, context: String? = nil) async -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *), available {
            do {
                let session = LanguageModelSession(instructions: Translation.instructions)
                let prior = context.map { "Trusted prior trip context (use only to resolve this follow-up): \(String($0.prefix(500)))\n" } ?? ""
                let response = try await session.respond(to: "\(prior)Traveller message: \(String(text.prefix(1000)))\nOutput:")
                return response.content
            } catch {
                return nil
            }
        }
        #endif
        return nil
    }
}
