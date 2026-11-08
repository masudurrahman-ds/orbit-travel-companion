@file:OptIn(ExperimentalLayoutApi::class)

package com.crowncouncil.orbit.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowncouncil.orbit.net.Consent
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------- More menu
@Composable
fun MoreScreen(onOpen: (Screen) -> Unit) {
    val appearance = LocalOrbitThemePreference.current
    val entries = listOf(
        Screen.Trip to ("Your itinerary" to "A day-by-day view of the journey you have saved"),
        Screen.Budget to ("Journey budget" to "Keep spending and estimates in perspective"),
        Screen.Explore to ("Destination atlas" to "Browse and rank Bangladesh's destinations"),
        Screen.Live to ("Live journey map" to "View your route and nearby places"),
        Screen.Photos to ("Travel photo studio" to "Edit and prepare your travel photographs"),
        Screen.Groups to ("Travel together" to "Coordinate a group and split shared costs"),
        Screen.Rankings to ("Traveller index" to "Explore community travel milestones"),
        Screen.Submit to ("Suggest a destination" to "Help the ORBIT atlas grow"),
        Screen.Account to ("Account & privacy" to "Manage your data, permissions and guide")
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SectionLabel("THE ORBIT COLLECTION")
            Text("Your travel\ntoolkit.", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, lineHeight = 36.sp)
            Text("Everything you need to shape, remember and share a journey.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .24f))
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("APPEARANCE")
                Text("A setting for every light.", style = MaterialTheme.typography.titleLarge)
                Text("Choose how ORBIT looks on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        FilterChip(selected = appearance.mode == value, onClick = { appearance.setMode(value) }, label = { Text(label) })
                    }
                }
            }
        }
        SectionLabel("YOUR TOOLS  ·  ${entries.size} TOOLS")
        entries.forEachIndexed { index, (screen, detail) ->
            Card(
                Modifier.fillMaxWidth().clickable { onOpen(screen) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF292B22), Color(0xFF111511)))),
                        contentAlignment = Alignment.Center
                    ) { Text("%02d".format(index + 1), color = Color(0xFFD8BC7A), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(detail.first, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(detail.second, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("↗", color = Gold, fontSize = 19.sp)
                }
            }
        }
        Text("ORBIT  ·  MADE FOR THE CURIOUS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.3.sp)
    }
}

// ---------------------------------------------------------------- Rankings
@Composable
fun RankingScreen(vm: OrbitViewModel) {
    val user by vm.user.collectAsState()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var me by remember { mutableStateOf<JSONObject?>(null) }
    var optedIn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading...") }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        val r = vm.api.get("/api/leaderboard")
        if (!r.ok) { status = if (r.status == 0) "Cannot reach the server." else "Could not load rankings."; return }
        val arr = r.body?.optJSONArray("top") ?: JSONArray()
        rows = (0 until arr.length()).map { arr.getJSONObject(it) }
        me = r.body?.optJSONObject("me")
        optedIn = r.body?.optBoolean("optedIn") == true
        status = if (rows.isEmpty()) "No travellers on the board yet." else ""
    }
    LaunchedEffect(user) { if (user != null) load() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SectionLabel("THE ORBIT COMMUNITY")
            Text("The traveller\nindex.", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, lineHeight = 36.sp)
            Text("A record of the places people have explored, measured by check-ins and discovery.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (user == null) {
            InfoCard("Your journey starts here", "Sign in from Account to join the community traveller index.")
            return@Column
        }
        if (!optedIn) {
            InfoCard("Your name stays private", "Join only if you want your display name and visit counts to appear on the public board. You can opt out at any time.")
            Button(onClick = { scope.launch { user?.consent?.let { consent -> vm.updateConsent(consent.copy(leaderboard = true)) { scope.launch { load() } } } } }) {
                Text("Join the traveller index  →")
            }
        } else {
            me?.let {
                Box(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF292B22), Color(0xFF111511))))
                        .padding(17.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("YOUR CURRENT POSITION", color = Color(0xFFD8BC7A), fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                        Text("#${it.optInt("rank")}", color = Color(0xFFF1EBDD), fontSize = 34.sp, fontWeight = FontWeight.Light, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Text("${it.optInt("verified")} GPS-reported check-ins  ·  ${it.optInt("spots")} spots  ·  ${it.optInt("districts")} districts", color = Color(0xFFCBC4B3), fontSize = 11.sp)
                    }
                }
            }
        }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        if (rows.isNotEmpty()) {
            SectionLabel("COMMUNITY LEADERS")
            rows.forEach { row ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("%02d".format(row.optInt("rank")), color = Gold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 21.sp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.optString("displayName") + if (row.optBoolean("isMe")) "  ·  YOU" else "", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            Text("${row.optInt("verified")} verified  ·  ${row.optInt("spots")} spots  ·  ${row.optInt("districts")} districts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Text("GPS-reported proximity raises the check-in confidence; it is not independent proof that a visit occurred.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

// ---------------------------------------------------------------- Submit a spot
@Composable
fun SubmitSpotScreen(vm: OrbitViewModel) {
    val user by vm.user.collectAsState()
    val dests by vm.destinations.collectAsState()
    val districts = remember(dests) { dests.map { it.district }.distinct().sorted() }
    var name by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var cost by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("CONTRIBUTE TO THE ATLAS")
        Text("Every place has\na story.", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, lineHeight = 36.sp)
        Text("Know somewhere worth discovering? Send it to the ORBIT editorial queue for review.", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (user == null) { InfoCard("Contribute when you are ready", "Sign in to suggest a place. Every submission is reviewed before appearing in the atlas."); return@Column }
        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Spot name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Column {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(district.ifEmpty { "Choose district" }) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                districts.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { district = d; menu = false }) }
            }
        }
        SectionLabel("THE PLACE  /  01")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERESTS.forEach { tg -> FilterChip(selected = tg in tags, onClick = { tags = if (tg in tags) tags - tg else if (tags.size < 3) tags + tg else tags }, label = { Text(tg.cap()) }) }
        }
        SectionLabel("TRAVEL CHARACTER  /  02")
        OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(5) }, label = { Text("Typical cost per person per day (BDT)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
        SectionLabel("A NOTE FOR FUTURE TRAVELLERS  /  03")
        OutlinedTextField(desc, { desc = it.take(300) }, label = { Text("What makes it special?") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
        result?.let { Text(it, fontWeight = FontWeight.SemiBold) }
        Button(
            enabled = !busy && name.trim().length >= 3 && district.isNotEmpty() && tags.isNotEmpty() && cost.isNotEmpty(),
            onClick = {
                busy = true; result = null
                scope.launch {
                    val r = vm.api.post("/api/destinations/submit", JSONObject().put("name", name.trim()).put("district", district)
                        .put("tags", JSONArray(tags.toList())).put("costPerDayBdt", cost.toInt()).put("description", desc.trim()))
                    busy = false
                    result = if (r.ok) "Thanks. Your spot will appear after review." else r.message ?: if (r.error == "duplicate") "That spot is already listed." else if (r.status == 0) "Cannot reach the server." else "Could not submit. Check the fields."
                    if (r.ok) { name = ""; desc = ""; tags = emptySet(); cost = "" }
                }
            }, modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Sending..." else "Submit for review") }
    }
}

// ---------------------------------------------------------------- Account & privacy
@Composable
fun AccountScreen(vm: OrbitViewModel) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val user by vm.user.collectAsState()
    val scope = rememberCoroutineScope()
    val notes by vm.notes.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Account")
        val u = user
        if (u == null) {
            InfoCard("You are using ORBIT as a guest", "Everything stays on this phone, including the guide. Sign in to sync visits, join rankings and submit spots.")
            Button(onClick = { vm.signOut() }) { Text("Sign in or create account") }
        } else {
            InfoCard(u.displayName, u.identifier)
            SectionLabel("DATA AND PERMISSIONS")
            if (com.crowncouncil.orbit.BuildConfig.USE_CLOUD_AI) {
                ConsentSwitch("Cloud AI guide", "Allow chat messages, visit history and saved notes to be processed by the optional cloud AI provider.", u.consent.ai) { vm.updateConsent(u.consent.copy(ai = it)) }
                ConsentSwitch("Share location with the cloud guide", "Sent only when you ask the guide for trip help. Never stored on our server.", u.consent.location) { vm.updateConsent(u.consent.copy(location = it)) }
            }
            ConsentSwitch("Public leaderboard", "Show your display name and visit counts to other travellers.", u.consent.leaderboard) { vm.updateConsent(u.consent.copy(leaderboard = it)) }

            SectionLabel("YOUR DATA")
            OutlinedButton(onClick = {
                scope.launch {
                    val r = vm.api.get("/api/me/export")
                    if (r.ok) ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, r.body.toString()) }, "Save my ORBIT data"))
                    else info = "Could not download your data."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("Download my data")) }
            OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) { Text(tr("Sign out")) }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete my account", color = Rose) }
        }
        SectionLabel("WHAT THE GUIDE REMEMBERS (ON THIS PHONE)")
        if (notes.isEmpty()) Text("Nothing saved yet. Tell the guide things like \"remember that I prefer quiet beaches\".", fontSize = 13.sp)
        notes.forEach { n ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(n.text, Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { vm.deleteNote(n.id) }) { Text(tr("Delete")) }
            }
        }
        if (notes.isNotEmpty()) OutlinedButton(onClick = { vm.forgetAllNotes() }) { Text("Forget everything") }
        SectionLabel("LANGUAGE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = I18n.lang == "en", onClick = { I18n.set(ctx, "en") }, label = { Text("English") })
            FilterChip(selected = I18n.lang == "bn", onClick = { I18n.set(ctx, "bn") }, label = { Text("বাংলা") })
        }
        Text("The guide understands Bangla and Banglish questions. Its answers are in English for now. Menus switch language.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("ON-DEVICE LANGUAGE MODEL (OPTIONAL)")
        val llmReady by vm.llmReady.collectAsState()
        val llmOn by vm.llmEnabled.collectAsState()
        var importing by remember { mutableStateOf(false) }
        var llmInfo by remember { mutableStateOf<String?>(null) }
        val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked != null) { importing = true; llmInfo = null; vm.importModel(picked) { msg -> importing = false; llmInfo = msg } }
        }
        Text(
            "The built-in guide works offline without a model. Add a compatible local language model for generative answers and better handling of open-ended requests. ORBIT's planner remains responsible for destinations, budgets and itinerary calculations. " +
                "Get a LiteRT-LM-compatible .litertlm model (for example Gemma 3 1B IT) from a trusted model publisher, review its license and hardware requirements, then choose it here. The file stays in private app storage. Model files are large and performance depends on your device.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (llmReady) {
            ConsentSwitch("Use it for Bangla and unfamiliar messages", "Model on this phone: ${vm.llmSizeMb} MB. The first use can take several seconds to load.", llmOn) { vm.setLlmEnabled(it) }
            OutlinedButton(onClick = { vm.removeModel() }) { Text("Remove model") }
        } else {
            OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = !importing) { Text(if (importing) "Copying model..." else "Choose model file") }
        }
        llmInfo?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        SectionLabel("POLICIES")
        Row { TextButton(onClick = { uri.openUri("${vm.baseUrl}/privacy") }) { Text(tr("Privacy Policy")) }; TextButton(onClick = { uri.openUri("${vm.baseUrl}/terms") }) { Text(tr("Terms of Use")) } }
        info?.let { Text(it, color = Rose, fontSize = 13.sp) }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete account?") },
        text = { Text("This permanently removes your account, visits and saved guide notes from our server and clears visits on this phone. It cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAccount { r -> if (!r.ok) info = "Could not delete the account. Try again." } }) { Text("Delete", color = Rose) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } }
    )
}

@Composable
private fun ConsentSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
