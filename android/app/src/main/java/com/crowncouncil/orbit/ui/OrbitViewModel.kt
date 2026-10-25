package com.crowncouncil.orbit.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crowncouncil.orbit.BuildConfig
import com.crowncouncil.orbit.data.ActivityOption
import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.data.Expense
import com.crowncouncil.orbit.data.ItineraryItem
import com.crowncouncil.orbit.data.ItineraryRow
import com.crowncouncil.orbit.data.LiteRtTranslator
import com.crowncouncil.orbit.data.MemoryNote
import com.crowncouncil.orbit.data.ModelStore
import com.crowncouncil.orbit.data.OrbitDatabase
import com.crowncouncil.orbit.data.Repository
import com.crowncouncil.orbit.data.Trip
import com.crowncouncil.orbit.data.Visit
import com.crowncouncil.orbit.domain.Brain
import com.crowncouncil.orbit.domain.BrainAction
import com.crowncouncil.orbit.domain.BrainAdvisory
import com.crowncouncil.orbit.domain.BrainContext
import com.crowncouncil.orbit.domain.BrainLocation
import com.crowncouncil.orbit.domain.BrainPlan
import com.crowncouncil.orbit.domain.BrainQuery
import com.crowncouncil.orbit.domain.BrainState
import com.crowncouncil.orbit.domain.BrainVisit
import com.crowncouncil.orbit.domain.ItineraryGenerator
import com.crowncouncil.orbit.domain.Lexicon
import com.crowncouncil.orbit.domain.PlanQuery
import com.crowncouncil.orbit.domain.RankedDestination
import com.crowncouncil.orbit.domain.ScoringEngine
import com.crowncouncil.orbit.domain.Tier
import com.crowncouncil.orbit.domain.Translation
import com.crowncouncil.orbit.domain.TransportData
import com.crowncouncil.orbit.net.ApiClient
import com.crowncouncil.orbit.net.ApiResult
import com.crowncouncil.orbit.net.Consent
import com.crowncouncil.orbit.net.SessionStore
import com.crowncouncil.orbit.net.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PriceStat(val median: Int, val count: Int)

data class ChatMessage(val fromUser: Boolean, val text: String, val plan: JSONObject? = null, val brainPlan: BrainPlan? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class OrbitViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(OrbitDatabase.get(app))
    val session = SessionStore(app)
    val api = ApiClient(BuildConfig.API_BASE_URL, session)
    val baseUrl: String get() = BuildConfig.API_BASE_URL

    // ---------------------------------------------------------------- planning state
    val query = MutableStateFlow(PlanQuery())
    val favorites = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedTripId = MutableStateFlow<Long?>(null)
    private val rainyDays = MutableStateFlow<Map<Long, Set<Int>>>(emptyMap())

    private fun <T> share(flow: Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val destinations: StateFlow<List<Destination>> = share(repo.destinations, emptyList())
    val trips: StateFlow<List<Trip>> = share(repo.trips, emptyList())
    val visits: StateFlow<List<Visit>> = share(repo.visits, emptyList())
    val notes: StateFlow<List<MemoryNote>> = share(repo.notes, emptyList())
    private val allActivities: StateFlow<List<ActivityOption>> = share(repo.activities, emptyList())
    val visitedIds: StateFlow<Set<Long>> = share(visits.map { v -> v.map { it.destinationId }.toSet() }, emptySet())

    val ranked: StateFlow<List<RankedDestination>> =
        share(combine(destinations, query, favorites) { d, q, f -> ScoringEngine.rank(d, q, f) }, emptyList())

    val currentTrip: StateFlow<Trip?> =
        share(combine(trips, selectedTripId) { ts, id -> ts.firstOrNull { it.id == id } ?: ts.firstOrNull() }, null)

    val rows: StateFlow<List<ItineraryRow>> =
        share(currentTrip.flatMapLatest { t -> if (t == null) flowOf(emptyList()) else repo.rows(t.id) }, emptyList())

    val expenses: StateFlow<List<Expense>> =
        share(currentTrip.flatMapLatest { t -> if (t == null) flowOf(emptyList()) else repo.expenses(t.id) }, emptyList())

    val rainy: StateFlow<Set<Int>> =
        share(combine(currentTrip, rainyDays) { t, m -> m[t?.id] ?: emptySet() }, emptySet())

    // ---------------------------------------------------------------- account state
    val user = MutableStateFlow(session.user)
    val guest = MutableStateFlow(session.guest)
    val signedIn: Boolean get() = user.value != null && session.token != null

    // ---------------------------------------------------------------- guide state
    val chat = MutableStateFlow(listOf(ChatMessage(false, "I am your ORBIT guide. Tell me what you want, or tap a trip style below. I will use sensible defaults instead of asking.")))
    val chatBusy = MutableStateFlow(false)
    val generatedImageB64 = MutableStateFlow<String?>(null)
    val imageBusy = MutableStateFlow(false)
    val imageError = MutableStateFlow<String?>(null)
    private var brainState = BrainState()
    private val brain = Brain(Lexicon(app.assets.open("brain-data.json").bufferedReader().use { it.readText() }))
    private val modelStore = ModelStore(app)
    private val translator = LiteRtTranslator(app, modelStore)
    val llmReady = MutableStateFlow(modelStore.exists())
    val llmEnabled = MutableStateFlow(modelStore.enabled)
    val llmSizeMb: Long get() = modelStore.sizeMb()

    // ---------------------------------------------------------------- researched data, advisories and community prices
    /** Dated fares from the research audit, bundled with the app so "getting there" works offline. */
    val transport = TransportData(app.assets.open("travel-costs.json").bufferedReader().use { it.readText() })

    // Advisories and community prices come from the backend and are cached so they still show offline.
    private val cache = app.getSharedPreferences("orbit_cache", Context.MODE_PRIVATE)
    val advisories = MutableStateFlow<List<BrainAdvisory>>(parseAdvisories(cache.getString("advisories", null)))
    val prices = MutableStateFlow<Map<Long, Map<String, PriceStat>>>(parsePrices(cache.getString("prices", null)))
    private var lastRefresh = 0L

    init {
        viewModelScope.launch { repo.seedIfEmpty() }
        if (signedIn) viewModelScope.launch { syncVisits() }
        refreshExtras()
    }

    fun update(transform: (PlanQuery) -> PlanQuery) = query.update(transform)
    fun toggleFavorite(id: Long) = favorites.update { if (id in it) it - id else it + id }

    // ---------------------------------------------------------------- trips
    fun planTrip(dest: Destination, onDone: () -> Unit) {
        viewModelScope.launch {
            val q = query.value
            val id = repo.createTrip(
                Trip(
                    title = dest.name, destinationId = dest.id, startMonth = q.month, days = q.days,
                    budget = q.budget, travellers = q.travellers, interests = q.interests.joinToString(","), tier = q.tier.name
                )
            )
            val acts = repo.activitiesFor(dest.id)
            repo.replaceItinerary(id, ItineraryGenerator.generate(id, q.days, acts, q.interests, tier = q.tier))
            selectedTripId.value = id
            onDone()
        }
    }

    /** Adaptive re-planning: toggling rain on a day regenerates the itinerary with indoor-only activities for it. */
    fun toggleRain(day: Int) {
        val t = currentTrip.value ?: return
        viewModelScope.launch {
            val current = rainyDays.value[t.id] ?: emptySet()
            val next = if (day in current) current - day else current + day
            rainyDays.update { it + (t.id to next) }
            val acts = repo.activitiesFor(t.destinationId)
            val interests = t.interests.split(",").filter { it.isNotBlank() }.toSet()
            val tier = runCatching { Tier.valueOf(t.tier) }.getOrDefault(Tier.VALUE)
            repo.replaceItinerary(t.id, ItineraryGenerator.generate(t.id, t.days, acts, interests, next, tier))
        }
    }

    fun addExpense(category: String, amount: Int, note: String) {
        val t = currentTrip.value ?: return
        viewModelScope.launch { repo.addExpense(Expense(tripId = t.id, category = category, amount = amount, note = note)) }
    }

    fun deleteCurrentTrip() {
        val t = currentTrip.value ?: return
        viewModelScope.launch { repo.deleteTrip(t.id); selectedTripId.value = null }
    }

    /** Saves a plan returned by the optional cloud guide as a local trip. Activity ids are checked against local data. */
    fun savePlan(plan: JSONObject, onDone: () -> Unit) {
        viewModelScope.launch {
            val destId = plan.optJSONObject("destination")?.optLong("id") ?: return@launch
            val dest = destinations.value.firstOrNull { it.id == destId } ?: return@launch
            val q = query.value
            val tier = runCatching { Tier.valueOf(plan.optString("tier").uppercase()) }.getOrDefault(q.tier)
            val days = plan.optInt("days", q.days).coerceIn(1, 14)
            val id = repo.createTrip(
                Trip(title = dest.name, destinationId = dest.id, startMonth = q.month, days = days, budget = q.budget,
                    travellers = q.travellers, interests = q.interests.joinToString(","), tier = tier.name)
            )
            val valid = repo.activitiesFor(dest.id).map { it.id }.toSet()
            val arr = plan.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optLong("activityId") in valid }
                .map { ItineraryItem(tripId = id, activityId = it.getLong("activityId"), dayNo = it.getInt("day"), startHour = it.getInt("hour")) }
            repo.saveItems(items)
            selectedTripId.value = id
            onDone()
        }
    }

    /** Saves a plan from ORBIT Brain as a local trip. */
    fun saveBrainPlan(plan: BrainPlan, q: BrainQuery?, onDone: () -> Unit) {
        viewModelScope.launch {
            val dest = destinations.value.firstOrNull { it.id == plan.destinationId } ?: return@launch
            val use = q ?: brainState.lastQuery ?: BrainQuery(query.value.budget, plan.days, query.value.travellers, query.value.month, query.value.interests.toList(), plan.tier)
            val id = repo.createTrip(Trip(title = dest.name, destinationId = dest.id, startMonth = use.month, days = plan.days, budget = use.budget,
                travellers = use.travellers, interests = use.interests.joinToString(","), tier = plan.tier.name))
            repo.saveItems(plan.items.map { ItineraryItem(tripId = id, activityId = it.activityId, dayNo = it.day, startHour = it.hour) })
            selectedTripId.value = id
            onDone()
        }
    }

    // ---------------------------------------------------------------- visits
    /** A "gps" visit should come with the phone's position so the server can verify it. */
    fun markVisited(destinationId: Long, rating: Int?, note: String, source: String = "self", lat: Double? = null, lon: Double? = null, mock: Boolean = false) {
        val v = Visit(destinationId, System.currentTimeMillis(), rating, note.take(300), source)
        viewModelScope.launch { repo.saveVisit(v); pushVisit(v, lat, lon, mock) }
    }

    fun unmarkVisited(destinationId: Long) {
        viewModelScope.launch {
            repo.removeVisit(destinationId)
            if (signedIn) api.delete("/api/visits/$destinationId")
        }
    }

    private suspend fun pushVisit(v: Visit, lat: Double? = null, lon: Double? = null, mock: Boolean = false) {
        if (!signedIn) return
        api.post("/api/visits", JSONObject().apply {
            put("destinationId", v.destinationId); v.rating?.let { put("rating", it) }; put("note", v.note); put("source", v.source)
            if (lat != null && lon != null) { put("lat", lat); put("lon", lon); put("mock", mock) }
        })
    }

    /** After login: pull server visits we lack, push local visits the server lacks. */
    private suspend fun syncVisits() {
        val res = api.get("/api/visits")
        if (!res.ok) return
        val arr = res.body?.optJSONArray("visits") ?: JSONArray()
        val remote = (0 until arr.length()).map { arr.getJSONObject(it) }
        val remoteIds = remote.map { it.getLong("destinationId") }.toSet()
        val localIds = visitedIds.value
        remote.filter { it.getLong("destinationId") !in localIds }.forEach {
            repo.saveVisit(Visit(it.getLong("destinationId"), System.currentTimeMillis(), if (it.isNull("rating")) null else it.optInt("rating"), it.optString("note"), it.optString("source", "self")))
        }
        visits.value.filter { it.destinationId !in remoteIds }.forEach { pushVisit(it) }
    }

    /** Merge approved community spots into the local list, with simple generated activities. */
    fun refreshCommunity() {
        viewModelScope.launch {
            val res = api.request("GET", "/api/destinations", auth = false)
            val arr = res.body?.optJSONArray("destinations") ?: return@launch
            val have = destinations.value.map { it.id }.toSet()
            val newDests = mutableListOf<Destination>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                if (!j.optBoolean("community") || j.getLong("id") in have) continue
                fun csv(a: JSONArray) = (0 until a.length()).joinToString(",") { a.get(it).toString() }
                newDests += Destination(
                    j.getLong("id"), j.getString("name"), j.getString("district"), j.optString("division"), "Bangladesh",
                    j.optDouble("latitude"), j.optDouble("longitude"), csv(j.getJSONArray("tags")), j.optInt("distanceKm", 200),
                    j.optInt("costPerDayBdt", 2000), j.optInt("recommendedDays", 1), csv(j.getJSONArray("bestMonths")), j.optString("blurb")
                )
            }
            if (newDests.isEmpty()) return@launch
            repo.addDestinations(newDests, newDests.flatMap { d ->
                val tag = d.tags.split(",").first()
                listOf(
                    ActivityOption(100000 + d.id * 10 + 1, d.id, "Visit ${d.name}", tag, 100, false, 2),
                    ActivityOption(100000 + d.id * 10 + 2, d.id, "Local meal in ${d.district}", "food", 500, true, 2),
                    ActivityOption(100000 + d.id * 10 + 3, d.id, "${d.district} local market visit", "culture", 300, true, 1)
                )
            })
        }
    }

    // ---------------------------------------------------------------- advisories and community prices
    private fun parseAdvisories(raw: String?): List<BrainAdvisory> = runCatching {
        val arr = JSONObject(raw ?: return emptyList()).getJSONArray("advisories")
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { runCatching { java.time.Instant.parse(it.getString("expiresAt")).isAfter(java.time.Instant.now()) }.getOrDefault(false) }
            .map { BrainAdvisory(it.getString("scope"), if (it.isNull("target")) null else it.getString("target"), it.getString("severity"), it.getString("title"), it.optString("text")) }
    }.getOrDefault(emptyList())

    private fun parsePrices(raw: String?): Map<Long, Map<String, PriceStat>> = runCatching {
        val o = JSONObject(raw ?: return emptyMap()).getJSONObject("prices")
        o.keys().asSequence().associate { id ->
            val kinds = o.getJSONObject(id)
            id.toLong() to kinds.keys().asSequence().associateWith { k -> kinds.getJSONObject(k).let { PriceStat(it.getInt("median"), it.getInt("count")) } }
        }
    }.getOrDefault(emptyMap())

    /** Pulls the latest advisories and community prices (at most once a minute). Failures keep the cached copy. */
    fun refreshExtras(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefresh < 60_000) return
        lastRefresh = now
        viewModelScope.launch {
            api.request("GET", "/api/advisories", auth = false).takeIf { it.ok }?.body?.let {
                cache.edit().putString("advisories", it.toString()).apply(); advisories.value = parseAdvisories(it.toString())
            }
            api.request("GET", "/api/prices", auth = false).takeIf { it.ok }?.body?.let {
                cache.edit().putString("prices", it.toString()).apply(); prices.value = parsePrices(it.toString())
            }
        }
    }

    fun reportPrice(destinationId: Long, kind: String, amount: Int, onResult: (String) -> Unit) {
        viewModelScope.launch {
            if (!signedIn) { onResult("Sign in to report prices."); return@launch }
            val r = api.post("/api/prices", JSONObject().put("destinationId", destinationId).put("kind", kind).put("amountBdt", amount))
            if (r.ok) { refreshExtras(force = true); onResult("Thanks. Your report was added.") }
            else onResult(r.message ?: if (r.status == 0) "Cannot reach the server." else "Could not send the report.")
        }
    }

    // ---------------------------------------------------------------- account
    fun continueAsGuest() { session.guest = true; guest.value = true }

    fun signup(identifier: String, password: String, name: String, consent: Consent, onResult: (ApiResult) -> Unit) {
        viewModelScope.launch {
            onResult(api.post("/api/auth/signup", JSONObject().apply {
                put("identifier", identifier); put("password", password); put("displayName", name)
                put("consent", JSONObject().put("terms", true).put("privacy", true)
                    .put("ai", consent.ai).put("location", consent.location).put("leaderboard", consent.leaderboard))
            }, auth = false))
        }
    }

    fun verify(identifier: String, code: String, onResult: (ApiResult) -> Unit) {
        viewModelScope.launch { val r = api.post("/api/auth/verify", JSONObject().put("identifier", identifier).put("code", code), auth = false); finishAuth(r); onResult(r) }
    }

    fun login(identifier: String, password: String, onResult: (ApiResult) -> Unit) {
        viewModelScope.launch { val r = api.post("/api/auth/login", JSONObject().put("identifier", identifier).put("password", password), auth = false); finishAuth(r); onResult(r) }
    }

    private suspend fun finishAuth(r: ApiResult) {
        val token = r.body?.optString("token").orEmpty()
        val u = r.body?.optJSONObject("user")
        if (r.ok && token.isNotEmpty() && u != null) {
            session.token = token; session.guest = false
            val info = UserInfo.from(u); session.user = info; user.value = info; guest.value = false
            syncVisits()
        }
    }

    fun signOut() { session.clear(); user.value = null; guest.value = false; chat.value = chat.value.take(1) }

    fun updateConsent(c: Consent, onResult: (ApiResult) -> Unit = {}) {
        viewModelScope.launch {
            val r = api.put("/api/me/consent", JSONObject().put("ai", c.ai).put("location", c.location).put("leaderboard", c.leaderboard))
            if (r.ok) { val u = user.value?.copy(consent = c); session.user = u; user.value = u }
            onResult(r)
        }
    }

    fun deleteAccount(onResult: (ApiResult) -> Unit) {
        viewModelScope.launch {
            val r = api.delete("/api/me")
            if (r.ok) { repo.clearVisits(); signOut() }
            onResult(r)
        }
    }

    // ---------------------------------------------------------------- guide
    /**
     * Sends a message to the guide. The default guide is ORBIT Brain, which runs on this phone: no account, no network,
     * nothing leaves the device. The optional cloud guide is used only when the build enables it and the traveller opted in.
     */
    fun generateTravelImage(prompt: String) {
        if (prompt.isBlank() || imageBusy.value) return
        imageBusy.value = true
        imageError.value = null
        generatedImageB64.value = null
        viewModelScope.launch {
            val result = api.post("/api/agent/image", JSONObject().put("prompt", prompt.trim().take(1200)))
            if (result.ok) {
                generatedImageB64.value = result.body?.optString("b64")?.takeIf { it.isNotBlank() && it != "null" }
                if (generatedImageB64.value == null) imageError.value = "The image provider returned no embedded image. Please try again."
            } else {
                imageError.value = result.body?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: when (result.body?.optString("error")) {
                        "ai_consent_required" -> "Enable AI guide consent in Account settings to generate images."
                        "image_generation_not_configured" -> "Image generation is not configured on the ORBIT server yet."
                        else -> "Could not generate the image. Check your connection and try again."
                    }
            }
            imageBusy.value = false
        }
    }

    fun ask(text: String, location: Pair<Double, Double>? = null, targetId: Long? = null) {
        if (text.isBlank() || chatBusy.value) return
        chat.update { it + ChatMessage(true, text.trim()) }
        chatBusy.value = true
        viewModelScope.launch {
            val cloudOk = BuildConfig.USE_CLOUD_AI && signedIn && user.value?.consent?.ai == true
            val reply = if (cloudOk) cloudReply(text.trim(), location, targetId) ?: nativeReply(text.trim(), location, targetId) else nativeReply(text.trim(), location, targetId)
            chat.update { it + reply }
            chatBusy.value = false
        }
    }

    private suspend fun nativeReply(text: String, location: Pair<Double, Double>?, targetId: Long?): ChatMessage {
        val ctx = BrainContext(
            destinations = destinations.value, activities = allActivities.value,
            visits = visits.value.reversed().map { BrainVisit(it.destinationId) },   // oldest first
            memory = notes.value.map { it.text }, month = java.time.LocalDate.now().monthValue,
            location = location?.let { BrainLocation(it.first, it.second, targetId) },
            advisories = advisories.value
        )
        var r = withContext(Dispatchers.Default) { brain.respond(text, ctx, brainState) }
        // Optional language layer: only when ORBIT Brain did not understand, and only if the traveller imported a model and turned it on.
        if (!r.understood && llmEnabled.value && modelStore.exists()) {
            val priorContext = buildString {
                brainState.lastQuery?.let { q ->
                    append("Last request: ${q.days} days, BDT ${q.budget}, ${q.travellers} travellers, interests ${q.interests.joinToString()}, style ${q.tier.name}. ")
                }
                val lastDestination = destinations.value.firstOrNull { it.id == brainState.lastPlaceId }?.name
                    ?: brainState.lastPlan?.destinationName
                if (!lastDestination.isNullOrBlank()) append("Last destination: $lastDestination. ")
            }
            val command = Translation.sanitize(runCatching { translator.translate(text, priorContext) }.getOrNull())
            if (command != null) {
                val second = withContext(Dispatchers.Default) { brain.respond(command, ctx, brainState) }
                if (second.understood) r = second.copy(reply = Translation.NOTICE + "\n" + second.reply)   // the model's own words are never shown
            }
        }
        brainState = r.state
        for (a in r.actions) when (a) {
            is BrainAction.Remember -> repo.addNote(a.text)
            BrainAction.ForgetAll -> repo.clearNotes()
            BrainAction.SavePlan -> r.state.lastPlan?.let { saveBrainPlan(it, r.state.lastQuery) {} }
        }
        return ChatMessage(false, r.reply, brainPlan = r.plan)
    }

    private suspend fun cloudReply(text: String, location: Pair<Double, Double>?, targetId: Long?): ChatMessage? {
        val history = JSONArray().also { arr ->
            chat.value.drop(1).dropLast(1).takeLast(10).forEach { arr.put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
        }
        val body = JSONObject().put("message", text).put("history", history)
        if (location != null) body.put("location", JSONObject().put("lat", location.first).put("lon", location.second).apply { targetId?.let { put("targetId", it) } })
        val r = api.post("/api/agent/chat", body)
        return if (r.ok) ChatMessage(false, r.body?.optString("reply").orEmpty(), r.body?.optJSONObject("plan")) else null
    }

    fun importModel(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            // Release any loaded weights before the file is replaced; otherwise the old engine could remain active.
            translator.close()
            val res = modelStore.import(uri)
            llmReady.value = modelStore.exists()
            onResult(res.fold({ "Model added (${it / 1_048_576} MB)." }, { it.message ?: "Could not add the model." }))
        }
    }

    fun removeModel() { translator.close(); modelStore.remove(); llmReady.value = false; llmEnabled.value = false }
    fun setLlmEnabled(on: Boolean) { modelStore.enabled = on; llmEnabled.value = on }

    override fun onCleared() { translator.close(); super.onCleared() }

    fun deleteNote(id: Long) { viewModelScope.launch { repo.deleteNote(id) } }
    fun forgetAllNotes() { viewModelScope.launch { repo.clearNotes() } }
}
