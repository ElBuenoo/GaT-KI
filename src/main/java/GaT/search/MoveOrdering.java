package GaT.search;

/**
 * MOVE ORDERING - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Removed all hardcoded priorities and bonuses
 * ✅ Killer move configuration from SearchConfig
 * ✅ History heuristic configuration from SearchConfig
 * ✅ Centralized parameter control for easy tuning
 */
public class MoveOrdering extends FastMoveOrdering{
    public MoveOrdering() {
        super();
        System.out.println("🚀 Using optimized FastMoveOrdering");
    }
}