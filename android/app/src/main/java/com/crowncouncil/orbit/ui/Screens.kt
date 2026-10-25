@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.crowncouncil.orbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowncouncil.orbit.domain.RankedDestination
import com.crowncouncil.orbit.domain.Tier
import com.crowncouncil.orbit.domain.tagSet
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Home
@Composable
fun HomeScreen(vm: OrbitViewModel) {
    val trip = vm.currentTrip.collectAsState().value
    val rows by vm.rows.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val dests by vm.destinations.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val visited by vm.visitedIds.collectAsState()
            val districtsTotal = dests.map { it.district }.distinct().size
            val districtsDone = dests.filter { it.id in visited }.map { it.district }.distinct().size
            val name = vm.user.collectAsState().value?.displayName
            Box(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Navy, Teal))).padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR NEXT ADVENTURE", fontSize = 11.sp, letterSpacing = 2.sp, color = Amber, fontWeight = FontWeight.Bold)
                    Text(if (name != null) "Welcome, $name" else "Find your\nnext escape.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("Thoughtful plans. Local discoveries. More meaningful journeys.", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 20.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Text("$districtsDone", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("of $districtsTotal districts explored", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            LinearProgressIndicator(
                                progress = { if (districtsTotal == 0) 0f else districtsDone.toFloat() / districtsTotal },
                                modifier = Modifier.fillMaxWidth(), color = Amber, trackColor = Color.White.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }
        }
        item {
            val adv by vm.advisories.collectAsState()
            val tripDest = trip?.let { t0 -> dests.firstOrNull { it.id == t0.destinationId } }
            val relevant = adv.filter { it.scope == "national" || (tripDest != null && ((it.scope == "division" && it.target == tripDest.division) || (it.scope == "district" && it.target == tripDest.district))) }
                .sortedByDescending { severityRank(it.severity) }.take(4)
            if (relevant.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("ADVISORIES")
                    relevant.forEach { a -> InfoCard(a.title, a.text, container = severityColor(a.severity).copy(alpha = 0.16f)) }
                }
            }
        }
        if (trip == null) {
            item {
                InfoCard(
                    "Your journey starts here",
                    "Set your budget, choose your travel style, and discover destinations ranked for you.",
                    container = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        } else {
            val dest = dests.firstOrNull { it.id == trip.destinationId }
            val spent = expenses.sumOf { it.amount }
            item {
                InfoCard(
                    title = trip.title,
                    body = "${trip.days} days | ${trip.travellers} traveller(s) | ${monthName(trip.startMonth)} | budget ${bdt(trip.budget)}",
                    container = Teal, content = Color.White
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { InfoCard("Weather", weatherLabel(dest, trip.startMonth)) }
                    Box(Modifier.weight(1f)) { InfoCard("Spent", bdt(spent)) }
                }
            }
            if (dest != null) item { TransportCard(vm.transport.describe(dest)) }
            item { SectionLabel("NEXT UP") }
            items(rows.take(3), key = { it.id }) { r ->
                InfoCard(r.name, "Day ${r.dayNo}, %02d:00 | ${r.category.cap()}".format(r.startHour))
            }
        }
    }
}

// ---------------------------------------------------------------- Plan
@Composable
fun PlanScreen(vm: OrbitViewModel, onFind: () -> Unit) {
    val q by vm.query.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Title("Plan your trip")
        SectionLabel("TOUR STYLE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tier.entries.forEach { t ->
                FilterChip(selected = q.tier == t, onClick = { vm.update { it.copy(tier = t) } }, label = { Text(t.label) })
            }
        }
        Text(tierBlurb(q.tier), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Stepper("Days", q.days, 1..7) { v -> vm.update { it.copy(days = v) } }
        Stepper("Travellers", q.travellers, 1..10) { v -> vm.update { it.copy(travellers = v) } }

        SectionLabel("BUDGET: ${bdt(q.budget)}")
        Slider(
            value = q.budget.toFloat(),
            onValueChange = { v -> vm.update { p -> p.copy(budget = (v / 500f).roundToInt() * 500) } },
            valueRange = 5_000f..100_000f
        )

        SectionLabel("TRAVEL MONTH")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..12).forEach { m ->
                FilterChip(selected = q.month == m, onClick = { vm.update { it.copy(month = m) } }, label = { Text(monthName(m)) })
            }
        }

        SectionLabel("INTERESTS")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERESTS.forEach { tag ->
                FilterChip(
                    selected = tag in q.interests,
                    onClick = { vm.update { p -> p.copy(interests = if (tag in p.interests) p.interests - tag else p.interests + tag) } },
                    label = { Text(tag.cap()) }
                )
            }
        }
        Button(onClick = onFind, modifier = Modifier.fillMaxWidth()) { Text(tr("Find destinations")) }
    }
}

// ---------------------------------------------------------------- Explore
@Composable
fun ExploreScreen(vm: OrbitViewModel, onPlanned: () -> Unit) {
    val ranked by vm.ranked.collectAsState()
    val favs by vm.favorites.collectAsState()
    val q by vm.query.collectAsState()
    val visited by vm.visitedIds.collectAsState()
    val prices by vm.prices.collectAsState()
    var reportFor by remember { mutableStateOf<com.crowncouncil.orbit.data.Destination?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshCommunity(); vm.refreshExtras() }
    var search by remember { mutableStateOf("") }
    val shown = remember(ranked, search) {
        val t = search.trim()
        if (t.isEmpty()) ranked else ranked.filter {
            it.destination.name.contains(t, ignoreCase = true) ||
                it.destination.district.contains(t, ignoreCase = true) ||
                it.destination.division.contains(t, ignoreCase = true)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Title("Explore")
            Text("${q.tier.label} tour | ${q.days} days, ${bdt(q.budget)}, ${monthName(q.month)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text(tr("Search spot, district or division")) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        notice?.let { n -> item { Text(n, fontWeight = FontWeight.SemiBold, color = Teal) } }
        if (shown.isEmpty()) item { InfoCard("No matches", "Try another spot, district or division name.") }
        items(shown, key = { it.destination.id }) { r ->
            DestinationCard(
                r, visited = r.destination.id in visited, favorite = r.destination.id in favs,
                prices = prices[r.destination.id], transport = vm.transport.describe(r.destination), onReport = { reportFor = r.destination },
                onFavorite = { vm.toggleFavorite(r.destination.id) },
                onPlan = { vm.planTrip(r.destination, onPlanned) }
            )
        }
    }

    reportFor?.let { d ->
        ReportPriceDialog(d.name, onDismiss = { reportFor = null }) { kind, amount ->
            vm.reportPrice(d.id, kind, amount) { msg -> notice = msg }
            reportFor = null
        }
    }
}

@Composable
private fun ReportPriceDialog(name: String, onDismiss: () -> Unit, onSend: (String, Int) -> Unit) {
    val kinds = listOf("bus", "train", "launch", "stay", "food", "guide", "entry")
    var kind by remember { mutableStateOf(kinds.first()) }
    var amount by remember { mutableStateOf("") }
    val value = amount.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report a price: $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Share what you actually paid, in taka, for one person. Your latest report replaces your earlier one.", fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.cap()) }) }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(6) }, label = { Text("Amount (BDT)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(enabled = value != null && value > 0, onClick = { onSend(kind, value ?: 0) }) { Text(tr("Send")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun DestinationCard(r: RankedDestination, visited: Boolean, favorite: Boolean, prices: Map<String, PriceStat>?, transport: com.crowncouncil.orbit.domain.TransportInfo, onReport: () -> Unit, onFavorite: () -> Unit, onPlan: () -> Unit) {
    val d = r.destination
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Navy), contentAlignment = Alignment.Center) {
                    Text("${r.score.percent}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${d.district}, ${d.division} | " + d.tagSet().joinToString(", ") { it.cap() }, fontSize = 12.sp, color = Teal)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = Rose)
                }
            }
            Text(d.blurb, fontSize = 14.sp)
            Text(
                "Estimated ${bdt(r.score.estimatedCost)} | ${if (r.score.affordable) "within budget" else "over budget"}" + if (visited) " | Visited" else "",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (r.score.affordable) Green else Rose
            )
            SectionLabel("WHY THIS SCORE")
            FactorBar("Budget fit", r.score.budget, Orange)
            FactorBar("Interest match", r.score.interest, Purple)
            FactorBar("Time fit", r.score.time, Teal)
            FactorBar("Weather", r.score.weather, Cyan)
            FactorBar("Route", r.score.route, Green)
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = !open }) { Text(if (open) "Hide travel costs and prices" else "Getting there, costs and prices") }
            if (open) {
                transport.lines.forEach { Text(it, fontSize = 13.sp) }
                if (transport.footnote.isNotEmpty()) Text(transport.footnote, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionLabel("COMMUNITY PRICES")
                if (prices.isNullOrEmpty()) Text("No traveller reports yet.", fontSize = 13.sp)
                else prices.entries.sortedBy { it.key }.forEach { (kind, p) ->
                    Text("${kind.cap()}: ${bdt(p.median)} typical (${p.count} ${if (p.count == 1) "report" else "reports"})", fontSize = 13.sp)
                }
                Text("Community reports are unverified. Confirm before you pay.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onReport) { Text(tr("Report a price")) }
            }
            Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) { Text(tr("Plan this trip")) }
        }
    }
}

@Composable
private fun FactorBar(label: String, value: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(100.dp))
        LinearProgressIndicator(
            progress = { value.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, modifier = Modifier.width(44.dp).padding(start = 8.dp))
    }
}

// ---------------------------------------------------------------- Itinerary
@Composable
fun ItineraryScreen(vm: OrbitViewModel) {
    val trip = vm.currentTrip.collectAsState().value
    val rows by vm.rows.collectAsState()
    val rainy by vm.rainy.collectAsState()
    var selected by remember { mutableIntStateOf(1) }

    if (trip == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Title("Itinerary")
            InfoCard("No trip yet", "Plan a trip first, then its day-by-day schedule appears here.")
        }
        return
    }
    val day = selected.coerceIn(1, trip.days)
    val todays = rows.filter { it.dayNo == day }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Title(trip.title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..trip.days).forEach { d ->
                    FilterChip(selected = day == d, onClick = { selected = d }, label = { Text(if (d in rainy) "Day $d (rain)" else "Day $d") })
                }
            }
        }
        item {
            OutlinedButton(onClick = { vm.toggleRain(day) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (day in rainy) "Clear rain on Day $day" else "Simulate rain on Day $day")
            }
        }
        if (day in rainy) item { InfoCard("Re-planned", "Outdoor activities were moved off this day; only indoor options remain.", Color(0xFFFFF3E0)) }
        if (todays.isEmpty()) item { InfoCard("Nothing scheduled", "No suitable activities for this day.") }
        items(todays, key = { it.id }) { r ->
            InfoCard(
                "%02d:00  %s".format(r.startHour, r.name),
                "${r.category.cap()} | ${if (r.isIndoor) "Indoor" else "Outdoor"} | ${bdt(r.estimatedCost)} per person"
            )
        }
    }
}

// ---------------------------------------------------------------- Budget
@Composable
fun BudgetScreen(vm: OrbitViewModel) {
    val trip = vm.currentTrip.collectAsState().value
    val rows by vm.rows.collectAsState()
    val expenses by vm.expenses.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    if (trip == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Title("Budget")
            InfoCard("No trip yet", "Plan a trip to start tracking expenses.")
        }
        return
    }
    val planned = rows.sumOf { it.estimatedCost } * trip.travellers
    val spent = expenses.sumOf { it.amount }
    val remaining = trip.budget - spent

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Title("Budget")
            InfoCard(
                "Budget ${bdt(trip.budget)}",
                "Planned activities: ${bdt(planned)}\nSpent: ${bdt(spent)}\nRemaining: ${bdt(remaining)}"
            )
        }
        item {
            LinearProgressIndicator(
                progress = { if (trip.budget == 0) 0f else (spent.toFloat() / trip.budget).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (remaining < 0) Rose else Orange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        item { SectionLabel("BY CATEGORY") }
        items(CATEGORIES, key = { it }) { c ->
            val total = expenses.filter { it.category == c }.sumOf { it.amount }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c); Text(bdt(total), fontWeight = FontWeight.SemiBold)
            }
        }
        item { Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Add expense")) } }
        item { SectionLabel("EXPENSES") }
        items(expenses, key = { it.id }) { e ->
            InfoCard("${e.category}: ${bdt(e.amount)}", e.note.ifBlank { "No note" })
        }
    }

    if (showDialog) AddExpenseDialog(
        onDismiss = { showDialog = false },
        onSave = { cat, amount, note -> vm.addExpense(cat, amount, note); showDialog = false }
    )
}

@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (String, Int, String) -> Unit) {
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val value = amount.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add expense")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(7) },
                    label = { Text("Amount (BDT)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(60) }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = value != null && value > 0, onClick = { onSave(category, value ?: 0, note) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
