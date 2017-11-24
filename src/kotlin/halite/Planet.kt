package halite

import java.lang.Math.abs
import java.util.Collections

class Planet(owner: Int, id: Int, xPos: Double, yPos: Double, health: Int,
             radius: Double, val dockingSpots: Int, val currentProduction: Int,
             val remainingProduction: Int, dockedShips: List<Int>) : Entity(owner, id, xPos, yPos, health, radius) {
    val dockedShips: List<Int> = Collections.unmodifiableList(dockedShips)

    var intendedDockingAngles = ArrayList<Double>()

    val isFull: Boolean
        get() = dockedShips.size == dockingSpots

    val isOwned: Boolean
        get() = owner != -1

    val freeSpots: Int
        get() = dockingSpots - dockedShips.size

    val dockedRatio: Double
        get() = dockedShips.size.toDouble() / dockingSpots

    val freeRatio: Double
        get() = 1.0 - dockedRatio

    override fun registerAngleOfApproach(angle: Double): Double {
        val newAngle = getAngleOfApproach(angle)
        Log.log("Approach at $newAngle")
        intendedDockingAngles.add(newAngle)
        return newAngle
    }

    private fun getAngleOfApproach(initial: Double): Double {
        if (isAngleSafe(initial)) return initial

        for (i in 1..CORRECTIONS) {
            Log.log("Correction.")
            val positive = initial + i * CORRECTION_STEP
            if (isAngleSafe(positive)) return positive

            val negative = initial - i * CORRECTION_STEP
            if (isAngleSafe(negative)) return negative
        }

        // Give up
        return initial
    }

    private fun isAngleSafe(angle: Double): Boolean {
        for (a in this.intendedDockingAngles) {
            var diff = abs(a - angle)
            if (diff > Math.PI) diff = Math.PI - diff
            if (diff < MIN_ANGULAR_DISTANCE) return false
        }
        return true
    }

    override fun toString(): String {
        return "Planet[${super.toString()}, remainingProduction=$remainingProduction, currentProduction=$currentProduction, dockingSpots=$dockingSpots, dockedShips=$dockedShips]"
    }

    companion object {
        val MIN_ANGULAR_DISTANCE = 0.174533
        val CORRECTION_STEP = 0.2
        val CORRECTIONS = 3
    }
}
