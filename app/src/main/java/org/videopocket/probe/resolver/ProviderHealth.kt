package org.videopocket.probe.resolver

import android.content.Context
import android.content.SharedPreferences

enum class ProviderHealth(val label: String, val arabicLabel: String) {
    AVAILABLE("Available", "متاح"),
    WORKING("Available", "متاح"),
    DEGRADED("Degraded", "أداء متأثر"),
    TEMPORARILY_BROKEN("Temporarily Unavailable", "غير متاح مؤقتاً"),
    TEMPORARILY_UNAVAILABLE("Temporarily Unavailable", "غير متاح مؤقتاً"),
    DISABLED("Disabled", "معطل"),
    UNKNOWN("Unknown / Not Tested", "غير مختبر")
}

data class ProviderMetrics(
    val providerId: String,
    var successCount: Int = 0,
    var fallbackSuccessCount: Int = 0,
    var failureCount: Int = 0,
    var consecutiveFailures: Int = 0,
    var contentSpecificErrorCount: Int = 0,
    var preventedLoginRequiredCount: Int = 0,
    var lastLatencyMs: Long = 0,
    var lastError: ResolverErrorType? = null,
    var lastStrategy: ResolutionStrategy? = null,
    var lastClassification: ResolutionClassification? = null,
    val strategyCounts: MutableMap<ResolutionStrategy, Int> = mutableMapOf()
)

object ProviderHealthManager {
    private val metricsMap = mutableMapOf<String, ProviderMetrics>()

    @Synchronized
    fun recordSuccess(
        providerId: String,
        latencyMs: Long,
        strategy: ResolutionStrategy = ResolutionStrategy.DEDICATED_EXTRACTOR,
        preventedLoginRequired: Boolean = false
    ) {
        val metrics = metricsMap.getOrPut(providerId) { ProviderMetrics(providerId) }
        metrics.successCount++
        metrics.consecutiveFailures = 0
        metrics.lastLatencyMs = latencyMs
        metrics.lastStrategy = strategy
        metrics.lastClassification = ResolutionClassification.PUBLIC_RESOLVED
        metrics.strategyCounts[strategy] = (metrics.strategyCounts[strategy] ?: 0) + 1
        if (preventedLoginRequired) {
            metrics.preventedLoginRequiredCount++
        }
    }

    @Synchronized
    fun recordFallbackSuccess(
        providerId: String,
        latencyMs: Long,
        strategy: ResolutionStrategy
    ) {
        val metrics = metricsMap.getOrPut(providerId) { ProviderMetrics(providerId) }
        metrics.successCount++
        metrics.fallbackSuccessCount++
        metrics.consecutiveFailures = 0
        metrics.lastLatencyMs = latencyMs
        metrics.lastStrategy = strategy
        metrics.lastClassification = ResolutionClassification.PUBLIC_RESOLVED
        metrics.strategyCounts[strategy] = (metrics.strategyCounts[strategy] ?: 0) + 1
    }

    @Synchronized
    fun recordPreventedLoginRequired(
        providerId: String,
        strategy: ResolutionStrategy,
        latencyMs: Long
    ) {
        recordFallbackSuccess(
            providerId = providerId,
            latencyMs = latencyMs,
            strategy = strategy
        )
        val metrics = metricsMap.getOrPut(providerId) { ProviderMetrics(providerId) }
        metrics.preventedLoginRequiredCount++
    }

    @Synchronized
    fun recordFailure(providerId: String, errorType: ResolverErrorType) {
        val metrics = metricsMap.getOrPut(providerId) { ProviderMetrics(providerId) }
        metrics.lastError = errorType
        metrics.lastClassification = errorType.toClassification()
        // Content-specific errors (such as individual video login requirements,
        // private accounts, or geo-restrictions) are property of that specific item,
        // NOT a defect of the provider itself.
        val isContentSpecific = when (errorType) {
            ResolverErrorType.LOGIN_REQUIRED,
            ResolverErrorType.PRIVATE_MEDIA,
            ResolverErrorType.GEO_RESTRICTED,
            ResolverErrorType.PUBLIC_MEDIA_UNAVAILABLE,
            ResolverErrorType.MEDIA_NOT_FOUND,
            ResolverErrorType.DRM_PROTECTED -> true
            else -> false
        }

        if (isContentSpecific) {
            metrics.contentSpecificErrorCount++
        } else {
            metrics.failureCount++
            metrics.consecutiveFailures++
        }
    }

    @Synchronized
    fun getHealth(providerId: String): ProviderHealth {
        if (!FeatureFlagsManager.isProviderEnabled(providerId)) {
            return ProviderHealth.DISABLED
        }
        val metrics = metricsMap[providerId] ?: return ProviderHealth.UNKNOWN
        val totalActionable = metrics.successCount + metrics.failureCount
        if (totalActionable == 0) {
            return ProviderHealth.UNKNOWN
        }
        val failRatio = metrics.failureCount.toFloat() / totalActionable.toFloat()
        return when {
            metrics.lastError == ResolverErrorType.RATE_LIMITED -> ProviderHealth.TEMPORARILY_BROKEN
            metrics.consecutiveFailures >= 3 -> ProviderHealth.TEMPORARILY_BROKEN
            metrics.fallbackSuccessCount > 0 && metrics.successCount == metrics.fallbackSuccessCount -> ProviderHealth.DEGRADED
            failRatio > 0.3f -> ProviderHealth.DEGRADED
            metrics.successCount > 0 -> ProviderHealth.AVAILABLE
            else -> ProviderHealth.TEMPORARILY_BROKEN
        }
    }

    @Synchronized
    fun getMetrics(providerId: String): ProviderMetrics {
        return metricsMap.getOrPut(providerId) { ProviderMetrics(providerId) }
    }
}

object FeatureFlagsManager {
    private const val PREFS_NAME = "videopocket_feature_flags"
    private var prefs: SharedPreferences? = null
    private val memoryOverrides = mutableMapOf<String, Boolean>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isProviderEnabled(providerId: String): Boolean {
        memoryOverrides[providerId]?.let { return it }
        val p = prefs ?: return true
        return p.getBoolean("provider_enabled_$providerId", true)
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        memoryOverrides[providerId] = enabled
        prefs?.edit()?.putBoolean("provider_enabled_$providerId", enabled)?.apply()
    }
}
