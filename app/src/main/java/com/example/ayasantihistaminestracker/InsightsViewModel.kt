package com.example.ayasantihistaminestracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*

/**
 * Represents a pill intake record from the repository.
 */
data class PillLog(
    val timestamp: Long, 
    val type: String,
    val mainReason: String = "",
    val subReason: String = "",
    val notes: String = ""
)

/**
 * Represents a flare-up record from the repository.
 */
data class FlareLog(val timestamp: Long, val severity: String, val angioedema: String, val reasons: String)

/**
 * Represents the user's profile and cycle history.
 */
data class UserProfile(
    val name: String, 
    val age: String, 
    val sex: String, 
    val cycles: List<CycleEvent>,
    val isOnTreatment: Boolean
)

/**
 * Interface for the Pills data source.
 */
interface PillsRepository {
    val pillLogs: Flow<List<PillLog>>
}

/**
 * Interface for the Flare data source.
 */
interface FlareRepository {
    val flareLogs: Flow<List<FlareLog>>
}

/**
 * Interface for the Profile data source.
 */
interface ProfileRepository {
    val profile: Flow<UserProfile>
}

/**
 * Date filtering options for the Insights dashboard.
 */
sealed class DateFilter {
    object Last30Days : DateFilter()
    data class Custom(val start: Long, val end: Long) : DateFilter()
}

/**
 * Detailed UI state for the Insights dashboard.
 * Designed to be directly consumed by Jetpack Compose.
 */
data class InsightsUiState(
    val name: String = "",
    val age: String = "",
    val sex: String = "",
    val isOnTreatment: Boolean = false,
    val activeFilter: String = "",

    val stabilityScore: Int = 0,
    val stabilityTrend: String = "",

    // Medication Metrics
    val totalAntihistamines: Int = 0,
    val totalCortisone: Int = 0,
    val shortestPillGap: String = "0",
    val longestPillFreeStreak: String = "0",
    val mostCommonMedicationTime: String = "N/A",
    val numberMedicationDays: Int = 0,
    val daysWithRepeatedMedicationUse: Int = 0,
    val unstableDependencyPeriods: List<String> = emptyList(),

    // Flare Metrics
    val totalFlareUps: Int = 0,
    val longestFlareFreeStreak: String = "0",
    val repeatedFlareDays: Int = 0,
    val mostActiveFlarePeriod: String = "N/A",
    val mostSevereFlarePeriod: String = "N/A",
    val flareDistribution: List<Pair<String, Int>> = emptyList(),
    val instabilitySpikes: List<String> = emptyList(),

    // Trigger Metrics
    val topTriggers: List<Pair<String, Int>> = emptyList(),
    val triggerCombinations: List<Pair<String, Int>> = emptyList(),
    val topShortGapReasons: List<Pair<String, Int>> = emptyList(),

    val topFlareReasons: List<Pair<String, Int>> = emptyList(),
    val angioedemaPercent: Int = 0,

    val smartInsights: List<String> = emptyList(),

    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
    val error: String? = null
) {
    companion object {
        fun empty() = InsightsUiState(isEmpty = true)
    }
}

/**
 * InsightsViewModel connects modern Flow-based repositories to the InsightsEngine
 * to provide a reactive stream of clinical insights for the Jetpack Compose UI.
 */
class InsightsViewModel(
    private val pillsRepository: PillsRepository,
    private val flareRepository: FlareRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _dateFilter = MutableStateFlow<DateFilter>(DateFilter.Last30Days)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    /**
     * Reactive UI State that re-computes insights whenever repositories update 
     * or the date filter changes.
     * 
     * Uses mapLatest and Dispatchers.Default to ensure heavy calculations 
     * do not block the UI thread and can be cancelled if new data arrives.
     */
    val uiState: StateFlow<InsightsUiState> = combine(
        pillsRepository.pillLogs,
        flareRepository.flareLogs,
        profileRepository.profile,
        _dateFilter
    ) { pills, flares, profile, filter ->
        // Bundle combined data into a temporary tuple for mapLatest
        DataPackage(pills, flares, profile, filter)
    }.mapLatest { (pills, flares, profile, filter) ->
        withContext(Dispatchers.Default) {
            try {
                // 1. Filter raw data based on selected time range
                val filteredPills = filterData(pills, filter) { it.timestamp }
                val filteredFlares = filterData(flares, filter) { it.timestamp }

                // 2. Map to engine models
                val pillEvents = filteredPills.map { PillEvent(it.timestamp, it.type, "", it.mainReason, it.subReason, it.notes) }
                val flareEvents = filteredFlares.map { FlareEvent(it.timestamp, it.severity, it.angioedema, it.reasons, "") }

                // 3. Run analysis via InsightsEngine
                val metrics = InsightsEngine.calculateMetrics(
                    pillEvents = pillEvents,
                    flareEvents = flareEvents,
                    cycleEvents = profile.cycles
                )

                // 4. Generate final UI report
                val report = InsightsEngine.createReport(metrics)

                InsightsUiState(
                    name = profile.name,
                    age = profile.age,
                    sex = profile.sex,
                    isOnTreatment = profile.isOnTreatment,
                    activeFilter = filter.toString(),
                    stabilityScore = report.stabilityScore,
                    stabilityTrend = "Stable",
                    
                    totalAntihistamines = report.totalAntihistamines,
                    totalCortisone = report.totalCortisone,
                    shortestPillGap = formatValue(report.shortestPillGapHours),
                    longestPillFreeStreak = formatValue(report.longestPillFreeStreakDays),
                    mostCommonMedicationTime = report.mostCommonMedicationTime,
                    numberMedicationDays = report.numberMedicationDays,
                    daysWithRepeatedMedicationUse = report.daysWithRepeatedMedicationUse,
                    unstableDependencyPeriods = report.unstableDependencyPeriods,

                    totalFlareUps = report.totalFlareUps,
                    longestFlareFreeStreak = formatValue(report.longestFlareFreeStreakDays),
                    repeatedFlareDays = report.repeatedFlareDays,
                    mostActiveFlarePeriod = report.mostActiveFlarePeriod,
                    mostSevereFlarePeriod = report.mostSevereFlarePeriod,
                    flareDistribution = report.flareDistribution,
                    instabilitySpikes = report.instabilitySpikes,

                    topTriggers = report.topTriggers,
                    triggerCombinations = report.triggerCombinations,
                    topShortGapReasons = report.topShortGapReasons,

                    topFlareReasons = report.topReasons.map { it.first to it.second.toInt() },
                    angioedemaPercent = report.angioedemaRate.toInt(),
                    smartInsights = report.smartInsights,
                    isLoading = false,
                    isEmpty = pillEvents.isEmpty() && flareEvents.isEmpty()
                )
            } catch (e: Exception) {
                InsightsUiState(error = "Insight calculation failed: ${e.message}", isLoading = false)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InsightsUiState(isLoading = true)
    )

    private fun formatValue(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    private data class DataPackage(
        val pills: List<PillLog>,
        val flares: List<FlareLog>,
        val profile: UserProfile,
        val filter: DateFilter
    )

    /**
     * Updates the current view filter.
     */
    fun updateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    private fun <T> filterData(list: List<T>, filter: DateFilter, timestampProvider: (T) -> Long): List<T> {
        return when (filter) {
            is DateFilter.Last30Days -> {
                val cutoff = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
                list.filter { timestampProvider(it) >= cutoff }
            }
            is DateFilter.Custom -> {
                list.filter { timestampProvider(it) in filter.start..filter.end }
            }
        }
    }
}
