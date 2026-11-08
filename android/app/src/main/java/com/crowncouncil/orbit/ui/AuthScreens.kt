package com.crowncouncil.orbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowncouncil.orbit.net.ApiResult
import com.crowncouncil.orbit.net.Consent

private enum class AuthStep { Welcome, Login, Signup, Verify }

private fun friendly(r: ApiResult): String = r.message ?: when {
    r.status == 0 -> "Cannot reach the server. Check your internet connection and the server address."
    r.error == "invalid_credentials" -> "Wrong email, number or password."
    r.error == "not_verified" -> "Verify your account first. Create the account again to get a new code."
    r.error == "account_exists" -> "An account with this email or number already exists. Try signing in."
    r.error == "invalid_code" -> "That code is wrong or expired."
    r.error == "too_many_requests" -> "Too many attempts. Wait a few minutes and try again."
    r.error == "otp_delivery_not_configured" -> r.message ?: "Account verification is not configured on this server yet."
    else -> "Something went wrong. Please try again."
}

/** Shown on first launch until the traveller signs in, creates an account or continues as guest. */
@Composable
fun AuthGate(vm: OrbitViewModel) {
    var step by remember { mutableStateOf(AuthStep.Welcome) }
    var identifier by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, Color(0xFF0B1F3A))))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ORBIT", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 42.sp, letterSpacing = 5.sp), color = Gold, fontWeight = FontWeight.Black)
                    Text("TRAVEL, WITH INTENTION", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("Plan smarter. Travel deeper.", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Your personal companion for discovering Bangladesh.", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                }
            }
            val ctx = LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = I18n.lang == "en", onClick = { I18n.set(ctx, "en") }, label = { Text("English") })
                FilterChip(selected = I18n.lang == "bn", onClick = { I18n.set(ctx, "bn") }, label = { Text("বাংলা") })
            }
            when (step) {
                AuthStep.Welcome -> Welcome(
                    onSignup = { step = AuthStep.Signup }, onLogin = { step = AuthStep.Login }, onGuest = { vm.continueAsGuest() }
                )
                AuthStep.Login -> LoginForm(vm, onBack = { step = AuthStep.Welcome }, onSignup = { step = AuthStep.Signup })
                AuthStep.Signup -> SignupForm(vm, onBack = { step = AuthStep.Welcome }) { id, code -> identifier = id; devCode = code; step = AuthStep.Verify }
                AuthStep.Verify -> VerifyForm(vm, identifier, devCode, onBack = { step = AuthStep.Welcome })
            }
        }
    }
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
}

@Composable
private fun Welcome(onSignup: () -> Unit, onLogin: () -> Unit, onGuest: () -> Unit) {
    FormCard {
        Button(onClick = onSignup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold)) { Text(tr("Create account")) }
        OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(tr("Sign in")) }
        TextButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) { Text(tr("Continue as guest")) }
        Text(
            "Guest mode keeps everything on this phone, including the built-in guide. Sync, rankings and spot submissions need an account.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoginForm(vm: OrbitViewModel, onBack: () -> Unit, onSignup: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FormCard {
        Text("Sign in", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(id, { id = it }, label = { Text(tr("Email or mobile number")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pw, { pw = it }, label = { Text(tr("Password")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(it, color = Rose, fontSize = 13.sp) }
        Button(
            onClick = { busy = true; error = null; vm.login(id.trim(), pw) { r -> busy = false; if (!r.ok) error = friendly(r) } },
            enabled = !busy && id.isNotBlank() && pw.isNotEmpty(), modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Signing in..." else "Sign in") }
        Row { TextButton(onClick = onBack) { Text(tr("Back")) }; TextButton(onClick = onSignup) { Text(tr("Create account")) } }
    }
}

@Composable
private fun SignupForm(vm: OrbitViewModel, onBack: () -> Unit, onCreated: (String, String?) -> Unit) {
    val uri = LocalUriHandler.current
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf(false) }
    var loc by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FormCard {
        Text("Create your account", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it.take(30) }, label = { Text(tr("Display name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(id, { id = it }, label = { Text(tr("Email or mobile number")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pw, { pw = it }, label = { Text("Password (8+ characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))

        SectionLabel("AGREEMENTS")
        ConsentRow(terms, { terms = it }, "I accept the Terms of Use and Privacy Policy (required)", null)
        Row {
            TextButton(onClick = { uri.openUri("${vm.baseUrl}/terms") }) { Text("Read Terms") }
            TextButton(onClick = { uri.openUri("${vm.baseUrl}/privacy") }) { Text("Read Privacy Policy") }
        }
        SectionLabel("OPTIONAL, OFF BY DEFAULT")
        if (com.crowncouncil.orbit.BuildConfig.USE_CLOUD_AI) {
            ConsentRow(ai, { ai = it }, "Cloud AI guide", "Lets your chat messages, visit history and saved notes be processed by the optional cloud AI provider.")
            ConsentRow(loc, { loc = it }, "Share location with the cloud guide", "Your live location is sent to the cloud guide only when you ask it for trip help. It is never stored on our server.")
        }
        ConsentRow(board, { board = it }, "Public leaderboard", "Shows your display name and visit counts to other travellers.")
        Text("You can change these any time under Account.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = Rose, fontSize = 13.sp) }
        Button(
            onClick = {
                busy = true; error = null
                vm.signup(id.trim(), pw, name.trim(), Consent(ai, loc, board)) { r ->
                    busy = false
                    if (r.ok) onCreated(id.trim(), r.body?.optString("devCode")?.takeIf { it.isNotEmpty() }) else error = friendly(r)
                }
            },
            enabled = !busy && terms && name.trim().length >= 2 && id.isNotBlank() && pw.length >= 8, modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Creating..." else "Create account") }
        TextButton(onClick = onBack) { Text(tr("Back")) }
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onChange: (Boolean) -> Unit, title: String, detail: String?) {
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (detail != null) Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VerifyForm(vm: OrbitViewModel, identifier: String, devCode: String?, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FormCard {
        Text("Verify your account", style = MaterialTheme.typography.titleLarge)
        Text("Enter the 6-digit code sent to $identifier.", fontSize = 14.sp)
        if (devCode != null) Text("Development server: your code is $devCode", fontSize = 13.sp, color = Teal, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Code") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        error?.let { Text(it, color = Rose, fontSize = 13.sp) }
        Button(
            onClick = { busy = true; error = null; vm.verify(identifier, code) { r -> busy = false; if (!r.ok) error = friendly(r) } },
            enabled = !busy && code.length == 6, modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Checking..." else "Verify") }
        TextButton(onClick = onBack) { Text(tr("Back")) }
    }
}
