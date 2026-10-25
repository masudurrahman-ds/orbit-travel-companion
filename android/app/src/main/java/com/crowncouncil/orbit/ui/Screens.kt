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
import androidx.compose.ui.graphics.Brush
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
    val visited by vm.visitedIds.collectAsState()
    val advisories by vm.advisories.collectAsState()
    val name = vm.user.collectAsState().value?.displayName?.substringBefore(" ") ?: "Traveller"
    val districtsTotal = dests.map { it.district }.distinct().size
    val districtsDone = dests.filter { it.id in visited }.map { it.district }.distinct().size

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("O R B I T", color = MaterialTheme.colorScheme.onBackground, fontSize = 19.sp, letterSpacing = 4.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                    Text("THE ART OF GETTING AWAY", color = Gold, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("✳", color = Gold, fontSize = 23.sp)
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF28291F), Color(0xFF101310), Color(0xFF20251E))))
                .padding(horizontal = 22.dp, vertical = 24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("A WORLD,\nWELL DISCOVERED.", color = Color(0xFFF1EBDD), fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 30.sp, lineHeight = 35.sp)
                    Box(Modifier.width(42.dp).size(height = 1.dp, width = 42.dp).background(Gold))
                    Text("Thoughtful journeys through Bangladesh and beyond.", color = Color(0xFFD0CABB), fontSize = 13.sp, lineHeight = 20.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✦", color = Gold, fontSize = 15.sp)
                        Text("${districtsDone} OF ${districtsTotal} DISTRICTS EXPLORED", color = Color(0xFFD8C08A), fontSize = 10.sp, letterSpacing = 1.2.sp)
                    }
                    LinearProgressIndicator(progress = { if (districtsTotal == 0) 0f else districtsDone.toFloat() / districtsTotal }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = Color.White.copy(alpha = 0.12f))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("GOOD TO SEE YOU, $name", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.6.sp)
                Text("Where to next?", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("Plan a journey", "Explore places", "Ask ORBIT").forEach { label ->
                        Box(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 13.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Text("Use the navigation below to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        if (trip != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("YOUR CURRENT JOURNEY")
                    InfoCard(title = trip.title, body = "$"+"{trip.days} days  ·  $"+"{trip.travellers} traveller(s)  ·  $"+"{monthName(trip.startMonth)}\nBudget $"+"{bdt(trip.budget)}  ·  Spent $"+"{bdt(expenses.sumOf { it.amount })}", container = MaterialTheme.colorScheme.surfaceVariant)
                    rows.take(2).forEach { row -> InfoCard(row.name, "DAY ${row.dayNo}  ·  %02d:00  ·  ${row.category.cap()}".format(row.startHour)) }
                }
            }
        }
        val featured = dests.take(4)
        if (featured.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("PLACES WORTH THE JOURNEY")
                    Text("A few considered suggestions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    featured.forEachIndexed { index, destination ->
                        Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("%02d".format(index + 1), color = Gold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 22.sp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(destination.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("${destination.district}  ·  ${destination.division}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text("↗", color = Gold, fontSize = 19.sp)
                        }
                    }
                }
            }
        }
        val relevant = advisories.sortedByDescending { severityRank(it.severity) }.take(2)
        if (relevant.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionLabel("TRAVEL NOTES")
                    relevant.forEach { advisory -> InfoCard(advisory.title, advisory.text, container = MaterialTheme.colorScheme.surfaceVariant) }
                }
            }
        }
        item {
            Text("ORBIT  ·  PRIVATE BY DESIGN  ·  MADE FOR THE CURIOUS", Modifier.fillMaxWidth().padding(vertical = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.1.sp)
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
        Title("Design your escape")
        InfoCard(
            "A trip that fits you",
            "Choose your pace, budget and interests. ORBIT ranks destinations against your preferences before you commit.",
            container = MaterialTheme.colorScheme.surfaceVariant
        )
        SectionLabel("01  ·  TOUR STYLE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tier.entries.forEach { t ->
                FilterChip(selected = q.tier == t, onClick = { vm.update { it.copy(tier = t) } }, label = { Text(t.label) })
            }
        }
        Text(tierBlurb(q.tier), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Stepper("Days", q.days, 1..7) { v -> vm.update { it.copy(days = v) } }
        Stepper("Travellers", q.travellers, 1..10) { v -> vm.update { it.copy(travellers = v) } }

        SectionLabel("02  ·  YOUR BUDGET: ${bdt(q.budget)}")
        Slider(
            value = q.budget.toFloat(),
            onValueChange = { v -> vm.update { p -> p.copy(budget = (v / 500f).roundToInt() * 500) } },
            valueRange = 5_000f..100_000f
        )

        SectionLabel("03  ·  WHEN ARE YOU GOING?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..12).forEach { m ->
                FilterChip(selected = q.month == m, onClick = { vm.update { it.copy(month = m) } }, label = { Text(monthName(m)) })
            }
        }

        SectionLabel("04  ·  WHAT DO YOU LOVE?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERESTS.forEach { tag ->
                FilterChip(
                    selected = tag in q.interests,
                    onClick = { vm.update { p -> p.copy(interests = if (tag in p.interests) p.interests - tag else p.interests + tag) } },
                    label = { Text(tag.cap()) }
                )
            }
        }
        Button(onClick = onFind, modifier = Modifier.fillMaxWidth()) { Text("Discover my destinations  →") }
        Text(
            "Recommendations are estimates based on the local destination dataset, your preferences and the current scoring rules.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp
        )
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
            Title("Find your place")
            Text("Ranked for your travel style", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text("${q.tier.label} tour  ·  ${q.days} days  ·  ${bdt(q.budget)}  ·  ${monthName(q.month)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(2.dp))
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
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Navy, Teal))), contentAlignment = Alignment.Center) {
                    Text("${r.score.percent}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            Text(d.blurb, fontSize = 14.sp, lineHeight = 20.sp)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(if (r.score.affordable) Green.copy(alpha = 0.09f) else Rose.copy(alpha = 0.09f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ESTIMATED COST", fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(bdt(r.score.estimatedCost), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (r.score.affordable) "BUDGET FIT" else "OVER BUDGET", fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold,
                        color = if (r.score.affordable) Green else Rose)
                    if (visited) Text("Visited", fontSize = 12.sp, color = Teal, fontWeight = FontWeight.SemiBold)
                }
            }
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
