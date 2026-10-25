@file:OptIn(ExperimentalLayoutApi::class)

package com.crowncouncil.orbit.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("More")
        listOf(
            Screen.Trip to "Day-by-day plan for your current trip",
            Screen.Budget to "Track spending against your budget",
            Screen.Explore to "Browse and rank all 113 spots",
            Screen.Live to "Trace your journey on a live map",
            Screen.Photos to "Edit your travel photos instantly",
            Screen.Groups to "Plan together and split costs fairly",
            Screen.Rankings to "See who has visited the most",
            Screen.Submit to "Suggest a spot we are missing",
            Screen.Account to "Sign in, privacy and your data"
        ).forEach { (s, hint) ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(s) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) { Text(tr(s.label), style = MaterialTheme.typography.titleMedium); Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
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
        me = r.body?.optJSONObject("me"); optedIn = r.body?.optBoolean("optedIn") == true
        status = if (rows.isEmpty()) "No travellers on the board yet." else ""
    }
    LaunchedEffect(user) { if (user != null) load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Traveller rankings")
        if (user == null) { InfoCard("Sign in to compete", "Rankings need an account. Create one under More, Account."); return@Column }
        Text("Ranked by GPS-verified check-ins first, then by different spots and districts visited. Verified means the phone reported a position near the spot; it raises the bar but is not proof.", fontSize = 13.sp)
        if (!optedIn) {
            InfoCard("You are not on the board", "Join to appear with your display name and visit counts. You can leave any time.")
            Button(onClick = { scope.launch { user?.consent?.let { c -> vm.updateConsent(c.copy(leaderboard = true)) { scope.launch { load() } } } } }) { Text(tr("Join the leaderboard")) }
        } else me?.let { InfoCard("Your rank: #${it.optInt("rank")}", "${it.optInt("verified")} GPS-verified, ${it.optInt("spots")} spots in ${it.optInt("districts")} districts") }
        if (status.isNotEmpty()) Text(status)
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${r.optInt("rank")}  ${r.optString("displayName")}" + if (r.optBoolean("isMe")) "  (you)" else "", fontWeight = if (r.optBoolean("isMe")) FontWeight.Bold else FontWeight.Normal)
                Text("${r.optInt("verified")} verified | ${r.optInt("spots")} spots | ${r.optInt("districts")} districts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
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
        Title("Add a spot")
        if (user == null) { InfoCard("Sign in to contribute", "Spot suggestions need an account so we can limit spam."); return@Column }
        Text("Suggest a place worth visiting. New spots are reviewed before they appear for everyone.", fontSize = 13.sp)
        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Spot name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Column {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(district.ifEmpty { "Choose district" }) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                districts.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { district = d; menu = false }) }
            }
        }
        SectionLabel("TAGS (1 TO 3)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERESTS.forEach { tg -> FilterChip(selected = tg in tags, onClick = { tags = if (tg in tags) tags - tg else if (tags.size < 3) tags + tg else tags }, label = { Text(tg.cap()) }) }
        }
        OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(5) }, label = { Text("Typical cost per person per day (BDT)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
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
            "The built-in guide already works without it. A language model adds Bangla and looser wording: it only translates your message into a request the guide understands, and the guide still gives all prices and places from the app's own data. " +
                "Get a .litertlm model file (for example a Gemma model) from its official page, check its license, then choose the file here. It stays on this phone and nothing is uploaded. Files are large, so use Wi-Fi to download and keep free storage.",
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
