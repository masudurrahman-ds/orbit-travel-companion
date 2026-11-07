package com.crowncouncil.orbit.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private data class HeroPlace(val name: String, val region: String, val line: String, val image: String, val number: String)

private val heroPlaces = listOf(
    HeroPlace("Sajek Valley", "Rangamati · Chattogram", "Above the clouds, closer to yourself", "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=1400&q=90", "01"),
    HeroPlace("Cox's Bazar", "Cox's Bazar · Chattogram", "Follow the horizon a little further", "https://images.unsplash.com/photo-1500375592092-40eb2168fd21?w=1400&q=90", "02"),
    HeroPlace("Jaflong", "Sylhet", "Where the river finds the hills", "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1400&q=90", "03"),
    HeroPlace("Ratargul", "Sylhet", "A quieter kind of adventure", "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1400&q=90", "04")
)

private val OrbitGold = Color(0xFFD4B879)
private val OrbitInk = Color(0xFF10120F)

@Composable
fun CinematicHomeScreen(vm: OrbitViewModel, onPlan: () -> Unit, onExplore: () -> Unit, onGuide: () -> Unit, onMap: () -> Unit) {
    val visited by vm.visitedIds.collectAsState()
    val destinations by vm.destinations.collectAsState()
    val user by vm.user.collectAsState()
    val appearance = LocalOrbitThemePreference.current
    var index by remember { mutableIntStateOf(0) }
    var showAllPlaces by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(6500)
            index = (index + 1) % heroPlaces.size
        }
    }

    val motion = rememberInfiniteTransition(label = "journey-motion")
    val vehicleOffset by motion.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "vehicle-offset"
    )
    val districtCount = destinations.filter { it.id in visited }.map { it.district }.distinct().size
    val travellerName = user?.displayName?.substringBefore(" ") ?: "Traveller"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 19.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("O R B I T", color = MaterialTheme.colorScheme.onBackground, fontSize = 23.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                    Text("THE ART OF GOING FURTHER", color = MaterialTheme.colorScheme.secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                }
                Surface(
                    onClick = { appearance.setMode(if (appearance.mode == "dark") "light" else "dark") },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                ) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Text(if (appearance.mode == "dark") "☼" else "◐", fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(490.dp).clip(RoundedCornerShape(7.dp))) {
                Crossfade(targetState = index, label = "destination-spotlight") { selected ->
                    AsyncImage(
                        model = heroPlaces[selected].image,
                        contentDescription = heroPlaces[selected].name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.04f),
                            0.35f to Color.Black.copy(alpha = 0.12f),
                            1f to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
                Row(Modifier.align(Alignment.TopStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(OrbitGold))
                    Text("THE BANGLADESH COLLECTION", color = Color.White.copy(alpha = 0.92f), fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 23.dp, end = 23.dp, bottom = 25.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DESTINATION \${heroPlaces[index].number}  /  04", color = OrbitGold, fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Text(heroPlaces[index].name, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 39.sp, lineHeight = 43.sp)
                    Text(heroPlaces[index].line, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(heroPlaces[index].region, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                            Text("BANGLADESH", color = Color.White.copy(alpha = 0.55f), fontSize = 8.sp, letterSpacing = 1.5.sp)
                        }
                        Button(
                            onClick = onExplore,
                            shape = RoundedCornerShape(3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrbitGold, contentColor = OrbitInk),
                            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp)
                        ) {
                            Text("EXPLORE", fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Search, null, Modifier.padding(start = 7.dp).size(15.dp))
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    heroPlaces.indices.forEach { item ->
                        Box(
                            Modifier.width(if (item == index) 22.dp else 6.dp).height(5.dp).clip(CircleShape)
                                .background(if (item == index) OrbitGold else Color.White.copy(alpha = 0.55f))
                                .clickable { index = item }
                        )
                    }
                }
                // Decorative train motif: an atmospheric animation, not live vehicle tracking.
                Column(Modifier.align(Alignment.CenterEnd).offset(x = vehicleOffset.dp).padding(top = 42.dp, end = 12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("A JOURNEY THROUGH BANGLADESH", color = Color.White.copy(alpha = 0.78f), fontSize = 7.sp, letterSpacing = 1.sp)
                    Row(
                        Modifier.clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.34f)).padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        repeat(3) {
                            Box(Modifier.width(22.dp).height(13.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.78f)))
                        }
                        Box(Modifier.width(5.dp).height(13.dp).background(OrbitGold))
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                LuxuryAction("PLAN", "Build your escape", Icons.Default.Create, onPlan, Modifier.weight(1f))
                LuxuryAction("ORBIT BRAIN", "Ask your guide", Icons.Default.Face, onGuide, Modifier.weight(1f))
                LuxuryAction("ATLAS", "Explore the map", Icons.Default.Place, onMap, Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 21.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("YOUR NEXT CHAPTER", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                    Text("Welcome, $travellerName.", color = MaterialTheme.colorScheme.onBackground, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 24.sp)
                    Text("The country is yours to discover.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("$districtCount", color = MaterialTheme.colorScheme.primary, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 30.sp)
                    Text("OF 64 DISTRICTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CURATED FOR YOUR MOOD", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                        Text("Find your kind of escape", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Text("VIEW ALL  ↗", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, letterSpacing = 0.8.sp, modifier = Modifier.clickable(onClick = onExplore))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Coast", "Highlands", "Wild", "Heritage").forEach { category ->
                        Surface(
                            Modifier.weight(1f).clickable(onClick = onExplore),
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f))
                        ) {
                            Box(Modifier.padding(vertical = 12.dp, horizontal = 3.dp), contentAlignment = Alignment.Center) {
                                Text(category.uppercase(), fontSize = 8.sp, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("THE EDITOR'S SELECTION", color = MaterialTheme.colorScheme.secondary, fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                        Text("Places worth the journey", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(if (showAllPlaces) "LESS  ↑" else "ALL PLACES  ↗", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, letterSpacing = 0.8.sp, modifier = Modifier.clickable { showAllPlaces = !showAllPlaces })
                }
                val selection = if (showAllPlaces) heroPlaces else heroPlaces.filterIndexed { i, _ -> i != index }.take(3)
                selection.forEach { place ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable { index = heroPlaces.indexOf(place); onExplore() }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(model = place.image, contentDescription = place.name, modifier = Modifier.size(86.dp, 76.dp).clip(RoundedCornerShape(3.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(place.region.uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 8.sp, letterSpacing = 1.sp)
                            Text(place.name, color = MaterialTheme.colorScheme.onSurface, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 18.sp)
                            Text(place.line, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 2)
                        }
                        Text("↗", color = MaterialTheme.colorScheme.primary, fontSize = 19.sp)
                    }
                }
            }
        }

        item {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 19.dp),
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("YOUR BANGLADESH PASSPORT", color = MaterialTheme.colorScheme.secondary, fontSize = 8.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                        Text("Every district has a story.", color = MaterialTheme.colorScheme.onSurface, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 17.sp)
                        Text("$districtCount of 64 districts explored", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    CircularProgressIndicator(
                        progress = { (districtCount / 64f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(43.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun LuxuryAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.26f))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
        }
    }
}
