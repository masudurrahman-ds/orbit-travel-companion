@file:OptIn(ExperimentalLayoutApi::class)

package com.crowncouncil.orbit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.domain.BangladeshMap
import com.crowncouncil.orbit.domain.Badge
import com.crowncouncil.orbit.domain.Badges
import com.crowncouncil.orbit.domain.Story
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.platform.LocalContext

/** Bangladesh with visited districts pinned in gold and districts still to visit left as faint dots. */
@Composable
fun VisitedMapScreen(vm: OrbitViewModel) {
    val dests by vm.destinations.collectAsState()
    val visited by vm.visitedIds.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf<Destination?>(null) }
    val ctx = LocalContext.current
    val visitList by vm.visits.collectAsState()
    val trip by vm.currentTrip.collectAsState()
    val tripRows by vm.rows.collectAsState()
    val tripExpenses by vm.expenses.collectAsState()
    val badges = remember(dests, visitList) { Badges.compute(dests, visitList) }

    val byDistrict = remember(dests) { dests.groupBy { it.district } }
    val centres = remember(byDistrict) { byDistrict.mapValues { (_, l) -> BangladeshMap.project(l.first().longitude, l.first().latitude) } }
    val doneDistricts = byDistrict.filter { (_, l) -> l.any { it.id in visited } }.keys
    val outline = MaterialTheme.colorScheme.surfaceVariant
    val line = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("My Bangladesh")
        Text("${doneDistricts.size} of ${byDistrict.size} districts | ${dests.count { it.id in visited }} of ${dests.size} spots", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Gold pin = visited", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Faint dot = still to visit", color = faint, fontSize = 13.sp)
        }
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(BangladeshMap.ASPECT).pointerInput(centres) {
                detectTapGestures { tap ->
                    val limit = 36.dp.toPx()
                    val hit = centres.minByOrNull { (_, p) -> (Offset(p.first * size.width, p.second * size.height) - tap).getDistance() }
                    if (hit != null && (Offset(hit.value.first * size.width, hit.value.second * size.height) - tap).getDistance() <= limit) selected = hit.key
                }
            }
        ) {
            val path = Path()
            BangladeshMap.outline.forEachIndexed { i, (lon, lat) ->
                val (x, y) = BangladeshMap.project(lon, lat)
                if (i == 0) path.moveTo(x * size.width, y * size.height) else path.lineTo(x * size.width, y * size.height)
            }
            path.close()
            drawPath(path, outline)
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
            centres.forEach { (district, p) ->
                val c = Offset(p.first * size.width, p.second * size.height)
                if (district in doneDistricts) {
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = c)
                    drawCircle(Gold, radius = 7.dp.toPx(), center = c)
                } else {
                    drawCircle(faint.copy(alpha = 0.55f), radius = 3.dp.toPx(), center = c, style = Stroke(width = 1.dp.toPx()))
                }
                if (district == selected) drawCircle(Teal, radius = 13.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Text("Outline is stylised. Tap a marker to see that district's spots.", fontSize = 12.sp, color = faint)

        SectionLabel("BADGES")
        Text("${badges.count { it.earned }} of ${badges.size} earned", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            badges.forEach { BadgeCard(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { shareImage(ctx, PassportCard.render(dests, visited, badges, vm.user.value?.displayName), "orbit-passport.png", "My Bangladesh on ORBIT") }) { Text(tr("Share my passport")) }
            trip?.let { t0 ->
                OutlinedButton(onClick = {
                    val dest = dests.firstOrNull { it.id == t0.destinationId }
                    shareText(ctx, Story.build(t0, dest, tripRows, tripExpenses.sumOf { it.amount }, visitList.firstOrNull { it.destinationId == t0.destinationId }, badges.count { it.earned }), "Share trip story")
                }) { Text(tr("Share trip story")) }
            }
        }

        selected?.let { d ->
            Text(d, style = MaterialTheme.typography.titleLarge)
            byDistrict[d].orEmpty().forEach { spot ->
                val done = spot.id in visited
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(spot.name, fontWeight = FontWeight.SemiBold)
                        Text(spot.tags.split(",").joinToString(", ") { it.cap() }, fontSize = 12.sp, color = faint)
                    }
                    if (done) OutlinedButton(onClick = { vm.unmarkVisited(spot.id) }) { Text(tr("Unmark")) }
                    else Button(onClick = { rating = spot }) { Text(tr("Mark visited")) }
                }
            }
        }
    }

    rating?.let { spot -> RateVisitDialog(spot.name, onDismiss = { rating = null }) { stars, note -> vm.markVisited(spot.id, stars, note); rating = null } }
}

@Composable
fun RateVisitDialog(name: String, onDismiss: () -> Unit, onSave: (Int?, String) -> Unit) {
    var stars by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Visited $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rate it (optional). Ratings feed place quality scores.", fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n -> FilterChip(selected = stars == n, onClick = { stars = if (stars == n) null else n }, label = { Text("$n star${if (n > 1) "s" else ""}") }) }
                }
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("Memory note (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(stars, note) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun BadgeCard(b: Badge) {
    Card(
        Modifier.widthIn(min = 150.dp),
        colors = CardDefaults.cardColors(containerColor = if (b.earned) Gold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr(b.title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (b.earned) Gold else MaterialTheme.colorScheme.onSurface)
            Text(b.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (b.earned) "Earned" else "${b.progress} / ${b.target}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
