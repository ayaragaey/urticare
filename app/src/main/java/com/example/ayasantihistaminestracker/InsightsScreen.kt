package com.example.ayasantihistaminestracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var showStabilityInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights Dashboard", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item { HeaderSection(uiState) }
                    item { StabilitySection(uiState) { showStabilityInfo = true } }
                    item { MedicationSection(uiState) }
                    item { FlareUpSection(uiState) }
                    item { PatternsAndTriggersSection(uiState) }
                    item { ShortGapReasonsSection(uiState) }
                    item { SmartInsightsSection(uiState.smartInsights) }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            if (showStabilityInfo) {
                AlertDialog(
                    onDismissRequest = { showStabilityInfo = false },
                    title = { Text("What is Stability Rating?", color = Color.White) },
                    text = {
                        Text(
                            text = "The Stability Rating estimates how controlled your condition has been recently.\n\n" +
                                    "It is based on:\n" +
                                    "• Flare-up frequency\n" +
                                    "• Medication dependency\n" +
                                    "• Cortisone usage\n" +
                                    "• Flare-free periods\n" +
                                    "• Repeated flare activity\n\n" +
                                    "Higher scores suggest more stable disease activity.",
                            color = Color.LightGray
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showStabilityInfo = false }) {
                            Text("Got it", color = Color(0xFF42A5F5))
                        }
                    },
                    containerColor = Color(0xFF2C2C2C),
                    textContentColor = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun HeaderSection(state: InsightsUiState) {
    Column {
        Text(
            text = "${state.name} • ${state.activeFilter.replace("DateFilter.", "")}",
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}

@Composable
fun StabilitySection(state: InsightsUiState, onInfoClick: () -> Unit) {
    InsightCard(title = "Stability Rating") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${state.stabilityScore}",
                color = getStabilityColor(state.stabilityScore),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(48.dp).padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Stability Information",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        LinearProgressIndicator(
            progress = { state.stabilityScore / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 16.dp),
            color = getStabilityColor(state.stabilityScore),
            trackColor = Color.DarkGray,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun MedicationSection(state: InsightsUiState) {
    InsightCard(title = "Medication") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItem("Total Antihistamines", state.totalAntihistamines.toString(), Color(0xFF42A5F5))
            StatItem("Total Cortisone", state.totalCortisone.toString(), Color(0xFFFFCA28))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        MetricRow("Shortest gap between pills", "${state.shortestPillGap}h")
        MetricRow("Longest pill-free streak", "${state.longestPillFreeStreak}d")
        MetricRow("Most common med time", state.mostCommonMedicationTime)
        MetricRow("Number of medication days", state.numberMedicationDays.toString())
    }
}

@Composable
fun FlareUpSection(state: InsightsUiState) {
    InsightCard(title = "Flare-Ups") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItem("Total Flare-ups", state.totalFlareUps.toString(), Color(0xFFE91E63))
            StatItem("Angioedema Rate", "${state.angioedemaPercent}%", Color(0xFFF48FB1))
        }

        Spacer(modifier = Modifier.height(16.dp))

        MetricRow("Longest flare-free streak", "${state.longestFlareFreeStreak}d")
        MetricRow("Repeated flare days", state.repeatedFlareDays.toString())
        MetricRow("Most active flare period", state.mostActiveFlarePeriod)
        
        if (state.flareDistribution.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Flare distribution", color = Color.Gray, fontSize = 12.sp)
            state.flareDistribution.take(3).forEach { (period, count) ->
                MetricRow(period, count.toString())
            }
        }
    }
}

@Composable
fun PatternsAndTriggersSection(state: InsightsUiState) {
    InsightCard(title = "Triggers & Associations") {
        if (state.topTriggers.isNotEmpty()) {
            Text("Most repeated triggers", color = Color.Gray, fontSize = 12.sp)
            state.topTriggers.take(3).forEach { (trigger, count) ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(trigger, color = Color.White, fontSize = 14.sp)
                    Text("$count logs", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        if (state.triggerCombinations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Trigger combinations", color = Color.Gray, fontSize = 12.sp)
            state.triggerCombinations.take(2).forEach { (combo, count) ->
                Text("• $combo ($count times)", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        
        if (state.topFlareReasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Reason frequency", color = Color.Gray, fontSize = 12.sp)
            state.topFlareReasons.take(2).forEach { (reason, percent) ->
                MetricRow(reason, "$percent%")
            }
        }
    }
}

@Composable
fun InsightCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            content()
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column {
        Text(text = value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun ShortGapReasonsSection(state: InsightsUiState) {
    if (state.topShortGapReasons.isEmpty()) return

    InsightCard(title = "Why you took extra doses early") {
        state.topShortGapReasons.forEach { (reason, count) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = reason,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${count}x",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SmartInsightsSection(insights: List<String>) {
    if (insights.isEmpty()) return
    Column {
        Text(
            "Smart Insights",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        insights.take(4).forEach { insight ->
            Surface(
                color = Color(0xFF2C2C2C),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💡", modifier = Modifier.padding(end = 12.dp), fontSize = 18.sp)
                    Text(text = insight, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

private fun getStabilityColor(score: Int): Color {
    return when {
        score > 80 -> Color(0xFF66BB6A)
        score > 50 -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }
}
