@file:OptIn(ExperimentalLayoutApi::class)

package com.crowncouncil.orbit.ui

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/**
 * Group trips: a shared code, a shared plan and shared expenses with fair splitting.
 * The app only calculates who owes whom. It does not move money, and nothing here connects to a payment service.
 */
@Composable
fun GroupsScreen(vm: OrbitViewModel) {
    val user by vm.user.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var newName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    suspend fun loadList() { groups = vm.api.get("/api/groups").body?.optJSONArray("groups")?.objs().orEmpty() }
    suspend fun openGroup(id: String) {
        val r = vm.api.get("/api/groups/$id")
        if (r.ok) open = r.body?.optJSONObject("group") else info = r.message ?: "Could not open the group."
    }
    fun apply(r: com.crowncouncil.orbit.net.ApiResult) {
        if (r.ok) { r.body?.optJSONObject("group")?.let { open = it }; info = null }
        else info = r.message ?: if (r.status == 0) "Cannot reach the server." else "That did not work. Check the details."
    }
    LaunchedEffect(user) { if (user != null) loadList() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Group trips")
        val g = open
        if (user == null) {
            InfoCard("Sign in to plan together", "Group trips need an account so friends can join with a code. Create one under More, Account.")
        } else if (g == null) {
            Text("Create a group, share its code, then add expenses as you go. ORBIT works out who owes whom. It does not move any money.", fontSize = 13.sp)
            OutlinedTextField(newName, { newName = it.take(40) }, label = { Text(tr("Group name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = newName.trim().length >= 2, onClick = {
                scope.launch { val r = vm.api.post("/api/groups", JSONObject().put("name", newName.trim())); apply(r); newName = ""; loadList() }
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("Create group")) }
            OutlinedTextField(joinCode, { joinCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, label = { Text(tr("Invite code")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = joinCode.length == 6, onClick = {
                scope.launch { val r = vm.api.post("/api/groups/join", JSONObject().put("code", joinCode)); apply(r); joinCode = ""; loadList() }
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("Join with code")) }
            SectionLabel("YOUR GROUPS")
            if (groups.isEmpty()) Text("No groups yet.", fontSize = 13.sp)
            groups.forEach { grp ->
                Card(Modifier.fillMaxWidth().clickable { scope.launch { openGroup(grp.getString("id")) } }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(grp.getString("name"), fontWeight = FontWeight.Bold)
                        Text("${grp.optInt("memberCount")} members | code ${grp.optString("code")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            GroupDetail(vm, g, onBack = { open = null; scope.launch { loadList() } }, onChange = { apply(it) }, onShare = { shareText(ctx, it, "Invite to group") }, onAdd = { showAdd = true })
            if (showAdd) AddGroupExpenseDialog(g, onDismiss = { showAdd = false }) { desc, amount, payer ->
                showAdd = false
                scope.launch { apply(vm.api.post("/api/groups/${g.getString("id")}/expenses", JSONObject().put("description", desc).put("amount", amount).put("paidBy", payer))) }
            }
        }
        info?.let { Text(it, color = Rose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun GroupDetail(vm: OrbitViewModel, g: JSONObject, onBack: () -> Unit, onChange: (com.crowncouncil.orbit.net.ApiResult) -> Unit, onShare: (String) -> Unit, onAdd: () -> Unit) {
    val scope = rememberCoroutineScope()
    val trip by vm.currentTrip.collectAsState()
    val rows by vm.rows.collectAsState()
    val id = g.getString("id")
    val members = g.getJSONArray("members").objs()
    val active = members.filter { !it.optBoolean("left") }

    TextButton(onClick = onBack) { Text(tr("Back")) }
    Text(g.getString("name"), style = MaterialTheme.typography.titleLarge)
    InfoCard("Invite code: ${g.getString("code")}", "${active.size} members: ${active.joinToString(", ") { it.getString("displayName") }}")
    OutlinedButton(onClick = { onShare("Join my ORBIT group \"${g.getString("name")}\" with the code ${g.getString("code")}.") }) { Text("Share invite") }

    g.optJSONObject("plan")?.let { p ->
        SectionLabel("SHARED PLAN")
        InfoCard("${p.getString("destinationName")}, ${p.optInt("days")} days, ${p.optString("tier")} tour", "Shared by ${p.optString("sharedBy")}\n" +
            p.getJSONArray("items").objs().joinToString("\n") { "Day ${it.optInt("day")}, %02d:00 ${it.optString("name")}".format(it.optInt("hour")) })
    }
    trip?.let { tr0 ->
        OutlinedButton(onClick = {
            val items = JSONArray().also { arr -> rows.forEach { arr.put(JSONObject().put("day", it.dayNo).put("hour", it.startHour).put("name", it.name)) } }
            scope.launch { onChange(vm.api.post("/api/groups/$id/plan", JSONObject().put("plan", JSONObject().put("destinationName", tr0.title).put("days", tr0.days).put("tier", tr0.tier.lowercase()).put("items", items)))) }
        }) { Text("Share my current trip with this group") }
    }

    SectionLabel("EXPENSES")
    Text("Total: ${bdt(g.optInt("total"))}", fontWeight = FontWeight.SemiBold)
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(tr("Add expense")) }
    g.getJSONArray("expenses").objs().reversed().forEach { e ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("${e.getString("description")}: ${bdt(e.getInt("amount"))}", fontWeight = FontWeight.SemiBold)
                Text("Paid by ${e.optString("paidByName")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { scope.launch { onChange(vm.api.delete("/api/groups/$id/expenses/${e.getString("id")}")) } }) { Text(tr("Delete")) }
        }
    }

    SectionLabel("BALANCES")
    g.getJSONArray("balances").objs().forEach { b ->
        val amt = b.getInt("amount")
        Text(when { amt > 0 -> "${b.getString("displayName")} gets back ${bdt(amt)}"; amt < 0 -> "${b.getString("displayName")} owes ${bdt(-amt)}"; else -> "${b.getString("displayName")} is settled" }, fontSize = 14.sp)
    }
    SectionLabel("WHO PAYS WHOM")
    val settle = g.getJSONArray("settlements").objs()
    if (settle.isEmpty()) Text("Everyone is settled.", fontSize = 14.sp)
    settle.forEach { s -> Text("${s.getString("fromName")} pays ${s.getString("toName")} ${bdt(s.getInt("amount"))}", fontWeight = FontWeight.SemiBold) }
    Text("These are calculations only. Pay each other however you like, for example cash, bKash or Nagad, outside ORBIT.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    OutlinedButton(onClick = { scope.launch { val r = vm.api.post("/api/groups/$id/leave", JSONObject()); if (r.ok) onBack() else onChange(r) } }) { Text(tr("Leave group")) }
}

@Composable
private fun AddGroupExpenseDialog(g: JSONObject, onDismiss: () -> Unit, onSave: (String, Int, String) -> Unit) {
    val members = g.getJSONArray("members").objs().filter { !it.optBoolean("left") }
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf(members.firstOrNull { it.optBoolean("isMe") }?.getString("mid") ?: members.first().getString("mid")) }
    val value = amount.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add expense")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(desc, { desc = it.take(60) }, label = { Text("What was it for?") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(8) }, label = { Text("Amount (BDT)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Who paid?", fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { m -> FilterChip(selected = payer == m.getString("mid"), onClick = { payer = m.getString("mid") }, label = { Text(m.getString("displayName")) }) }
                }
                Text("Split equally between everyone in the group.", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = desc.trim().length >= 2 && value != null && value > 0, onClick = { onSave(desc.trim(), value ?: 0, payer) }) { Text(tr("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
