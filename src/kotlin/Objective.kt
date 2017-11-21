import halite.Planet
import halite.Position
import java.util.*

interface Intelligence {
    var kingdomCenter: Position

    fun getPlanet(id: Int): Planet?

    fun isOwn(planet: Planet): Boolean
}

sealed class Objective {
    var assignedShips = LinkedList<Int>()

    var score: Double = 0.0

    /** Set to false at the start of each turn. May be set to true during [Commander.update] */
    var valid: Boolean = false
        private set

    fun update(intel: Intelligence) {
        this.valid = this.onPreUpdate(intel) && this.isValid(intel)

        if (this.valid) {
            this.score = this.computeScore(intel)
        }
    }

    override fun toString() = "(score ${this.score})"

    abstract fun onPreUpdate(intel: Intelligence): Boolean

    abstract fun isValid(intel: Intelligence): Boolean

    abstract fun computeScore(intel: Intelligence): Double
}

abstract class PlanetObjective(var planet: Planet): Objective() {
    override fun onPreUpdate(intel: Intelligence): Boolean {
        val planet = intel.getPlanet(this.planet.id) ?: return false

        this.planet = planet

        return true
    }
}

class SettlePlanetObjective(planet: Planet): PlanetObjective(planet) {
    override fun isValid(intel: Intelligence): Boolean = true

    override fun computeScore(intel: Intelligence): Double {
        if (this.planet.isFull) return 0.0

        var distanceScore = Math.min(100.0, 1000 / intel.kingdomCenter.getDistanceTo(this.planet))
        var unsettledScore = when {
            planet.isOwned && intel.isOwn(planet) -> 80.0
            !planet.isOwned -> 100.0
            else -> 0.0
        }

        return distanceScore + unsettledScore
    }

    override fun toString() = "Settle(${this.planet.id}) ${super.toString()}"
}

class AttackPlanetObjective(planet: Planet) : PlanetObjective(planet) {
    override fun isValid(intel: Intelligence): Boolean = true

    override fun computeScore(intel: Intelligence): Double {
        return -1.0
    }
}