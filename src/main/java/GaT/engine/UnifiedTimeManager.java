package GaT.engine;

import GaT.model.ConsolidatedSearchConfig;

/**
 * UNIFIED TIME MANAGER - Single source of truth for all time decisions
 *
 * ELIMINATES:
 * - Multiple time control systems
 * - Scattered emergency/panic time logic
 * - Inconsistent time allocation
 *
 * PROVIDES:
 * - Single time authority
 * - Smart time allocation
 * - Emergency detection
 * - Iterative deepening time control
 */
public class UnifiedTimeManager {

    // === TIME STATE ===
    private long totalTimeMs;
    private long searchStartTime;
    private long emergencyTimeMs;
    private long comfortTimeMs;
    private long normalTimeMs;

    // === ALLOCATION RATIOS ===
    private static final double EMERGENCY_RATIO = 0.10;  // 10% for emergency
    private static final double COMFORT_RATIO = 0.30;    // 30% for comfortable search
    private static final double NORMAL_RATIO = 0.60;     // 60% for normal search

    // === TIME STATES ===
    public enum TimeState {
        NORMAL,      // Plenty of time
        PRESSURE,    // Getting tight
        EMERGENCY,   // Very little time
        PANIC        // Almost out of time
    }

    // === INITIALIZATION ===

    public void setTimeLimit(long timeMs) {
        this.totalTimeMs = Math.max(timeMs, ConsolidatedSearchConfig.PANIC_TIME_MS);
        allocateTime();
    }

    public void startSearch() {
        this.searchStartTime = System.currentTimeMillis();
    }

    private void allocateTime() {
        // Emergency reserve (for forced moves)
        emergencyTimeMs = Math.max(
                (long)(totalTimeMs * EMERGENCY_RATIO),
                ConsolidatedSearchConfig.EMERGENCY_TIME_MS
        );

        // Comfort zone (for quality moves)
        comfortTimeMs = (long)(totalTimeMs * COMFORT_RATIO);

        // Normal search time
        normalTimeMs = totalTimeMs - emergencyTimeMs - comfortTimeMs;

        // Ensure minimum times
        if (emergencyTimeMs > totalTimeMs / 2) {
            emergencyTimeMs = totalTimeMs / 2;
            comfortTimeMs = totalTimeMs / 4;
            normalTimeMs = totalTimeMs - emergencyTimeMs - comfortTimeMs;
        }
    }

    // === TIME QUERIES ===

    public long getElapsedTime() {
        return searchStartTime > 0 ? System.currentTimeMillis() - searchStartTime : 0;
    }

    public long getRemainingTime() {
        return Math.max(0, totalTimeMs - getElapsedTime());
    }

    public TimeState getCurrentTimeState() {
        long elapsed = getElapsedTime();

        if (elapsed >= totalTimeMs - ConsolidatedSearchConfig.PANIC_TIME_MS) {
            return TimeState.PANIC;
        } else if (elapsed >= totalTimeMs - emergencyTimeMs) {
            return TimeState.EMERGENCY;
        } else if (elapsed >= normalTimeMs) {
            return TimeState.PRESSURE;
        } else {
            return TimeState.NORMAL;
        }
    }

    public boolean isTimeUp() {
        return getElapsedTime() >= totalTimeMs;
    }

    public boolean shouldAbortImmediately() {
        return getCurrentTimeState() == TimeState.PANIC;
    }

    // === ITERATIVE DEEPENING CONTROL ===

    /**
     * Check if we have time for another iteration
     */
    public boolean hasTimeForIteration(int currentDepth, long averageIterationTime) {
        long elapsed = getElapsedTime();
        long remaining = getRemainingTime();

        // No time if we're in emergency mode
        if (getCurrentTimeState() == TimeState.EMERGENCY) {
            return false;
        }

        // Estimate time for next iteration (exponential growth)
        long estimatedNextIteration = estimateNextIterationTime(currentDepth, averageIterationTime);

        // Need at least 1.5x estimated time to start next iteration
        return remaining > estimatedNextIteration * 3 / 2;
    }

    private long estimateNextIterationTime(int currentDepth, long averageTime) {
        if (averageTime <= 0) {
            // No history, use simple heuristic
            return ConsolidatedSearchConfig.EMERGENCY_TIME_MS;
        }

        // Next iteration typically takes 3-5x longer than current
        double growthFactor = currentDepth < 8 ? 4.0 : 3.0;
        return (long)(averageTime * growthFactor);
    }

    /**
     * Time allocation for specific depth
     */
    public long getTimeForDepth(int depth) {
        TimeState state = getCurrentTimeState();

        switch (state) {
            case NORMAL:
                return depth <= 6 ? normalTimeMs / 3 : normalTimeMs / 2;
            case PRESSURE:
                return comfortTimeMs / 2;
            case EMERGENCY:
                return emergencyTimeMs;
            case PANIC:
                return Math.min(emergencyTimeMs / 4, getRemainingTime());
            default:
                return emergencyTimeMs;
        }
    }

    // === ADAPTIVE TIME MANAGEMENT ===

    /**
     * Determine optimal search strategy based on time
     */
    public ConsolidatedSearchConfig.Strategy getOptimalStrategy() {
        TimeState state = getCurrentTimeState();

        switch (state) {
            case NORMAL:
            case PRESSURE:
                return ConsolidatedSearchConfig.Strategy.PVS_QUIESCENCE; // Best quality
            case EMERGENCY:
                return ConsolidatedSearchConfig.Strategy.PVS; // Fast but good
            case PANIC:
                return ConsolidatedSearchConfig.Strategy.ALPHA_BETA; // Fastest
            default:
                return ConsolidatedSearchConfig.DEFAULT_STRATEGY;
        }
    }

    /**
     * Get maximum search depth based on time
     */
    public int getMaxDepthForTime() {
        TimeState state = getCurrentTimeState();

        switch (state) {
            case NORMAL:
                return ConsolidatedSearchConfig.MAX_DEPTH;
            case PRESSURE:
                return ConsolidatedSearchConfig.MAX_DEPTH - 2;
            case EMERGENCY:
                return Math.min(8, ConsolidatedSearchConfig.MAX_DEPTH);
            case PANIC:
                return Math.min(4, ConsolidatedSearchConfig.MAX_DEPTH);
            default:
                return 4;
        }
    }

    // === PERFORMANCE TRACKING ===

    /**
     * Calculate time usage efficiency
     */
    public double getTimeUsageEfficiency() {
        if (totalTimeMs <= 0) return 0.0;

        long elapsed = getElapsedTime();
        double usage = (double) elapsed / totalTimeMs;

        // Efficient usage is 70-90% of allocated time
        if (usage < 0.3) return usage / 0.7; // Underutilization penalty
        if (usage > 0.95) return Math.max(0.1, 2.0 - usage); // Overtime penalty

        return 1.0; // Optimal usage
    }

    // === EMERGENCY PROTOCOLS ===

    /**
     * Force immediate move selection
     */
    public boolean mustMoveImmediately() {
        return getRemainingTime() <= ConsolidatedSearchConfig.PANIC_TIME_MS;
    }

    /**
     * Check if we need to reduce search scope
     */
    public boolean shouldReduceSearchScope() {
        return getCurrentTimeState().ordinal() >= TimeState.EMERGENCY.ordinal();
    }

    // === STATUS REPORTING ===

    public String getTimeStatus() {
        long elapsed = getElapsedTime();
        long remaining = getRemainingTime();
        TimeState state = getCurrentTimeState();

        return String.format("Time: %dms/%dms (%s) - %s",
                elapsed, totalTimeMs, formatTimePercent(elapsed, totalTimeMs), state);
    }

    public String getDetailedTimeBreakdown() {
        return String.format(
                "Time Allocation: Total=%dms | Normal=%dms | Comfort=%dms | Emergency=%dms | State=%s",
                totalTimeMs, normalTimeMs, comfortTimeMs, emergencyTimeMs, getCurrentTimeState()
        );
    }

    private String formatTimePercent(long used, long total) {
        if (total <= 0) return "0%";
        double percent = (double) used / total * 100;
        return String.format("%.1f%%", percent);
    }

    // === UTILITY METHODS ===

    /**
     * Get time multiplier for move ordering (more time = better ordering)
     */
    public double getMoveOrderingTimeMultiplier() {
        TimeState state = getCurrentTimeState();

        switch (state) {
            case NORMAL: return 1.0;     // Full ordering
            case PRESSURE: return 0.7;   // Reduced ordering
            case EMERGENCY: return 0.3;  // Minimal ordering
            case PANIC: return 0.1;      // Almost no ordering
            default: return 0.5;
        }
    }

    /**
     * Get time multiplier for transposition table operations
     */
    public double getTTTimeMultiplier() {
        TimeState state = getCurrentTimeState();

        switch (state) {
            case PANIC: return 0.5;      // Reduce TT overhead in panic
            default: return 1.0;        // Normal TT usage
        }
    }

    /**
     * Check if we should use aspiration windows (time-dependent)
     */
    public boolean shouldUseAspirationWindows() {
        return getCurrentTimeState().ordinal() < TimeState.EMERGENCY.ordinal();
    }

    // === TESTING AND DEBUGGING ===

    public void simulateTimeElapse(long ms) {
        // For testing - simulate time passage
        searchStartTime -= ms;
    }

    public void reset() {
        totalTimeMs = 0;
        searchStartTime = 0;
        emergencyTimeMs = 0;
        comfortTimeMs = 0;
        normalTimeMs = 0;
    }
}