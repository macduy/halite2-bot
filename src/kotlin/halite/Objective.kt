package halite

import java.util.*

interface Intelligence {
    var kingdomCenter: Position

    fun getPlanet(id: Int): Planet?

    fun isOwn(planet: Planet): Boolean

    fun shipExists(id: Int): Boolean

    fun getShip(id: Int): Ship?
}

sealed class Objective {
    var assignedShips = LinkedList<Int>()

    var allocation: Int = 0

    var score: Double = 0.0

    /** Set to false at the start of each turn. May be set to true during [Commander.update] */
    var valid: Boolean = false
        private set

    fun update(intel: Intelligence) {
        // Check existing ships
        this.assignedShips.filter { intel.shipExists(it) }

        // Set objective on each ship
        for (shipId in this.assignedShips) {
            intel.getShip(shipId)?.objective = this
        }

        this.valid = this.onPreUpdate(intel) && this.isValid(intel)

        if (this.valid) {
            val (score, allocation) = this.computeScoreAndAllocation(intel)
            this.score = score
            this.allocation = allocation
        }
    }

    fun isFree(): Boolean = this.assignedShips.count() < this.allocation

    override fun toString() = "(score ${this.score})"

    abstract fun onPreUpdate(intel: Intelligence): Boolean

    abstract fun isValid(intel: Intelligence): Boolean

    abstract fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int>
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

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        if (this.planet.isFull) return Pair(0.0, 0)

        val distanceScore = Math.min(100.0, 1000 / intel.kingdomCenter.getDistanceTo(this.planet))
        val unsettledScore: Pair<Double, Int> = when {
            planet.isOwned && intel.isOwn(planet) ->  {
                val freeSpots = planet.dockingSpots - planet.dockedShips.count()
                Pair(freeSpots.toDouble() / planet.dockingSpots * 150.0, freeSpots)
            }
            !planet.isOwned -> Pair(100.0, planet.dockingSpots)
            else -> Pair(0.0, 0)
        }

        return Pair(distanceScore + unsettledScore.first, unsettledScore.second)
    }

    override fun toString() = "Settle(${this.planet.id}) ${super.toString()}"
}

class AttackPlanetObjective(planet: Planet) : PlanetObjective(planet) {
    override fun isValid(intel: Intelligence): Boolean = true

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        return Pair(-1.0, 0)
    }
}