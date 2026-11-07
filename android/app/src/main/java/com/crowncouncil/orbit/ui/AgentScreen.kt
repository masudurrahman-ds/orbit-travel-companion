package com.crowncouncil.orbit.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentScreen(vm: OrbitViewModel, onPlanSaved: () -> Unit) {
    val messages by vm.chat.collectAsState()
    val busy by vm.chatBusy.collectAsState()
    val generatedImage by vm.generatedImageB64.collectAsState()
    val imageBusy by vm.imageBusy.collectAsState()
    val imageError by vm.imageError.collectAsState()
    val q by vm.query.collectAsState()
    val list = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var imagePrompt by remember { mutableStateOf("") }
    var imageMode by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(listOf(Color(0xFF171B16), Color(0xFF25291F), Color(0xFF171B16)))
            ).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("O R B I T  /  INTELLIGENCE", color = Color(0xFFD8BC7A), fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Text("Your journey, considered.", color = Color(0xFFF1EBDD), fontFamily = FontFamily.Serif, fontSize = 23.sp)
                }
                Surface(shape = RoundedCornerShape(30.dp), color = Color.White.copy(alpha = .08f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (vm.llmReady.value) Color(0xFF9DC49A) else Color(0xFFD8BC7A)))
                        Text(if (vm.llmReady.value) "ON-DEVICE AI" else "LOCAL BRAIN", color = Color(0xFFE7DEC9), fontSize = 9.sp, letterSpacing = .7.sp)
                    }
                }
            }
            Text("A thoughtful travel companion for Bangladesh.", color = Color(0xFFBDB9AC), fontSize = 12.sp)
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 15.dp),
            state = list,
            contentPadding = PaddingValues(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (messages.size <= 1) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("THE CONCIERGE", color = Gold, fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                        Text("Where would you\nlike to disappear?", color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Serif, fontSize = 31.sp, lineHeight = 36.sp)
                        Text("Ask for a considered itinerary, compare destinations, or discover a quieter side of the country.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Plan a 2-day trip from Dhaka under BDT ${q.budget}.",
                            "Find peaceful nature escapes in Bangladesh.",
                            "Compare Sajek Valley and Bandarban.",
                            "What should I know before visiting Cox's Bazar?"
                        ).forEachIndexed { index, prompt ->
                            Surface(
                                onClick = { vm.ask(prompt) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .25f))
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("0${index + 1}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(prompt, Modifier.weight(1f).padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 18.sp)
                                    Text("↗", color = Gold, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(messages) { _, message ->
                if (message.fromUser) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                        ) {
                            Text(message.text, Modifier.widthIn(max = 310.dp).padding(horizontal = 15.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 2.dp).size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF272B22)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Face, null, tint = Color(0xFFD8BC7A), modifier = Modifier.size(17.dp))
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ORBIT  ·  TRAVEL INTELLIGENCE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 3.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(message.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 22.sp)
                                    message.plan?.let { plan -> Button(onClick = { vm.savePlan(plan, onPlanSaved) }) { Text("Save itinerary  →") } }
                                    message.brainPlan?.let { plan -> Button(onClick = { vm.saveBrainPlan(plan, null, onPlanSaved) }) { Text("Save itinerary  →") } }
                                }
                            }
                        }
                    }
                }
            }

            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
                    Text("Considering your options…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            generatedImage?.let { encoded ->
                item {
                    val bitmap = remember(encoded) {
                        runCatching {
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                    bitmap?.let {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("YOUR TRAVEL INSPIRATION", color = Gold, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                            Image(it, "AI generated travel inspiration", Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }

        imageError?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

        if (imageMode) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CREATE A TRAVEL IMAGE", color = Gold, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(imagePrompt, { imagePrompt = it.take(1000) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Describe a scene, place or travel poster…") }, maxLines = 2)
                Button(onClick = { vm.generateTravelImage(imagePrompt) }, enabled = imagePrompt.isNotBlank() && !imageBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (imageBusy) "Creating image…" else "Generate image")
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AssistChip(onClick = { vm.ask("Plan a budget-friendly trip within BDT ${q.budget}.") }, label = { Text("Budget escape") })
                    AssistChip(onClick = { vm.ask("Suggest a destination I have not visited.") }, label = { Text("Surprise me") })
                    AssistChip(onClick = { imageMode = !imageMode }, label = { Icon(Icons.Default.Image, null, Modifier.size(15.dp)); Text("Image") })
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(2000) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask ORBIT anything…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(18.dp)
                    )
                    FilledIconButton(
                        onClick = { vm.ask(input); input = "" },
                        enabled = input.isNotBlank() && !busy,
                        modifier = Modifier.size(50.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Gold, contentColor = Color(0xFF171811))
                    ) { Icon(Icons.Default.Send, contentDescription = "Send message") }
                }
                Text("Offline-first travel knowledge · Verify live fares, weather and local conditions before departure.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
    }
}
