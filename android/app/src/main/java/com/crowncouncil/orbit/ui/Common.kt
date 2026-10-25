package com.crowncouncil.orbit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowncouncil.orbit.data.Destination
import java.text.DecimalFormat

val INTERESTS = listOf("beach", "hills", "nature", "food", "culture", "adventure")
val CATEGORIES = listOf("Transport", "Stay", "Food", "Activities", "Other")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun monthName(m: Int) = tr(MONTHS[(m - 1).coerceIn(0, 11)])
fun bdt(amount: Int): String = "BDT " + DecimalFormat("#,###", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(amount)
fun String.cap() = replaceFirstChar { it.uppercase() }

/** Offline placeholder: a real weather API is planned for a later phase (see the roadmap). */
fun weatherLabel(dest: Destination?, month: Int): String {
    val dry = dest?.bestMonths?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.contains(month) == true
    return if (dry) "Dry and pleasant" else "Rain possible"
}

@Composable
fun Title(text: String) = Text(tr(text), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

fun tierBlurb(t: com.crowncouncil.orbit.domain.Tier) = when (t) {
    com.crowncouncil.orbit.domain.Tier.BUDGET -> "Lowest sensible cost: simple stays, local food, low-cost activities."
    com.crowncouncil.orbit.domain.Tier.VALUE -> "Affordable but good quality: the best experience per taka."
    com.crowncouncil.orbit.domain.Tier.FANCY -> "Comfort first: premium stays and experiences."
}

@Composable
fun SectionLabel(text: String) = Text(tr(text).uppercase(), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)

@Composable
fun InfoCard(title: String, body: String, container: Color = MaterialTheme.colorScheme.surface, content: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 23.sp)
            Text(body, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("-") }
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("+") }
        }
    }
}

fun severityColor(severity: String): Color = when (severity) { "danger" -> Rose; "warning" -> Amber; else -> Teal }
fun severityRank(severity: String): Int = when (severity) { "danger" -> 3; "warning" -> 2; else -> 1 }

@Composable
fun TransportCard(info: com.crowncouncil.orbit.domain.TransportInfo) {
    InfoCard(
        "Getting there from Dhaka",
        info.lines.joinToString("\n") + if (info.footnote.isNotEmpty()) "\n" + info.footnote else ""
    )
}
