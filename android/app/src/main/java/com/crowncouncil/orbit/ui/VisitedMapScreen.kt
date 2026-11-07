@file:OptIn(ExperimentalLayoutApi::class)

package com.crowncouncil.orbit.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.data.TouristLocation
import com.crowncouncil.orbit.data.TouristLocationCatalog
import com.crowncouncil.orbit.domain.BangladeshMap
import com.crowncouncil.orbit.domain.Badge
import com.crowncouncil.orbit.domain.Badges
import com.crowncouncil.orbit.domain.Story

/**
 * Interactive travel passport map. District markers are derived from the actual destination
 * corpus; tapping any marker or district chip opens that district's destination list.
 * The boundary is intentionally labelled stylised, not an official administrative GIS layer.
 */
@Composable
fun VisitedMapScreen(vm: OrbitViewModel) {
    val dests by vm.destinations.collectAsState()
    val visited by vm.visitedIds.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var selectedTourist by remember { mutableStateOf<TouristLocation?>(null) }
    var search by remember { mutableStateOf("") }
    var mapZoom by remember { mutableStateOf(1f) }
    var mapPan by remember { mutableStateOf(Offset.Zero) }
    var rating by remember { mutableStateOf<Destination?>(null) }
    val ctx = LocalContext.current
    val touristLocations = remember(ctx) { TouristLocationCatalog.load(ctx) }
    val visitList by vm.visits.collectAsState()
    val trip by vm.currentTrip.collectAsState()
    val tripRows by vm.rows.collectAsState()
    val tripExpenses by vm.expenses.collectAsState()
    val badges = remember(dests, visitList) { Badges.compute(dests, visitList) }

    val byDistrict = remember(dests) { dests.groupBy { it.district }.toSortedMap() }
    val centres = remember(byDistrict) {
        byDistrict.mapValues { (_, spots) ->
            val lon = spots.map { it.longitude }.average()
            val lat = spots.map { it.latitude }.average()
            BangladeshMap.project(lon, lat)
        }
    }
    val doneDistricts = byDistrict.filterValues { spots -> spots.any { it.id in visited } }.keys
    val totalVisited = dests.count { it.id in visited }
    val progress = if (dests.isEmpty()) 0f else totalVisited.toFloat() / dests.size
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val shownDistricts = remember(byDistrict, search) {
        if (search.isBlank()) byDistrict.keys.toList()
        else byDistrict.keys.filter { it.contains(search.trim(), ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("THE TRAVEL PASSPORT", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Title("My Bangladesh")
            Text("Every district has a story. Mark the places you've explored and choose where the road takes you next.", color = faint, fontSize = 13.sp, lineHeight = 19.sp)
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("YOUR EXPLORATION", fontSize = 10.sp, letterSpacing = 1.4.sp, color = faint, fontWeight = FontWeight.Bold)
                        Text("${doneDistricts.size} / ${byDistrict.size} districts", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("${(progress * 100).toInt()}%", color = Gold, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Gold,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PassportStat(totalVisited.toString(), "spots visited", Modifier.weight(1f))
                    PassportStat(dests.size.toString(), "destinations", Modifier.weight(1f))
                    PassportStat((dests.size - totalVisited).coerceAtLeast(0).toString(), "to discover", Modifier.weight(1f))
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101C19))
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("BANGLADESH", color = Color(0xFFE9D6A5), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("64 DISTRICTS · ${touristLocations.size} PLACES", color = Color(0xFF9CB2A7), fontSize = 9.sp, letterSpacing = 0.5.sp)
                }
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(BangladeshMap.ASPECT)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = mapZoom
                            scaleY = mapZoom
                            translationX = mapPan.x
                            translationY = mapPan.y
                            clip = true
                        }
                        .pointerInput(mapZoom, mapPan, centres, touristLocations) {
                            detectTapGestures { tap ->
                                val mapTap = Offset((tap.x - mapPan.x) / mapZoom, (tap.y - mapPan.y) / mapZoom)
                                val limit = 40.dp.toPx() / mapZoom
                                val touristHit = touristLocations.minByOrNull { place ->
                                    val point = BangladeshMap.project(place.longitude, place.latitude)
                                    (Offset(point.first * size.width, point.second * size.height) - mapTap).getDistance()
                                }
                                val touristDistance = touristHit?.let { place ->
                                    val point = BangladeshMap.project(place.longitude, place.latitude)
                                    (Offset(point.first * size.width, point.second * size.height) - mapTap).getDistance()
                                } ?: Float.MAX_VALUE
                                val districtHit = centres.minByOrNull { (_, point) ->
                                    (Offset(point.first * size.width, point.second * size.height) - mapTap).getDistance()
                                }
                                val districtDistance = districtHit?.let { (_, point) ->
                                    (Offset(point.first * size.width, point.second * size.height) - mapTap).getDistance()
                                } ?: Float.MAX_VALUE
                                if (touristDistance <= limit && touristDistance <= districtDistance) {
                                    selectedTourist = touristHit
                                    selected = null
                                } else if (districtHit != null && districtDistance <= limit) {
                                    selected = districtHit.key
                                    selectedTourist = null
                                }
                            }
                        }
                        .pointerInput(mapZoom, mapPan) {
                            detectTransformGestures { centroid, pan, zoomChange, _ ->
                                val nextZoom = (mapZoom * zoomChange).coerceIn(1f, 5f)
                                val zoomRatio = nextZoom / mapZoom
                                val proposed = mapPan + pan + (centroid - Offset.Zero) * (1f - zoomRatio)
                                // Keep the transformed map covering the viewport: users can pan
                                // freely, but cannot lose the country completely off-screen.
                                val maxX = size.width * (1f - nextZoom)
                                val maxY = size.height * (1f - nextZoom)
                                mapPan = Offset(
                                    proposed.x.coerceIn(maxX, 0f),
                                    proposed.y.coerceIn(maxY, 0f)
                                )
                                mapZoom = nextZoom
                            }
                        }
                ) {
                    // Quiet cartographic grid.
                    val grid = Color(0xFF54756A).copy(alpha = 0.20f)
                    for (i in 1..5) {
                        val x = size.width * i / 6f
                        drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                    for (i in 1..5) {
                        val y = size.height * i / 6f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    val path = Path()
                    BangladeshMap.outline.forEachIndexed { index, (lon, lat) ->
                        val (x, y) = BangladeshMap.project(lon, lat)
                        if (index == 0) path.moveTo(x * size.width, y * size.height)
                        else path.lineTo(x * size.width, y * size.height)
                    }
                    path.close()
                    drawPath(path, Brush.linearGradient(listOf(Color(0xFF426A58), Color(0xFF213F35), Color(0xFF172C27))))
                    drawPath(path, Color(0xFFC6A568), style = Stroke(width = 2.dp.toPx()))
                    // Exact tourist placemarks from the offline Travel Mate import.
                    touristLocations.forEach { place ->
                        val point = BangladeshMap.project(place.longitude, place.latitude)
                        val c = Offset(point.first * size.width, point.second * size.height)
                        val markerScale = mapZoom.coerceAtLeast(1f)
                        drawCircle(Color(0xFF07110E), radius = (5.5f / markerScale).dp.toPx(), center = c)
                        drawCircle(Color(0xFF73D6C2), radius = (3.2f / markerScale).dp.toPx(), center = c)
                        if (selectedTourist?.id == place.id) {
                            drawCircle(Color(0xFFFFD77F), radius = (8f / markerScale).dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
                        }
                    }
                    centres.forEach { (district, point) ->
                        val c = Offset(point.first * size.width, point.second * size.height)
                        if (district in doneDistricts) {
                            drawCircle(Color(0xFFFFE9B8).copy(alpha = 0.20f), radius = 13.dp.toPx(), center = c)
                            drawCircle(Color(0xFFFFD77F), radius = 5.dp.toPx(), center = c)
                        } else {
                            drawCircle(Color(0xFFB8C9BF).copy(alpha = 0.75f), radius = 3.dp.toPx(), center = c)
                        }
                        if (district == selected) {
                            drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
                            drawCircle(Color(0xFFFFD77F), radius = 3.dp.toPx(), center = c)
                        }
                    }
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(242, 232, 208)
                        textSize = 9.dp.toPx()
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                        setShadowLayer(3.dp.toPx(), 0f, 1.dp.toPx(), android.graphics.Color.BLACK)
                    }
                    listOf("Dhaka", "Chattogram", "Sylhet", "Cox's Bazar", "Rajshahi", "Khulna", "Rangamati", "Barishal").forEach { label ->
                        val entry = centres.entries.firstOrNull { it.key.equals(label, ignoreCase = true) } ?: return@forEach
                        drawContext.canvas.nativeCanvas.drawText(
                            entry.key, entry.value.first * size.width,
                            entry.value.second * size.height - 10.dp.toPx(), labelPaint
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pinch or drag to explore", color = Color(0xFFB8C9BF), fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { mapZoom = (mapZoom / 1.25f).coerceAtLeast(1f); if (mapZoom == 1f) mapPan = Offset.Zero }) { Text("−", color = Color(0xFFE9D6A5), fontSize = 20.sp) }
                        Text("${(mapZoom * 100).toInt()}%", color = Color(0xFFE9D6A5), fontSize = 11.sp)
                        TextButton(onClick = { mapZoom = (mapZoom * 1.25f).coerceAtMost(5f) }) { Text("+", color = Color(0xFFE9D6A5), fontSize = 20.sp) }
                        TextButton(onClick = { mapZoom = 1f; mapPan = Offset.Zero }) { Text("Reset", color = Color(0xFFE9D6A5), fontSize = 11.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("●", color = Color(0xFFFFD77F), fontSize = 12.sp)
                        Text("Visited", color = Color(0xFFE8E5DA), fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("●", color = Color(0xFF73D6C2), fontSize = 12.sp)
                        Text("Tourist place", color = Color(0xFFE8E5DA), fontSize = 11.sp)
                    }
                    Text("Tap a marker", color = Color(0xFFB8C9BF), fontSize = 10.sp)
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Find a district or tourist place") },
            placeholder = { Text("e.g. Jaflong, Tara Masjid, Sylhet") }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            shownDistricts.forEach { district ->
                val done = district in doneDistricts
                FilterChip(
                    selected = selected == district,
                    onClick = { selected = if (selected == district) null else district },
                    label = { Text((if (done) "✓ " else "") + district, fontSize = 12.sp) }
                )
            }
        }

        selectedTourist?.let { place ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("FEATURED PLACE", color = Gold, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { selectedTourist = null }) { Text("Close") }
                    }
                    Text(place.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(place.upazila, place.district, place.sourceRegion.takeIf { it.isNotBlank() }).distinct().joinToString(" · ").ifBlank { "Bangladesh" },
                        color = faint, fontSize = 12.sp
                    )
                    Text("${"%.5f".format(place.latitude)}, ${"%.5f".format(place.longitude)}", color = faint, fontSize = 11.sp)
                    Text("Offline map point · Source: Travel Mate KML", color = faint, fontSize = 10.sp)
                }
            }
        }

        selected?.let { district ->
            val spots = byDistrict[district].orEmpty()
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("DISTRICT GUIDE", color = Gold, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Text(district, style = MaterialTheme.typography.titleLarge)
                            Text("${spots.count { it.id in visited }} of ${spots.size} spots explored", color = faint, fontSize = 12.sp)
                        }
                        TextButton(onClick = { selected = null }) { Text("Close") }
                    }
                    spots.forEach { spot ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(spot.name, fontWeight = FontWeight.SemiBold)
                                Text(spot.tags.split(",").joinToString(" · ") { it.cap() }, fontSize = 11.sp, color = faint)
                            }
                            if (spot.id in visited) {
                                OutlinedButton(onClick = { vm.unmarkVisited(spot.id) }) { Text("Visited") }
                            } else {
                                Button(onClick = { rating = spot }) { Text("Explore") }
                            }
                        }
                    }
                }
            }
        }

        SectionLabel("TRAVELLER MILESTONES")
        Text("${badges.count { it.earned }} of ${badges.size} earned", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            badges.forEach { BadgeCard(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                shareImage(ctx, PassportCard.render(dests, visited, badges, vm.user.value?.displayName), "orbit-passport.png", "My Bangladesh on ORBIT")
            }, modifier = Modifier.weight(1f)) { Text("Share passport") }
            trip?.let { current ->
                OutlinedButton(onClick = {
                    val dest = dests.firstOrNull { it.id == current.destinationId }
                    shareText(ctx, Story.build(current, dest, tripRows, tripExpenses.sumOf { it.amount }, visitList.firstOrNull { it.destinationId == current.destinationId }, badges.count { it.earned }), "Share trip story")
                }) { Text("Trip story") }
            }
        }
        Text("Tourist markers use the bundled offline Travel Mate placemarks. District outline remains a stylised illustration, not an official boundary or navigation map. Confirm source reuse permission before public release.", color = faint, fontSize = 11.sp, lineHeight = 16.sp)
    }

    rating?.let { spot ->
        RateVisitDialog(spot.name, onDismiss = { rating = null }) { stars, note ->
            vm.markVisited(spot.id, stars, note)
            rating = null
        }
    }
}

@Composable
private fun PassportStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 18.sp, color = Gold, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
                    (1..5).forEach { n ->
                        FilterChip(selected = stars == n, onClick = { stars = if (stars == n) null else n }, label = { Text("$n star${if (n == 1) "" else "s"}") })
                    }
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
