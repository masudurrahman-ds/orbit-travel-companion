package com.crowncouncil.orbit.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ORBIT AI", fontSize = 11.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Your Bangladesh travel companion", style = MaterialTheme.typography.titleMedium)
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.llmReady.value) Color(0xFF4C9A70) else MaterialTheme.colorScheme.primary))
                    Text(if (vm.llmReady.value) "On-device model" else "Local travel brain", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), state = list, contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (messages.size <= 1) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(29.dp))
                        }
                        Text("Where should we take you?", style = MaterialTheme.typography.headlineMedium)
                        Text("Tell ORBIT what you love, how much you want to spend, or what you need to know.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Plan a 2-day trip from Dhaka under BDT ${q.budget}.",
                            "Suggest peaceful nature destinations in Bangladesh.",
                            "Compare Sajek Valley and Bandarban.",
                            "What should I know before visiting Cox's Bazar?"
                        ).forEach { prompt ->
                            OutlinedButton(onClick = { vm.ask(prompt) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(14.dp)) {
                                Text(prompt, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            itemsIndexed(messages) { _, message ->
                if (message.fromUser) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp)) {
                            Text(message.text, Modifier.widthIn(max = 310.dp).padding(horizontal = 15.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 3.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                        Column(Modifier.padding(start = 9.dp).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("ORBIT", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(message.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 22.sp)
                            message.plan?.let { plan -> Button(onClick = { vm.savePlan(plan, onPlanSaved) }) { Text("Save itinerary") } }
                            message.brainPlan?.let { plan -> Button(onClick = { vm.saveBrainPlan(plan, null, onPlanSaved) }) { Text("Save itinerary") } }
                        }
                    }
                }
            }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Thinking through your journey…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            generatedImage?.let { encoded ->
                item {
                    val bitmap = remember(encoded) {
                        runCatching { val bytes = Base64.decode(encoded, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
                    }
                    bitmap?.let { androidx.compose.foundation.Image(it, "AI generated travel inspiration", Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop) }
                }
            }
        }
        imageError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (imageMode) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Create a travel image", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(imagePrompt, { imagePrompt = it.take(1000) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Describe a Bangladesh travel scene…") }, maxLines = 2)
                Button(onClick = { vm.generateTravelImage(imagePrompt) }, enabled = imagePrompt.isNotBlank() && !imageBusy, modifier = Modifier.fillMaxWidth()) { Text(if (imageBusy) "Generating…" else "Generate image") }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { vm.ask("Plan a budget-friendly trip within BDT ${q.budget}.") }, label = { Text("Budget trip") })
                    AssistChip(onClick = { vm.ask("Suggest a destination I have not visited.") }, label = { Text("Surprise me") })
                    AssistChip(onClick = { imageMode = !imageMode }, label = { Icon(Icons.Default.Image, null, Modifier.size(16.dp)); Text("Image") })
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(input, { input = it.take(2000) }, modifier = Modifier.weight(1f), placeholder = { Text("Message ORBIT…") }, maxLines = 5, shape = RoundedCornerShape(24.dp))
                    FilledIconButton(onClick = { vm.ask(input); input = "" }, enabled = input.isNotBlank() && !busy, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Send, contentDescription = "Send message") }
                }
                Text("Local travel assistant · Responses are based on bundled travel data. Verify live fares and conditions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}