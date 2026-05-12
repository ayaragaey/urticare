package com.example.ayasantihistaminestracker

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * Lightweight model representing a Pill Intake event.
 */
data class PillEvent(
    val timestamp: Long,
    val type: String, // "Antihistamine", "Cortison", "Xolair", etc.
    val originalLog: String,
    val mainReason: String = "",
    val subReason: String = "",
    val notes: String = ""
)

/**
 * Lightweight model representing a Flare Up event.
 */
data class FlareEvent(
    val timestamp: Long,
    val severity: String, // "Mild", "Severe", "None"
    val angioedema: String, // "Angioedema", "No Angioedema", ""
    val reasons: String,
    val originalLog: String
)

/**
 * Model representing an associated trigger signal.
 */
data class TriggerInsight(
    val triggerName: String,
    val associationStrength: Double, // 0.0 to 1.0 (frequency of flare within 24h of trigger)
    val confidence: Double, // 0.0 to 1.0 (based on sample size)
    val sampleSize: Int
)

/**
 * Model representing an insight about medication gaps.
 */
data class GapInsight(
    val thresholdHours: Int,
    val flareRate: Double,
    val message: String
)

/**
 * Model representing an insight about the menstrual cycle.
 */
data class CycleInsight(
    val strongestPhase: String, // "Before", "During", "After"
    val flareDensity: Double, // Flares per day in that phase
    val message: String
)

/**
 * Model representing calculated metrics for a specific time range.
 */
data class InsightsMetrics(
    val totalAntihistamines: Int,
    val totalCortisone: Int,
    val shortestPillGapHours: Double,
    val longestPillFreeStreakDays: Double,
    val mostCommonMedicationTime: String,
    val numberMedicationDays: Int,
    val daysWithRepeatedMedicationUse: Int,
    val unstableDependencyPeriods: List<String>,
    val totalFlareUps: Int,
    val longestFlareFreeStreakDays: Double,
    val repeatedFlareDays: Int,
    val mostActiveFlarePeriod: String,
    val mostSevereFlarePeriod: String,
    val flareDistribution: List<Pair<String, Int>>,
    val instabilitySpikes: List<String>,
    val topTriggers: List<Pair<String, Int>>,
    val triggerCombinations: List<Pair<String, Int>>,
    val angioedemaRate: Double, // Percentage 0-100
    val topReasons: List<Pair<String, Double>>, // List of (Reason, Percentage)
    val triggerInsights: List<TriggerInsight>,
    val gapInsights: List<GapInsight>,
    val topShortGapReasons: List<Pair<String, Int>>,
    val cycleInsight: CycleInsight?,
    val treatmentInsights: List<String>,
    val timingInsight: String?,
    val stabilityScore: Int
)

/**
 * Lightweight model representing a logged trigger session.
 */
data class TriggerSession(
    val timestamp: Long,
    val triggers: List<String>,
    val source: String = "Manual Entry"
)

/**
 * Lightweight model representing a Menstrual Cycle event.
 */
data class CycleEvent(
    val start: Long,
    val end: Long
)

/**
 * Comprehensive report structure for the Insights UI.
 */
data class FinalReport(
    val totalAntihistamines: Int,
    val totalCortisone: Int,
    val shortestPillGapHours: Double,
    val longestPillFreeStreakDays: Double,
    val mostCommonMedicationTime: String,
    val numberMedicationDays: Int,
    val daysWithRepeatedMedicationUse: Int,
    val unstableDependencyPeriods: List<String>,
    val totalFlareUps: Int,
    val longestFlareFreeStreakDays: Double,
    val repeatedFlareDays: Int,
    val mostActiveFlarePeriod: String,
    val mostSevereFlarePeriod: String,
    val flareDistribution: List<Pair<String, Int>>,
    val instabilitySpikes: List<String>,
    val topTriggers: List<Pair<String, Int>>,
    val triggerCombinations: List<Pair<String, Int>>,
    val topShortGapReasons: List<Pair<String, Int>>,
    val angioedemaRate: Double,
    val stabilityScore: Int,
    val topReasons: List<Pair<String, Double>>,
    val smartInsights: List<String>,
    val detectedPatterns: List<String>
)

/**
 * Insights Engine
 */
object InsightsEngine {

    private val logFormat = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.US)
    private val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L

    /**
     * High-level function to generate the final insights report from all sources.
     */
    fun generateFullReport(
        pillPrefs: SharedPreferences,
        profilePrefs: SharedPreferences,
        triggerPrefs: SharedPreferences
    ): FinalReport {
        val pills = mapPillsLogToEvents(pillPrefs)
        val flares = mapFlareLogToEvents(pillPrefs)
        val triggers = mapTriggerLogToSessions(triggerPrefs)
        val cycles = mapCycleLogToEntries(profilePrefs)
        
        val metrics = calculateMetrics(pills, flares, triggers, cycles, pills)
        
        return createReport(metrics)
    }

    /**
     * Helper to construct a FinalReport from calculated metrics.
     */
    fun createReport(
        metrics: InsightsMetrics
    ): FinalReport {
        val smartInsights = mutableListOf<String>()
        val detectedPatterns = mutableListOf<String>()

        // 1) Timing Patterns
        metrics.timingInsight?.let { smartInsights.add(it) }

        // 2) Medication Gap & Dependency Patterns
        metrics.gapInsights.forEach { smartInsights.add(it.message) }
        if (metrics.shortestPillGapHours > 0 && metrics.shortestPillGapHours < 6) {
            smartInsights.add("Short pill gaps detected: Medication dependency appears high.")
        }
        if (metrics.daysWithRepeatedMedicationUse > 3) {
             smartInsights.add("Frequent multi-dose days suggest periods of unstable control.")
        }

        // 3) Flare Activity Patterns
        if (metrics.repeatedFlareDays > 3) {
            detectedPatterns.add("Repeated daily flare-ups suggest a temporary loss of control.")
        }
        if (metrics.instabilitySpikes.isNotEmpty()) {
            detectedPatterns.add("Sudden instability spikes detected after stable periods.")
        }

        // 4) Treatment & Cycle Patterns (Xolair/Alternative)
        metrics.treatmentInsights.forEach { detectedPatterns.add(it) }
        metrics.cycleInsight?.let { detectedPatterns.add(it.message) }

        // 5. Trigger Associations (Confidence-based) - Aggregated to 1 entry
        val possibleAssociations = metrics.triggerInsights
            .filter { it.confidence <= 0.8 }
            .map { it.triggerName }
        
        val strongAssociations = metrics.triggerInsights
            .filter { it.confidence > 0.8 }
            .map { it.triggerName }

        if (strongAssociations.isNotEmpty()) {
            detectedPatterns.add("Strong signals detected for: ${strongAssociations.joinToString(", ")}.")
        }
        
        if (possibleAssociations.isNotEmpty()) {
            detectedPatterns.add("Possible associations: ${possibleAssociations.joinToString(", ")}.")
        }

        // 6. Stability Score Context
        if (metrics.stabilityScore < 40) {
            smartInsights.add("Overall stability is low. Patterns suggest current control is insufficient.")
        } else if (metrics.stabilityScore > 85 && metrics.totalFlareUps == 0) {
            smartInsights.add("Excellent stability! Current regimen is maintaining clear control.")
        }

        return FinalReport(
            totalAntihistamines = metrics.totalAntihistamines,
            totalCortisone = metrics.totalCortisone,
            shortestPillGapHours = metrics.shortestPillGapHours,
            longestPillFreeStreakDays = metrics.longestPillFreeStreakDays,
            mostCommonMedicationTime = metrics.mostCommonMedicationTime,
            numberMedicationDays = metrics.numberMedicationDays,
            daysWithRepeatedMedicationUse = metrics.daysWithRepeatedMedicationUse,
            unstableDependencyPeriods = metrics.unstableDependencyPeriods,
            totalFlareUps = metrics.totalFlareUps,
            longestFlareFreeStreakDays = metrics.longestFlareFreeStreakDays,
            repeatedFlareDays = metrics.repeatedFlareDays,
            mostActiveFlarePeriod = metrics.mostActiveFlarePeriod,
            mostSevereFlarePeriod = metrics.mostSevereFlarePeriod,
            flareDistribution = metrics.flareDistribution,
            instabilitySpikes = metrics.instabilitySpikes,
            topTriggers = metrics.topTriggers,
            triggerCombinations = metrics.triggerCombinations,
            topShortGapReasons = metrics.topShortGapReasons,
            angioedemaRate = metrics.angioedemaRate,
            stabilityScore = metrics.stabilityScore,
            topReasons = metrics.topReasons,
            smartInsights = smartInsights,
            detectedPatterns = detectedPatterns
        )
    }

    /**
     * Maps the raw trigger logs into a sorted list of TriggerSessions.
     */
    fun mapTriggerLogToSessions(triggerPrefs: SharedPreferences): List<TriggerSession> {
        val sessions = mutableListOf<TriggerSession>()
        val json = triggerPrefs.getString("logs", "[]") ?: "[]"
        
        try {
            val gson = Gson()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(json, type)
            
            rawList.forEach { map ->
                val ts = (map["timestamp"] as? Number)?.toLong() ?: 0L
                val triggers = (map["triggers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val source = (map["source"] as? String) ?: "Manual Entry"
                if (ts > 0) sessions.add(TriggerSession(ts, triggers, source))
            }
        } catch (e: Exception) {}
        
        return sessions.sortedBy { it.timestamp }
    }

    /**
     * Maps the cycle history from ProfileData SharedPreferences.
     */
    fun mapCycleLogToEntries(profilePrefs: SharedPreferences): List<CycleEvent> {
        val entries = mutableListOf<CycleEvent>()
        
        // Current/latest cycle
        val start = profilePrefs.getLong("mens_start", 0)
        val end = profilePrefs.getLong("mens_end", 0)
        if (start > 0) {
            entries.add(CycleEvent(start, if (end > 0) end else System.currentTimeMillis()))
        }
        
        // History
        val json = profilePrefs.getString("cycle_history", "[]") ?: "[]"
        try {
            val gson = Gson()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(json, type)
            rawList.forEach { map ->
                val s = (map["start"] as? Number)?.toLong() ?: 0L
                val e = (map["end"] as? Number)?.toLong() ?: 0L
                if (s > 0 && e > 0) entries.add(CycleEvent(s, e))
            }
        } catch (e: Exception) {}
        
        return entries.distinctBy { it.start }.sortedBy { it.start }
    }

    fun calculateMetrics(
        pillEvents: List<PillEvent>, 
        flareEvents: List<FlareEvent>,
        triggerSessions: List<TriggerSession> = emptyList(),
        cycleEvents: List<CycleEvent> = emptyList(),
        treatmentEvents: List<PillEvent> = emptyList()
    ): InsightsMetrics {
        // 1) Medication usage
        val totalAntihistamines = pillEvents.count { it.type == "Antihistamine" }
        val totalCortisone = pillEvents.count { it.type == "Cortison" }
        
        val shortestPillGapHours = calculateShortestPillGap(pillEvents)
        val longestPillFreeStreakDays = calculateLongestPillFreeStreak(pillEvents)
        val mostCommonMedicationTime = calculateMostCommonMedicationTime(pillEvents)
        val numberMedicationDays = calculateNumberMedicationDays(pillEvents)
        val daysWithRepeatedMedicationUse = calculateDaysWithRepeatedMedicationUse(pillEvents)
        val unstableDependencyPeriods = detectUnstableDependencyPeriods(pillEvents)

        // 2) Flare frequency
        val totalFlareUps = flareEvents.size
        val longestFlareFreeStreakDays = calculateLongestFlareFreeStreak(flareEvents)
        val repeatedFlareDays = calculateRepeatedFlareDays(flareEvents)
        val mostActiveFlarePeriod = calculateMostActiveFlarePeriod(flareEvents)
        val mostSevereFlarePeriod = calculateMostSevereFlarePeriod(flareEvents)
        val flareDistribution = calculateFlareDistribution(flareEvents)
        val instabilitySpikes = detectInstabilitySpikes(flareEvents, pillEvents)

        // 3) Trigger Analysis
        val topTriggers = calculateTriggerFrequency(triggerSessions)
        val triggerCombinations = calculateTriggerCombinations(triggerSessions)

        // 4) Angioedema rate
        val angioCount = flareEvents.count { it.angioedema == "Angioedema" }
        val angioRate = if (flareEvents.isNotEmpty()) (angioCount.toDouble() / flareEvents.size) * 100 else 0.0

        // 5) Top flare reasons
        val topReasons = calculateTopReasons(flareEvents)
        
        // 6) Trigger Associations (Smart Insights)
        val triggerInsights = calculateTriggerAssociations(triggerSessions, flareEvents)
        
        // 7) Medication Gap Analysis (Smart Insights)
        val gapInsights = calculateGapInsights(pillEvents, flareEvents)
        
        // 8) Cycle Analysis
        val cycleInsight = calculateCycleInsights(cycleEvents, flareEvents)

        // 9) Treatment Analysis (Xolair)
        val treatmentInsights = calculateTreatmentInsights(treatmentEvents.ifEmpty { pillEvents }, flareEvents)

        // 10) Timing Analysis
        val timingInsight = calculateTimingInsight(flareEvents)

        // 11) Stability Score
        val stabilityScore = calculateStabilityScore(flareEvents, totalCortisone, pillEvents)

        val topShortGapReasons = calculateShortGapPatterns(pillEvents)

        return InsightsMetrics(
            totalAntihistamines = totalAntihistamines,
            totalCortisone = totalCortisone,
            shortestPillGapHours = shortestPillGapHours,
            longestPillFreeStreakDays = longestPillFreeStreakDays,
            mostCommonMedicationTime = mostCommonMedicationTime,
            numberMedicationDays = numberMedicationDays,
            daysWithRepeatedMedicationUse = daysWithRepeatedMedicationUse,
            unstableDependencyPeriods = unstableDependencyPeriods,
            totalFlareUps = totalFlareUps,
            longestFlareFreeStreakDays = longestFlareFreeStreakDays,
            repeatedFlareDays = repeatedFlareDays,
            mostActiveFlarePeriod = mostActiveFlarePeriod,
            mostSevereFlarePeriod = mostSevereFlarePeriod,
            flareDistribution = flareDistribution,
            instabilitySpikes = instabilitySpikes,
            topTriggers = topTriggers,
            triggerCombinations = triggerCombinations,
            angioedemaRate = angioRate,
            topReasons = topReasons,
            triggerInsights = triggerInsights,
            gapInsights = gapInsights,
            topShortGapReasons = topShortGapReasons,
            cycleInsight = cycleInsight,
            treatmentInsights = treatmentInsights,
            timingInsight = timingInsight,
            stabilityScore = stabilityScore
        )
    }

    private fun calculateShortGapPatterns(pills: List<PillEvent>): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        pills.forEach {
            if (it.mainReason.isNotEmpty()) {
                counts[it.mainReason] = counts.getOrDefault(it.mainReason, 0) + 1
                if (it.subReason.isNotEmpty()) {
                    val key = "${it.mainReason}: ${it.subReason}"
                    counts[key] = counts.getOrDefault(key, 0) + 1
                }
            }
        }
        return counts.toList().sortedByDescending { it.second }.take(10)
    }

    private fun calculateDaysWithRepeatedMedicationUse(pills: List<PillEvent>): Int {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dayCounts = pills.groupingBy { dateFormat.format(Date(it.timestamp)) }.eachCount()
        return dayCounts.count { it.value > 1 }
    }

    private fun detectUnstableDependencyPeriods(pills: List<PillEvent>): List<String> {
        if (pills.isEmpty()) return emptyList()
        val periods = mutableListOf<String>()
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)
        
        // Unstable if gaps are consistently small for a period
        val threshold = 8.0 // hours
        val windowSize = 5
        for (i in 0 until pills.size - windowSize) {
            val window = pills.subList(i, i + windowSize)
            val gaps = (1 until window.size).map { (window[it].timestamp - window[it-1].timestamp) / (1000 * 60 * 60.0) }
            if (gaps.all { it < threshold }) {
                val month = monthFormat.format(Date(window[0].timestamp))
                periods.add("Possible instability in $month")
            }
        }
        return periods.distinct()
    }

    private fun calculateMostSevereFlarePeriod(flares: List<FlareEvent>): String {
        if (flares.isEmpty()) return "N/A"
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)
        val severityMap = mapOf("Mild" to 1, "Moderate" to 2, "Severe" to 3, "None" to 0)
        
        val monthSeverity = flares.groupBy { monthFormat.format(Date(it.timestamp)) }
            .mapValues { (_, events) -> events.sumOf { severityMap[it.severity] ?: 0 } }
        
        return monthSeverity.maxByOrNull { it.value }?.key ?: "N/A"
    }

    private fun detectInstabilitySpikes(flares: List<FlareEvent>, pills: List<PillEvent>): List<String> {
        if (flares.isEmpty()) return emptyList()
        val spikes = mutableListOf<String>()
        val dayFormat = SimpleDateFormat("MMM dd", Locale.US)
        
        // Instability spike: Sudden flare after a long flare-free period
        for (i in 1 until flares.size) {
            val gap = (flares[i].timestamp - flares[i-1].timestamp) / (1000 * 60 * 60 * 24.0)
            if (gap > 14.0) { // After 2 weeks of peace
                spikes.add("Spike after ${gap.toInt()} days of peace on ${dayFormat.format(Date(flares[i].timestamp))}")
            }
        }
        return spikes.take(3)
    }

    private fun calculateShortestPillGap(pills: List<PillEvent>): Double {
        if (pills.size < 2) return 0.0
        var minGapMs = Long.MAX_VALUE
        for (i in 1 until pills.size) {
            val gap = pills[i].timestamp - pills[i - 1].timestamp
            if (gap > 0 && gap < minGapMs) minGapMs = gap
        }
        return if (minGapMs == Long.MAX_VALUE) 0.0 else minGapMs.toDouble() / (1000 * 60 * 60)
    }

    private fun calculateLongestPillFreeStreak(pills: List<PillEvent>): Double {
        if (pills.isEmpty()) return 0.0
        var maxGapMs = 0L
        for (i in 1 until pills.size) {
            val gap = pills[i].timestamp - pills[i - 1].timestamp
            if (gap > maxGapMs) maxGapMs = gap
        }
        return maxGapMs.toDouble() / (1000 * 60 * 60 * 24)
    }

    private fun calculateMostCommonMedicationTime(pills: List<PillEvent>): String {
        if (pills.isEmpty()) return "N/A"
        val hourCounts = mutableMapOf<Int, Int>()
        val cal = Calendar.getInstance()
        pills.forEach {
            cal.timeInMillis = it.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourCounts[hour] = hourCounts.getOrDefault(hour, 0) + 1
        }
        val mostCommonHour = hourCounts.maxByOrNull { it.value }?.key ?: return "N/A"
        return String.format(Locale.US, "%02d:00", mostCommonHour)
    }

    private fun calculateNumberMedicationDays(pills: List<PillEvent>): Int {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        return pills.map { dateFormat.format(Date(it.timestamp)) }.distinct().size
    }

    private fun calculateLongestFlareFreeStreak(flares: List<FlareEvent>): Double {
        if (flares.isEmpty()) return 0.0
        var maxGapMs = 0L
        for (i in 1 until flares.size) {
            val gap = flares[i].timestamp - flares[i - 1].timestamp
            if (gap > maxGapMs) maxGapMs = gap
        }
        return maxGapMs.toDouble() / (1000 * 60 * 60 * 24)
    }

    private fun calculateRepeatedFlareDays(flares: List<FlareEvent>): Int {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dayCounts = flares.groupingBy { dateFormat.format(Date(it.timestamp)) }.eachCount()
        return dayCounts.count { it.value > 1 }
    }

    private fun calculateMostActiveFlarePeriod(flares: List<FlareEvent>): String {
        if (flares.isEmpty()) return "N/A"
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)
        val monthCounts = flares.groupingBy { monthFormat.format(Date(it.timestamp)) }.eachCount()
        return monthCounts.maxByOrNull { it.value }?.key ?: "N/A"
    }

    private fun calculateFlareDistribution(flares: List<FlareEvent>): List<Pair<String, Int>> {
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)
        return flares.groupingBy { monthFormat.format(Date(it.timestamp)) }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    private fun calculateTriggerFrequency(sessions: List<TriggerSession>): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        sessions.forEach { session ->
            session.triggers.forEach { trigger ->
                val name = trigger.split(":").first().trim().replace("(Other)", "").trim()
                counts[name] = counts.getOrDefault(name, 0) + 1
            }
        }
        return counts.toList().sortedByDescending { it.second }
    }

    private fun calculateTriggerCombinations(sessions: List<TriggerSession>): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        sessions.forEach { session ->
            if (session.triggers.size > 1) {
                val combination = session.triggers.map { it.split(":").first().trim().replace("(Other)", "").trim() }.sorted().joinToString(" + ")
                counts[combination] = counts.getOrDefault(combination, 0) + 1
            }
        }
        return counts.toList().sortedByDescending { it.second }.take(5)
    }

    private fun calculateTimingInsight(flares: List<FlareEvent>): String? {
        if (flares.isEmpty()) return null
        
        val counts = mutableMapOf("Morning" to 0, "Afternoon" to 0, "Evening" to 0, "Night" to 0)
        val cal = Calendar.getInstance()
        
        flares.forEach { flare ->
            cal.timeInMillis = flare.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..11 -> counts["Morning"] = counts["Morning"]!! + 1
                in 12..17 -> counts["Afternoon"] = counts["Afternoon"]!! + 1
                in 18..23 -> counts["Evening"] = counts["Evening"]!! + 1
                else -> counts["Night"] = counts["Night"]!! + 1
            }
        }
        
        val maxEntry = counts.maxByOrNull { it.value } ?: return null
        if (maxEntry.value <= 1) return null // Need at least some evidence
        
        val total = flares.size.toDouble()
        val percentage = (maxEntry.value / total) * 100
        
        // Only return if there's a clear tendency (> 40% of flares in one period)
        return if (percentage > 40.0) {
            "Flare-ups occur more in the ${maxEntry.key.lowercase()}."
        } else null
    }

    private fun calculateStabilityScore(
        flares: List<FlareEvent>,
        totalCortisone: Int,
        pills: List<PillEvent>
    ): Int {
        var score = 100.0
        
        // 1) Flare Activity Impact (Activity-based, not average)
        // Penalty for each flare-up, but capped to avoid zeroing out too fast
        val flarePenalty = (flares.size * 2.0).coerceAtMost(30.0)
        score -= flarePenalty

        // 2) Repeated Flare Days (Instability indicator)
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val repeatedDays = flares.groupingBy { dateFormat.format(Date(it.timestamp)) }
            .eachCount().count { it.value > 1 }
        score -= (repeatedDays * 5.0).coerceAtMost(20.0)

        // 4) Medication Dependency (Cortisone is a major stability reducer)
        score -= (totalCortisone * 10.0).coerceAtMost(40.0)
        
        // 5) Instability Spikes (Loss of control)
        var spikes = 0
        for (i in 1 until flares.size) {
            val gap = (flares[i].timestamp - flares[i-1].timestamp) / (1000 * 60 * 60 * 24.0)
            if (gap > 10.0) spikes++ // Sudden flare after 10+ stable days
        }
        score -= (spikes * 12.0).coerceAtMost(36.0)

        // 6) Stability Bonuses (Rewarding flare-free periods)
        if (flares.isEmpty() && pills.isNotEmpty()) {
            score += 15.0 // Bonus for medication without flares (preventative success)
        }
        
        val longestStreak = calculateLongestFlareFreeStreak(flares)
        if (longestStreak >= 7.0) score += 5.0
        if (longestStreak >= 14.0) score += 10.0

        return score.toInt().coerceIn(0, 100)
    }

    private fun calculateTreatmentInsights(pills: List<PillEvent>, flares: List<FlareEvent>): List<String> {
        val xolairEvents = pills.filter { it.type == "Xolair" }.sortedBy { it.timestamp }
        if (xolairEvents.isEmpty() || flares.isEmpty()) return emptyList()

        val insights = mutableListOf<String>()
        val dayInMs = 24 * 60 * 60 * 1000L

        // Group flares by days since LAST injection
        val flaresByDaySinceInjection = mutableMapOf<Int, Int>()
        
        flares.forEach { flare ->
            // Find the most recent injection BEFORE this flare
            val lastInjection = xolairEvents.lastOrNull { it.timestamp < flare.timestamp }
            if (lastInjection != null) {
                val daysDiff = ((flare.timestamp - lastInjection.timestamp) / dayInMs).toInt()
                // Focus on a 4-week window
                if (daysDiff in 0..31) {
                    flaresByDaySinceInjection[daysDiff] = flaresByDaySinceInjection.getOrDefault(daysDiff, 0) + 1
                }
            }
        }

        if (flaresByDaySinceInjection.isEmpty()) return emptyList()

        // Detect patterns
        val postDose = (0..7).sumOf { flaresByDaySinceInjection.getOrDefault(it, 0) }
        val midCycle = (8..20).sumOf { flaresByDaySinceInjection.getOrDefault(it, 0) }
        val wearOff = (21..31).sumOf { flaresByDaySinceInjection.getOrDefault(it, 0) }
        
        val totalConsidered = postDose + midCycle + wearOff
        if (totalConsidered < 5) return emptyList()

        // 1) Wear-off detection (Days 21+)
        if (wearOff > postDose && wearOff >= 3) {
            insights.add("Xolair Wear-off: Flares are most frequent in the final week before your next dose.")
        }

        // 2) Post-dose spikes (Days 0-3)
        val immediatePost = (0..3).sumOf { flaresByDaySinceInjection.getOrDefault(it, 0) }
        if (immediatePost >= 3 && immediatePost > (totalConsidered * 0.5)) {
            insights.add("Post-Injection Flare: You have a pattern of increased activity in the first 3 days after Xolair.")
        }

        return insights
    }

    private fun calculateCycleInsights(cycles: List<CycleEvent>, flares: List<FlareEvent>): CycleInsight? {
        if (cycles.isEmpty() || flares.isEmpty()) return null

        var flaresDuring = 0
        var totalCycleDays = 0.0
        
        cycles.forEach { cycle ->
            val duration = (cycle.end - cycle.start).toDouble() / (1000 * 60 * 60 * 24)
            totalCycleDays += if (duration < 1.0) 1.0 else duration
            
            flaresDuring += flares.count { it.timestamp in cycle.start..cycle.end }
        }
        
        val duringDensity = if (totalCycleDays > 0) flaresDuring / totalCycleDays else 0.0
        
        // Check "Before" phase (3 days before each cycle)
        var flaresBefore = 0
        cycles.forEach { cycle ->
            val beforeStart = cycle.start - (3 * 24 * 60 * 60 * 1000L)
            flaresBefore += flares.count { it.timestamp in beforeStart until cycle.start }
        }
        val beforeDensity = flaresBefore / (cycles.size * 3.0)

        return when {
            beforeDensity > duringDensity && beforeDensity > 0.5 -> CycleInsight(
                strongestPhase = "Before",
                flareDensity = beforeDensity,
                message = "Flares are most frequent in the 3 days BEFORE your cycle starts."
            )
            duringDensity > 0.5 -> CycleInsight(
                strongestPhase = "During",
                flareDensity = duringDensity,
                message = "Your cycle appears to be a significant trigger for flare-ups."
            )
            else -> null
        }
    }

    private fun calculateGapInsights(pills: List<PillEvent>, flares: List<FlareEvent>): List<GapInsight> {
        if (pills.size < 5 || flares.isEmpty()) return emptyList()

        val thresholds = listOf(6, 8, 12, 24)
        val insights = mutableListOf<GapInsight>()

        thresholds.forEach { hours ->
            val thresholdMs = hours * 60 * 60 * 1000L
            var gapsBelowThreshold = 0
            var flaresAfterShortGaps = 0

            // Analyze gaps between consecutive pills
            for (i in 1 until pills.size) {
                val gap = pills[i].timestamp - pills[i-1].timestamp
                if (gap < thresholdMs) {
                    gapsBelowThreshold++
                    // Check if a flare happened within 12h AFTER the second pill in this short gap
                    // This indicates the short gap didn't prevent the next flare
                    val hasFollowUpFlare = flares.any { flare ->
                        flare.timestamp > pills[i].timestamp && 
                        flare.timestamp <= (pills[i].timestamp + 12 * 60 * 60 * 1000L)
                    }
                    if (hasFollowUpFlare) flaresAfterShortGaps++
                }
            }

            if (gapsBelowThreshold >= 3) { // Avoid false insights when data is small
                val rate = (flaresAfterShortGaps.toDouble() / gapsBelowThreshold) * 100
                if (rate > 60.0) {
                    insights.add(GapInsight(
                        thresholdHours = hours,
                        flareRate = rate,
                        message = "Flare-ups increase when pill gap is below $hours hours"
                    ))
                }
            }
        }

        return insights.sortedBy { it.thresholdHours }
    }

    private fun calculateTriggerAssociations(
        sessions: List<TriggerSession>, 
        flares: List<FlareEvent>
    ): List<TriggerInsight> {
        if (sessions.isEmpty() || flares.isEmpty()) return emptyList()

        val triggerStats = mutableMapOf<String, Pair<Int, Int>>() // Name -> (Total Occurrences, Flare Follow-ups)

        sessions.forEach { session ->
            session.triggers.forEach { triggerName ->
                val baseTrigger = triggerName.split(":").first().trim()
                val (count, followUps) = triggerStats.getOrDefault(baseTrigger, 0 to 0)
                
                // Check if a flare happened within 24h AFTER this trigger session
                val hasFollowUpFlare = flares.any { flare ->
                    flare.timestamp > session.timestamp && 
                    flare.timestamp <= (session.timestamp + TWENTY_FOUR_HOURS_MS)
                }
                
                triggerStats[baseTrigger] = (count + 1) to (if (hasFollowUpFlare) followUps + 1 else followUps)
            }
        }

        val totalSessions = sessions.size.toDouble()
        return triggerStats.map { (name, stats) ->
            val strength = stats.second.toDouble() / stats.first
            // Confidence increases with more samples, normalized against total data availability
            val confidence = (stats.first.toDouble() / totalSessions).coerceAtMost(1.0)
            
            TriggerInsight(name, strength, confidence, stats.first)
        }.filter { 
            it.associationStrength > 0.6 && it.confidence > 0.4 
        }.sortedByDescending { it.associationStrength }
    }

    private fun calculateTotalDays(pills: List<PillEvent>, flares: List<FlareEvent>): Double {
        val allTimestamps = pills.map { it.timestamp } + flares.map { it.timestamp }
        if (allTimestamps.isEmpty()) return 0.0
        
        val minTs = allTimestamps.minOrNull() ?: return 0.0
        val maxTs = allTimestamps.maxOrNull() ?: return 0.0
        
        val diffMs = maxTs - minTs
        val days = diffMs.toDouble() / (1000 * 60 * 60 * 24)
        return if (days < 1.0) 1.0 else days
    }

    private fun calculateTopReasons(flareEvents: List<FlareEvent>): List<Pair<String, Double>> {
        if (flareEvents.isEmpty()) return listOf("CU Activity" to 100.0)

        val reasonCounts = mutableMapOf<String, Int>()
        flareEvents.forEach { event ->
            // Reasons format: "Category: Sub1, Sub2; Category2: Sub3"
            val cleanReasons = event.reasons.split("; ").flatMap { block ->
                if (block.contains(": ")) {
                    block.split(": ").getOrNull(1)?.split(", ") ?: emptyList()
                } else {
                    emptyList()
                }
            }.map { it.trim() }.filter { it.isNotEmpty() }

            if (cleanReasons.isEmpty()) {
                reasonCounts["Unknown"] = reasonCounts.getOrDefault("Unknown", 0) + 1
            } else {
                cleanReasons.forEach { reason ->
                    reasonCounts[reason] = reasonCounts.getOrDefault(reason, 0) + 1
                }
            }
        }

        val totalRecords = flareEvents.size
        return reasonCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to (it.value.toDouble() / totalRecords) * 100 }
    }

    /**
     * Maps the raw pills logs and xolair logs into a sorted list of PillEvents.
     */
    fun mapPillsLogToEvents(pillPrefs: SharedPreferences): List<PillEvent> {
        val events = mutableListOf<PillEvent>()
        
        // Handle standard pill logs
        val rawPillLogs = pillPrefs.getString("pill_logs", "") ?: ""
        if (rawPillLogs.isNotEmpty()) {
            rawPillLogs.split("\n").forEach { log ->
                if (log.isNotEmpty() && !log.startsWith("EFFECT:")) {
                    try {
                        // Extract timestamp: HH:mm:ss - dd/MM/yyyy
                        val tsPart = log.split(" - ").let { if (it.size >= 2) "${it[0]} - ${it[1]}" else log }
                        val date = logFormat.parse(tsPart)
                        if (date != null) {
                            val type = if (log.contains("Cortison Taken")) "Cortison" else "Antihistamine"
                            
                            val main = pillPrefs.getString("pill_main_reason_" + log, "") ?: ""
                            val sub = pillPrefs.getString("pill_sub_reason_" + log, "") ?: ""
                            val note = pillPrefs.getString("pill_other_note_" + log, "") ?: ""
                            
                            events.add(PillEvent(date.time, type, log, main, sub, note))
                        }
                    } catch (e: Exception) {
                        // Skip malformed logs
                    }
                }
            }
        }

        // Handle Xolair logs (stored in the same SharedPreferences but different key usually, 
        // let's verify if XolairLogFragment uses "PillLogs" or something else)
        val rawXolairLogs = pillPrefs.getString("xolair_logs", "") ?: ""
        if (rawXolairLogs.isNotEmpty()) {
            rawXolairLogs.split("\n").forEach { log ->
                if (log.isNotEmpty()) {
                    try {
                        // Xolair log format: HH:mm:ss - dd/MM/yyyy - Xolair dose 300 mg
                        val parts = log.split(" - ")
                        if (parts.size >= 2) {
                            val date = logFormat.parse("${parts[0]} - ${parts[1]}")
                            if (date != null) {
                                events.add(PillEvent(date.time, "Xolair", log))
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed logs
                    }
                }
            }
        }
        
        // Handle Cortison logs if they are stored in a separate key (checking LogFragment context)
        // Note: Based on displayFullLogs, Cortison is usually within pill_logs with "Cortison Taken"

        return events.sortedBy { it.timestamp }
    }

    /**
     * Maps the raw flare logs into a sorted list of FlareEvents.
     */
    fun mapFlareLogToEvents(flarePrefs: SharedPreferences): List<FlareEvent> {
        val events = mutableListOf<FlareEvent>()
        val rawLogs = flarePrefs.getString("flare_logs", "") ?: ""
        
        if (rawLogs.isNotEmpty()) {
            rawLogs.split("\n").forEach { log ->
                if (log.isNotEmpty() && !log.startsWith("CONTROLLED:") && !log.startsWith("CLEARED:")) {
                    try {
                        val date = logFormat.parse(log)
                        if (date != null) {
                            val key = log // The full original string is used as a key in SharedPreferences
                            val severity = flarePrefs.getString("flare_intensity_$key", "None") ?: "None"
                            val angio = flarePrefs.getString("flare_angio_$key", "") ?: ""
                            
                            val main = flarePrefs.getString("flare_main_reason_" + key, "") ?: ""
                            val sub = flarePrefs.getString("flare_sub_reason_" + key, "") ?: ""
                            val vit = flarePrefs.getString("flare_vitamin_" + key, "") ?: ""
                            var reasons = main
                            if (sub.isNotEmpty()) reasons += ": $sub"
                            if (sub.equals("Medication-related trigger") && vit.isNotEmpty()) reasons += " ($vit)"
                            
                            val notes = flarePrefs.getString("flare_other_note_" + key, "") ?: ""
                            if (notes.isNotEmpty()) reasons += "; Note: $notes"

                            events.add(FlareEvent(date.time, severity, angio, reasons, log))
                        }
                    } catch (e: Exception) {
                        // Skip malformed logs
                    }
                }
            }
        }
        
        return events.sortedBy { it.timestamp }
    }
}
