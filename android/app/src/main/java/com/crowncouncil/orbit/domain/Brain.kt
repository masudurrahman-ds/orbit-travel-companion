package com.crowncouncil.orbit.domain

import com.crowncouncil.orbit.data.ActivityOption
import com.crowncouncil.orbit.data.Destination
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ORBIT Brain: the app's own travel agent. No external AI service and no network.
 * Pipeline: normalise -> extract entities -> detect intent -> apply defaults and memory -> call the planner -> write a short reply.
 * This is a line-by-line port of backend/src/brain.js (which has the tests) and shares data/brain-data.json.
 */
class Lexicon(json: String) {
    private val root = JSONObject(json)
    private fun list(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }
    private fun intMap(o: JSONObject): Map<String, Int> = o.keys().asSequence().associateWith { o.getInt(it) }

    val interests: Map<String, List<String>> = root.getJSONObject("interests").let { o -> o.keys().asSequence().associateWith { list(o.getJSONArray(it)) } }
    val cheapWords = list(root.getJSONArray("cheapWords"))
    val qualityWords = list(root.getJSONArray("qualityWords"))
    val fancyWords = list(root.getJSONArray("fancyWords"))
    val numberWords = intMap(root.getJSONObject("numberWords"))
    val seasons = intMap(root.getJSONObject("seasons"))
    val monthNames = intMap(root.getJSONObject("monthNames"))
    val triggers: Map<String, List<String>> = root.getJSONObject("triggers").let { o -> o.keys().asSequence().associateWith { list(o.getJSONArray(it)) } }
    val safetyTags = list(root.getJSONArray("safetyTags"))
    val dailyPerPerson = intMap(root.getJSONObject("dailyPerPerson"))
    val firstWordStoplist = list(root.getJSONArray("firstWordStoplist"))
    val bangla: List<Pair<String, String>> = root.getJSONObject("bangla").let { o -> o.keys().asSequence().map { it to o.getString(it) }.sortedByDescending { it.first.length }.toList() }
    val banglish: Map<String, String> = root.getJSONObject("banglish").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
    val placeAliases: List<Pair<String, String>> = root.getJSONObject("placeAliases").let { o -> o.keys().asSequence().map { it to o.getString(it) }.toList() }
}

data class BrainQuery(val budget: Int, val days: Int, val travellers: Int, val month: Int, val interests: List<String>, val tier: Tier)
data class PlanItem(val day: Int, val hour: Int, val activityId: Long)
data class BrainPlan(val destinationId: Long, val destinationName: String, val district: String, val tier: Tier, val days: Int, val items: List<PlanItem>, val totalBdt: Int)
data class BrainState(val lastQuery: BrainQuery? = null, val lastPlaceId: Long? = null, val offset: Int = 0, val lastPlan: BrainPlan? = null)
data class BrainLocation(val lat: Double, val lon: Double, val targetId: Long? = null)
data class BrainVisit(val destinationId: Long, val recent: Long = 0)

sealed interface BrainAction {
    data class Remember(val text: String) : BrainAction
    data object ForgetAll : BrainAction
    data object SavePlan : BrainAction
}

class BrainAdvisory(val scope: String, val target: String?, val severity: String, val title: String, val text: String)

class BrainContext(
    val destinations: List<Destination>,
    val activities: List<ActivityOption>,
    val visits: List<BrainVisit>,        // oldest first
    val memory: List<String>,
    val month: Int,
    val location: BrainLocation? = null,
    val advisories: List<BrainAdvisory> = emptyList(),
    val transport: TransportData? = null
)

data class BrainReply(val reply: String, val state: BrainState, val plan: BrainPlan?, val actions: List<BrainAction>, val understood: Boolean = true)

private class Entities(
    val budget: Int?, val days: Int?, val travellers: Int?, val month: Int?, val interests: List<String>, var tier: Tier?
)

private class Match(val dest: Destination, val key: String, val pos: Int, val byDistrict: Boolean)
private class Prefs(val likes: List<String>, val dislikes: List<String>, val tier: Tier?)

class Brain(private val lex: Lexicon) {
    private val monthsLong = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val interestOrder = listOf("beach", "hills", "nature", "food", "culture", "adventure")
    private val suggestions = "Try: \"3 days beach trip under 20k\", \"cheap hills trip for 4 people\", \"affordable but good quality trip in December\", \"fancy weekend in Sylhet\", \"itinerary for Jaflong\", \"best time to visit Sajek\", \"compare Sylhet and Bandarban\", \"something I have not visited\"."

    // ------------------------------------------------------------ text helpers
    private val bnDigits = "০১২৩৪৫৬৭৮৯"

    /** Bangla script and Banglish are understood offline with the word lists in brain-data.json: no model needed. */
    private fun prepareBangla(text: String): String {
        var s = buildString { for (ch in text) { val i = bnDigits.indexOf(ch); append(if (i >= 0) ('0' + i) else ch) } }
        if (!Regex("[\\u0980-\\u09FF]").containsMatchIn(s)) return s
        s = Regex("(\\d+(?:\\.\\d+)?)\\s*হাজার").replace(s, "\$1k ")
        s = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:লাখ|লক্ষ)").replace(s, "\$1 lakh ")
        s = Regex("(\\d+)\\s*জন").replace(s, "\$1 people ")
        s = Regex("(\\d+)\\s*দিন").replace(s, "\$1 days ")
        s = Regex("(\\d+)\\s*রাত").replace(s, "\$1 nights ")
        s = Regex("(\\d+)\\s*সপ্তাহ").replace(s, "\$1 weeks ")
        for ((bn, en) in lex.bangla) s = s.replace(bn, " $en ")
        return s
    }

    fun normalize(text: String): String {
        var s = prepareBangla(text).lowercase(Locale.ROOT).replace("\u2019", "").replace("'", "")
        s = s.replace("৳", " bdt ")
        s = Regex("\\b(taka|tk)\\b").replace(s, " bdt ")
        s = Regex("(\\d),(\\d{3})").replace(s, "\$1\$2")
        s = Regex("[^a-z0-9. ]+").replace(s, " ")
        s = Regex("(?<!\\d)\\.|\\.(?!\\d)").replace(s, " ")
        s = s.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { lex.numberWords[it]?.toString() ?: lex.banglish[it] ?: it }
        s = " " + s.replace(Regex("\\s+"), " ").trim() + " "
        for ((from, to) in lex.placeAliases) s = s.replace(" $from ", " $to ")
        return s
    }

    private fun has(norm: String, phrase: String) = norm.contains(" $phrase ")
    private fun hasAny(norm: String, list: List<String>) = list.any { has(norm, it) }
    private fun trig(name: String) = lex.triggers[name].orEmpty()
    private fun bdt(n: Int): String = "BDT " + String.format(Locale.US, "%,d", n)
    private fun clamp(v: Double, lo: Int, hi: Int) = v.roundToInt().coerceIn(lo, hi)
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private fun tierLabel(t: Tier) = t.label
    private fun tierKey(t: Tier) = t.name.lowercase(Locale.ROOT)
    private fun defaultBudget(t: Tier, days: Int, travellers: Int) = (((lex.dailyPerPerson[tierKey(t)] ?: 3000) * days * travellers) / 500.0).roundToInt() * 500

    // ------------------------------------------------------------ entity extraction
    private fun first(pattern: String, s: String) = Regex(pattern).find(s)?.groupValues

    fun extractBudget(norm: String): Int? {
        first(" (\\d+(?:\\.\\d+)?) ?(k|thousand|lakh|lac) ", norm)?.let { m ->
            val mult = if (m[2] == "k" || m[2] == "thousand") 1000 else 100000
            return clamp(m[1].toDouble() * mult, 1000, 10_000_000)
        }
        (first(" bdt (\\d{4,7}) ", norm) ?: first(" (\\d{4,7}) bdt ", norm))?.let { return clamp(it[1].toDouble(), 1000, 10_000_000) }
        first(" (?:under|below|within|budget of|budget|max|upto|up to|around|about) (\\d{4,7}) ", norm)?.let { return clamp(it[1].toDouble(), 1000, 10_000_000) }
        return null
    }

    fun extractDays(norm: String): Int? {
        first(" (\\d+) (?:day|days) ", norm)?.let { return clamp(it[1].toDouble(), 1, 14) }
        first(" (\\d+) (?:night|nights) ", norm)?.let { return clamp(it[1].toDouble() + 1, 1, 14) }
        first(" (\\d+) (?:week|weeks) ", norm)?.let { return clamp(it[1].toDouble() * 7, 1, 14) }
        if (has(norm, "long weekend")) return 3
        if (has(norm, "weekend")) return 2
        if (has(norm, "day trip") || has(norm, "a day") || has(norm, "one day")) return 1
        if (has(norm, "a week") || has(norm, "week long") || has(norm, "weeklong")) return 7
        return null
    }

    fun extractTravellers(norm: String): Int? {
        (first(" family of (\\d+) ", norm) ?: first(" (\\d+) (?:people|persons|person|travellers|travelers|adults|pax) ", norm) ?: first(" (\\d+) of us ", norm))
            ?.let { return clamp(it[1].toDouble(), 1, 30) }
        first(" for (\\d{1,2}) (?!(?:day|days|night|nights|week|weeks|k|thousand|lakh|lac|bdt)\\b)", norm)?.let { return clamp(it[1].toDouble(), 1, 30) }
        first(" with (\\d+) (?:friends|friend|colleagues|cousins) ", norm)?.let { return clamp(it[1].toDouble() + 1, 1, 30) }
        if (hasAny(norm, listOf("solo", "alone", "myself", "by myself", "just me"))) return 1
        if (hasAny(norm, listOf("couple", "honeymoon", "wife", "husband", "partner", "girlfriend", "boyfriend", "two of us"))) return 2
        if (has(norm, "family")) return 4
        if (hasAny(norm, listOf("friends", "group"))) return 4
        return null
    }

    fun extractMonth(norm: String, current: Int): Int? {
        if (has(norm, "this month")) return current
        if (has(norm, "next month")) return (current % 12) + 1
        for ((name, n) in lex.monthNames) if (has(norm, name)) return n
        if (norm.contains(" in may ") || norm.contains(" this may ") || norm.contains(" next may ")) return 5
        if (has(norm, "march")) return 3
        for ((name, n) in lex.seasons) if (has(norm, name)) return n
        return null
    }

    fun extractInterests(norm: String): List<String> =
        interestOrder.filter { tag -> hasAny(norm, lex.interests[tag].orEmpty()) }

    fun extractTier(norm: String): Tier? {
        val cheap = hasAny(norm, lex.cheapWords)
        val quality = hasAny(norm, lex.qualityWords)
        val fancy = hasAny(norm, lex.fancyWords)
        return when {
            fancy -> Tier.FANCY
            cheap && quality -> Tier.VALUE
            quality -> Tier.VALUE
            cheap -> Tier.BUDGET
            else -> null
        }
    }

    private fun wordsOf(name: String) = normalize(name).trim()
    private fun baseName(d: Destination) = d.name.split('(', ',').first()

    private fun destinationKeys(d: Destination): List<String> {
        val base = wordsOf(baseName(d))
        val keys = linkedSetOf(wordsOf(d.name), base, wordsOf(d.district))
        val parts = base.split(" ")
        if (parts.size >= 3) {
            val two = parts.take(2).joinToString(" ")
            keys += two
            if (two.endsWith("s")) keys += two.dropLast(1)
        }
        if (parts.size >= 2 && parts[0].length >= 5 && parts[0] !in lex.firstWordStoplist) keys += parts[0]
        return keys.filter { it.length >= 4 }
    }

    private fun findDestinations(norm: String, destinations: List<Destination>): List<Match> {
        val cleaned = Regex(" from [a-z0-9]+ ").replace(norm, " ")
        val found = mutableListOf<Match>()
        for (d in destinations) {
            var best: String? = null
            for (k in destinationKeys(d)) if (has(cleaned, k) && (best == null || k.length > best.length)) best = k
            if (best != null) found += Match(d, best, cleaned.indexOf(" $best "), best == wordsOf(d.district) && best != wordsOf(baseName(d)))
        }
        return found.sortedWith(compareByDescending<Match> { it.key.length }.thenBy { it.dest.id })
    }

    // ------------------------------------------------------------ memory and defaults
    private fun readPreferences(memory: List<String>): Prefs {
        val likes = linkedSetOf<String>(); val dislikes = linkedSetOf<String>()
        var tier: Tier? = null
        for (note in memory) {
            val n = normalize(note)
            val negative = hasAny(n, listOf("hate", "dislike", "avoid", "not into", "no"))
            for (t in extractInterests(n)) if (negative) dislikes += t else likes += t
            if (!negative) tier = extractTier(n) ?: tier
        }
        return Prefs(likes.toList(), dislikes.toList(), tier)
    }

    private fun historyInterests(visits: List<BrainVisit>, destinations: List<Destination>): List<String> {
        val counts = linkedMapOf<String, Int>()
        val byId = destinations.associateBy { it.id }
        for (v in visits) byId[v.destinationId]?.tagSet()?.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(2).map { it.key }
    }

    private fun resolveQuery(ent: Entities, ctx: BrainContext, last: BrainQuery?, prefs: Prefs): Pair<BrainQuery, List<String>> {
        val assumed = mutableListOf<String>()
        val tier = ent.tier ?: last?.tier ?: prefs.tier ?: Tier.VALUE
        if (ent.tier == null && last?.tier == null && prefs.tier == null) assumed += "${tierLabel(tier)} tour"
        val travellers = ent.travellers ?: last?.travellers ?: 2
        if (ent.travellers == null && last == null) assumed += "$travellers travellers"
        val days = ent.days ?: last?.days ?: 3
        if (ent.days == null && last == null) assumed += "$days days"
        val month = ent.month ?: last?.month ?: ctx.month
        if (ent.month == null && last == null) assumed += monthsLong[month - 1]
        var interests: List<String> = if (ent.interests.isNotEmpty()) ent.interests else last?.interests ?: emptyList()
        if (ent.interests.isEmpty() && last == null) {
            val learned = if (prefs.likes.isNotEmpty()) prefs.likes else historyInterests(ctx.visits, ctx.destinations)
            interests = learned
            if (learned.isNotEmpty()) assumed += "interests: ${learned.joinToString(", ")}"
        }
        interests = interests.filter { it !in prefs.dislikes }
        var budget = ent.budget ?: if (last != null && ent.tier == null && ent.days == null && ent.travellers == null) last.budget else null
        if (budget == null) {
            budget = defaultBudget(tier, days, travellers)
            assumed += "budget about ${bdt(budget)}"
        }
        return BrainQuery(budget, days, travellers, month, interests, tier) to assumed
    }

    // ------------------------------------------------------------ planner glue
    private fun toPlanQuery(q: BrainQuery) = PlanQuery(q.budget, q.travellers, q.days, q.month, q.interests.toSet(), q.tier)
    private fun rank(pool: List<Destination>, q: BrainQuery) = ScoringEngine.rank(pool, toPlanQuery(q))
    private fun estimate(d: Destination, q: BrainQuery) = (d.costPerDayBdt * q.tier.costFactor * q.days * q.travellers).roundToInt()

    private fun planFor(d: Destination, q: BrainQuery, ctx: BrainContext): BrainPlan {
        val acts = ctx.activities.filter { it.destinationId == d.id }
        val items = ItineraryGenerator.generate(0, q.days, acts, q.interests.toSet(), emptySet(), q.tier)
            .map { PlanItem(it.dayNo, it.startHour, it.activityId) }
        return BrainPlan(d.id, d.name, d.district, q.tier, q.days, items, estimate(d, q))
    }

    private fun itineraryText(plan: BrainPlan, ctx: BrainContext): String {
        val byId = ctx.activities.associateBy { it.id }
        return (1..plan.days).mapNotNull { day ->
            val items = plan.items.filter { it.day == day }
            if (items.isEmpty()) null else "Day $day: " + items.joinToString("; ") { String.format(Locale.US, "%02d:00 %s", it.hour, byId[it.activityId]?.name.orEmpty()) }
        }.joinToString("\n")
    }

    private fun pickFor(m: Match, q: BrainQuery, ctx: BrainContext): Destination =
        if (!m.byDistrict) m.dest else rank(ctx.destinations.filter { it.district == m.dest.district }, q).first().destination

    private fun safetyNote(d: Destination) = if (d.tagSet().any { it in lex.safetyTags }) " Check weather and local advisories before you go." else ""

    /** The most severe active advisory that applies to this spot (national, its division or its district). */
    private fun advisoryNote(d: Destination, ctx: BrainContext): String {
        val rank = mapOf("danger" to 3, "warning" to 2, "info" to 1)
        val list = ctx.advisories.filter { it.scope == "national" || (it.scope == "division" && it.target == d.division) || (it.scope == "district" && it.target == d.district) }
        if (list.isEmpty()) return ""
        val a = list.sortedByDescending { rank[it.severity] ?: 0 }.first()
        return " Advisory (${a.severity}): ${a.title}.${if (a.text.isNotEmpty()) " " + a.text else ""}"
    }

    private fun whyLine(row: RankedDestination, q: BrainQuery): String {
        val f = row.score
        val bits = mutableListOf<String>()
        bits += if (f.weather == 1.0) "in season in ${monthsLong[q.month - 1]}" else "off-season in ${monthsLong[q.month - 1]}, so expect rain"
        if (q.interests.isNotEmpty() && f.interest >= 0.99) bits += "matches ${q.interests.joinToString(" and ")}"
        else if (q.interests.isNotEmpty() && f.interest > 0) bits += "partly matches ${q.interests.joinToString(", ")}"
        bits += if (f.affordable) "within your budget" else "about ${bdt(f.estimatedCost - q.budget)} over budget"
        return bits.joinToString(", ")
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double) = com.crowncouncil.orbit.domain.BangladeshMap.distanceKm(lat1, lon1, lat2, lon2)

    // ------------------------------------------------------------ the agent
    fun respond(utterance: String, ctx: BrainContext, state: BrainState = BrainState()): BrainReply {
        val norm = normalize(utterance)
        val month = ctx.month
        val prefs = readPreferences(ctx.memory)
        val ent = Entities(extractBudget(norm), extractDays(norm), extractTravellers(norm), extractMonth(norm, month), extractInterests(norm), extractTier(norm))
        val dests = findDestinations(norm, ctx.destinations)
        val visitedIds = ctx.visits.map { it.destinationId }.toSet()
        val anyEntity = ent.budget != null || ent.days != null || ent.travellers != null || ent.month != null || ent.interests.isNotEmpty() || ent.tier != null
        val shortText = norm.trim().split(" ").size <= 4
        fun out(reply: String, st: BrainState = state, plan: BrainPlan? = null, actions: List<BrainAction> = emptyList()) = BrainReply(reply, st, plan, actions)

        // housekeeping intents
        if (hasAny(norm, trig("forget"))) return out("Done. I cleared your saved preferences.", state.copy(lastQuery = null, lastPlaceId = null, offset = 0), actions = listOf(BrainAction.ForgetAll))
        if (hasAny(norm, trig("remember"))) {
            val m = Regex("(?:remember(?: that)?|note that|keep in mind(?: that)?)\\s+(.+)", RegexOption.IGNORE_CASE).find(utterance)
            val note = (m?.groupValues?.get(1) ?: utterance).replace(Regex("[\\u0000-\\u001f]"), " ").trim().take(200)
            return out("Saved: \"$note\". I will use it in future plans.", actions = listOf(BrainAction.Remember(note)))
        }
        if (hasAny(norm, trig("save")) && state.lastPlan != null) return out("Saved the ${state.lastPlan.destinationName} plan as your trip.", actions = listOf(BrainAction.SavePlan))
        if (shortText && hasAny(norm, trig("thanks"))) return out("You are welcome. Tell me when you want the next plan.")
        if (shortText && hasAny(norm, trig("greeting")) && !anyEntity) return out("Hello. Tell me what kind of trip you want, or say \"something I have not visited\". I will fill in the details.")
        if (hasAny(norm, trig("help")) && !hasAny(norm, trig("plan"))) return out("I can plan trips by budget, days, interests and season, pick Budget, Value or Fancy tours, build itineraries, estimate costs, compare places, suggest places you have not visited, and remember your preferences. $suggestions")

        // Explain a recommendation using the actual destination data and current planning assumptions.
        if (hasAny(norm, trig("why"))) {
            val target = dests.firstOrNull()?.dest
                ?: state.lastPlaceId?.let { id -> ctx.destinations.firstOrNull { it.id == id } }
            if (target == null) return out("I can explain the trade-offs, but I need a destination to evaluate first. Ask me to recommend a trip, then ask \"why this place?\".")
            val (query, assumed) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            val cost = estimate(target, query)
            val season = if (query.month in target.monthSet()) "your selected month is in its listed best season" else "your selected month is outside its listed best season"
            val matched = target.tagSet().filter { tag -> query.interests.any { it.equals(tag, ignoreCase = true) } }
            val fit = if (matched.isNotEmpty()) "It matches your interests in ${matched.joinToString(", ")}." else "Its recorded travel themes are ${target.tagSet().joinToString(", ")}."
            val budgetFit = if (cost <= query.budget) "The estimated total, ${bdt(cost)}, is within your ${bdt(query.budget)} budget." else "The estimated total, ${bdt(cost)}, is above your ${bdt(query.budget)} budget, so I would switch to Budget style or shorten the trip."
            val assumptions = if (assumed.isNotEmpty()) " Assumptions: ${assumed.joinToString(", ")}." else ""
            return out("Why ${target.name}: $fit The season check says $season. $budgetFit These are planning estimates, not live quotes.$assumptions", state.copy(lastQuery = query, lastPlaceId = target.id))
        }

        // progress
        if (hasAny(norm, trig("progress"))) {
            val districts = ctx.destinations.filter { it.id in visitedIds }.map { it.district }.toSet()
            val total = ctx.destinations.map { it.district }.toSet().size
            if (visitedIds.isEmpty()) return out("You have not marked any spots yet. Open the Map tab and tap a district to mark places you have visited.")
            val recent = ctx.visits.takeLast(3).mapNotNull { v -> ctx.destinations.firstOrNull { it.id == v.destinationId }?.name }
            return out("You have visited ${visitedIds.size} of ${ctx.destinations.size} spots across ${districts.size} of $total districts. Latest: ${recent.joinToString(", ")}.")
        }

        // navigation with live location
        if (hasAny(norm, trig("nav"))) {
            val loc = ctx.location
            val target = dests.firstOrNull()?.dest
                ?: loc?.targetId?.let { id -> ctx.destinations.firstOrNull { it.id == id } }
                ?: state.lastPlaceId?.let { id -> ctx.destinations.firstOrNull { it.id == id } }
            if (loc == null) return out("Open the Live map and start tracking, then ask again. I need your location to measure the distance.")
            if (target == null) return out("Plan a trip first, or name the place, and I will measure the distance from where you are.")
            val km = haversine(loc.lat, loc.lon, target.latitude, target.longitude)
            val roadKm = km * 1.3
            val hours = roadKm / 40
            val time = if (hours < 1) "${maxOf(5, (hours * 60).roundToInt())} minutes" else String.format(Locale.US, "%.1f hours", hours)
            val near = if (km <= 10) " You are inside ${target.district}. You can mark ${target.name} as visited." else ""
            return out(String.format(Locale.US, "%s: about %.0f km straight-line, roughly %.0f km by road, about %s at 40 km/h. Spot positions are district-level, so this is rough.%s", target.name, km, roadKm, time, near), state.copy(lastPlaceId = target.id))
        }

        // compare: one mention per named place or district, in the order written
        val mentions = dests.distinctBy { if (it.byDistrict) "d:" + it.dest.district else "n:" + it.dest.id }.sortedWith(compareBy<Match> { it.pos }.thenBy { it.dest.id })
        if (mentions.size >= 2 && (hasAny(norm, trig("compare")) || has(norm, "and"))) {
            val (query, _) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            val a = pickFor(mentions[0], query, ctx); val b = pickFor(mentions[1], query, ctx)
            val ra = RankedDestination(a, ScoringEngine.score(a, toPlanQuery(query))); val rb = RankedDestination(b, ScoringEngine.score(b, toPlanQuery(query)))
            val winner = if (ra.score.total >= rb.score.total) ra else rb
            val other = if (winner === ra) rb else ra
            fun line(r: RankedDestination) = "${r.destination.name}: ${bdt(r.score.estimatedCost)}, ${if (query.month in r.destination.monthSet()) "in season" else "off-season"} in ${monthsLong[query.month - 1]}, ${r.destination.tagSet().joinToString("/")}"
            return out("${winner.destination.name} fits better for ${query.days} days, ${query.travellers} travellers, ${tierLabel(query.tier)} tour.\n${line(winner)}\n${line(other)}\nCosts are estimates.", state.copy(lastQuery = query, lastPlaceId = winner.destination.id))
        }

        // transport names and dated fare evidence; never imply cached fares are live
        val asksTransport = Regex("\\b(bus|coach|train|rail|flight|plane|airfare|transport|fare|ticket|route|how to get)\\b").containsMatchIn(norm)
        if (asksTransport && (dests.isNotEmpty() || state.lastPlaceId != null)) {
            val match = dests.firstOrNull() ?: ctx.destinations.firstOrNull { it.id == state.lastPlaceId }?.let { Match(it, "", 0, false) }
            val target = match?.let { if (it.byDistrict) pickFor(it, resolveQuery(ent, ctx, state.lastQuery, prefs).first, ctx) else it.dest }
            if (target != null && ctx.transport != null) {
                val info = ctx.transport.describe(target)
                val checked = ctx.transport.checkedOn
                return out("Getting to ${target.name} from Dhaka:\\n" + info.lines.joinToString("\\n") +
                    "\\nFare data checked: $checked. This is dated reference data, not a live operator quote. Fares, availability and schedules can change; confirm with the railway or operator before paying. I cannot confirm real-time seat availability from this dataset.",
                    state.copy(lastPlaceId = target.id))
            }
        }

        // when to go
        if (hasAny(norm, trig("when")) && (dests.isNotEmpty() || state.lastPlaceId != null)) {
            val d = if (dests.isNotEmpty()) pickFor(dests[0], resolveQuery(ent, ctx, state.lastQuery, prefs).first, ctx) else (ctx.destinations.firstOrNull { it.id == state.lastPlaceId } ?: ctx.destinations.first())
            val ms = d.monthSet().sortedBy { (it + 8) % 12 }.map { monthsLong[it - 1] }
            val now = if (month in d.monthSet()) "${monthsLong[month - 1]} is in season." else "${monthsLong[month - 1]} is off-season, so expect rain."
            return out("Best months for ${d.name}: ${ms.joinToString(", ")}. $now${safetyNote(d)}${advisoryNote(d, ctx)}", state.copy(lastPlaceId = d.id))
        }

        // cost
        if (hasAny(norm, trig("cost")) && (dests.isNotEmpty() || state.lastPlaceId != null)) {
            val (query, _) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            val d = if (dests.isNotEmpty()) pickFor(dests[0], query, ctx) else (ctx.destinations.firstOrNull { it.id == state.lastPlaceId } ?: ctx.destinations.first())
            val total = estimate(d, query)
            val stay = (total * 0.4).roundToInt(); val food = (total * 0.25).roundToInt(); val transport = (total * 0.2).roundToInt(); val activities = (total * 0.15).roundToInt()
            val fits = if (total <= query.budget) "within" else "over"
            return out("${d.name}, ${query.days} days, ${query.travellers} travellers, ${tierLabel(query.tier)} tour: about ${bdt(total)}. Stay ${bdt(stay)}, food ${bdt(food)}, transport ${bdt(transport)}, activities ${bdt(activities)}. That is $fits a ${bdt(query.budget)} budget. Estimates only.", state.copy(lastQuery = query, lastPlaceId = d.id))
        }

        // itinerary for a named place (or the last one)
        if (hasAny(norm, trig("itinerary")) && (dests.isNotEmpty() || state.lastPlaceId != null)) {
            val (query, assumed) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            val d = if (dests.isNotEmpty()) pickFor(dests[0], query, ctx) else (ctx.destinations.firstOrNull { it.id == state.lastPlaceId } ?: ctx.destinations.first())
            val plan = planFor(d, query, ctx)
            val text = itineraryText(plan, ctx)
            val thin = if (plan.items.size < query.days * 3) "\nOnly ${plan.items.size} activities are listed for this spot so far." else ""
            val assumedLine = if (assumed.isNotEmpty()) "\nAssumed: ${assumed.joinToString(", ")}." else ""
            return out("${d.name}, ${query.days} days, ${tierLabel(query.tier)} tour, about ${bdt(plan.totalBdt)} for ${query.travellers}.\n${text.ifEmpty { "No suitable activities found." }}$thin$assumedLine${safetyNote(d)}${advisoryNote(d, ctx)}",
                BrainState(query, d.id, 0, plan), plan)
        }

        // info about a place
        if (dests.isNotEmpty() && (hasAny(norm, trig("info")) || (!anyEntity && !hasAny(norm, trig("plan"))))) {
            val (query, _) = resolveQuery(ent, ctx, state.lastQuery, prefs)
            val d = pickFor(dests[0], query, ctx)
            val months = d.monthSet().sorted().joinToString(", ") { monthsLong[it - 1].take(3) }
            return out("${d.name} (${d.district}): ${d.blurb} Tags: ${d.tagSet().joinToString(", ")}. Best months: $months. About ${bdt(estimate(d, query))} for ${query.days} days, ${query.travellers} travellers, ${tierLabel(query.tier)} tour (estimate).${safetyNote(d)}${advisoryNote(d, ctx)}", state.copy(lastPlaceId = d.id))
        }

        // follow-ups: "another one", "cheaper", "what about fancy", "make it 5 days"
        val tokens = norm.trim().split(" ").size
        val modifier = dests.isEmpty() && anyEntity && !hasAny(norm, trig("plan")) && tokens <= 8
        val followUp = state.lastQuery != null && (hasAny(norm, trig("alt")) || hasAny(norm, trig("cheaper")) || hasAny(norm, trig("fancier")) || modifier)
        if (followUp) {
            if (hasAny(norm, trig("cheaper"))) ent.tier = Tier.BUDGET
            if (hasAny(norm, trig("fancier"))) ent.tier = Tier.FANCY
        }

        // planning: the default for anything travel-shaped
        val wantsNew = hasAny(norm, trig("nextNew"))
        if (hasAny(norm, trig("plan")) || anyEntity || wantsNew || followUp || dests.isNotEmpty()) {
            val q: BrainQuery
            var assumed: List<String> = emptyList()
            if (followUp) {
                val last = state.lastQuery!!
                var nq = last.copy(tier = ent.tier ?: last.tier, budget = ent.budget ?: last.budget, days = ent.days ?: last.days, travellers = ent.travellers ?: last.travellers, month = ent.month ?: last.month)
                if (ent.interests.isNotEmpty()) nq = nq.copy(interests = ent.interests)
                if (ent.budget == null && (ent.tier != null || ent.days != null || ent.travellers != null)) nq = nq.copy(budget = defaultBudget(nq.tier, nq.days, nq.travellers))
                q = nq
            } else {
                val r = resolveQuery(ent, ctx, null, prefs); q = r.first; assumed = r.second
            }
            var pool = ctx.destinations
            if (dests.isNotEmpty()) {
                // Naming a specific spot plans that spot. Naming only a district considers every spot in it.
                val specific = dests.filter { !it.byDistrict }.map { it.dest.id }.toSet()
                pool = if (specific.isNotEmpty()) pool.filter { it.id in specific }
                else { val ds = dests.map { it.dest.district }.toSet(); pool.filter { it.district in ds } }
            }
            if (wantsNew) pool = pool.filter { it.id !in visitedIds }
            val rows = rank(pool, q)
            if (rows.isEmpty()) return out(if (wantsNew) "You have visited every spot in the list. Add new spots from More, Add a spot." else "I found nothing for that. $suggestions")
            val offset = if (hasAny(norm, trig("alt"))) ((state.offset + 1) % minOf(rows.size, 5)) else 0
            val top = rows[offset]
            val others = rows.filterIndexed { i, _ -> i != offset }.take(2)
            val plan = planFor(top.destination, q, ctx)
            val seen = if (top.destination.id in visitedIds) " You have visited it before." else ""
            val also = if (others.isNotEmpty()) "\nAlso good: ${others.joinToString(", ") { "${it.destination.name} (${bdt(it.score.estimatedCost)})" }}." else ""
            val head = "${tierLabel(q.tier)} tour pick: ${top.destination.name} (${top.destination.district}), about ${bdt(top.score.estimatedCost)} for ${q.travellers} ${if (q.travellers == 1) "person" else "people"}, ${q.days} ${if (q.days == 1) "day" else "days"}. ${cap(whyLine(top, q))}.$seen"
            val assumedLine = if (assumed.isNotEmpty()) "\nAssumed: ${assumed.joinToString(", ")}." else ""
            return out("$head$also$assumedLine\nCosts are estimates.${safetyNote(top.destination)}${advisoryNote(top.destination, ctx)}", BrainState(q, top.destination.id, offset, plan), plan)
        }

        // nothing matched: stay calm, give examples, do not interrogate
        return out("I did not catch a trip request in that. $suggestions").copy(understood = false)
    }
}
