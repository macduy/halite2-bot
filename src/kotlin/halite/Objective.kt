package halite

import ratioOf
import java.util.*

interface Intelligence {
    var kingdomCenter: Position
    var freePlanets: Int
    var totalPlanets: Int

    fun getPlanet(id: Int): Planet?

    fun isOwn(planet: Planet): Boolean

    fun shipExists(id: Int): Boolean

    fun getShip(id: Int): Ship?
}

sealed class Objective {
    private var assignedShips: MutableList<Int> = mutableListOf()

    var allocation: Int = 0

    var score: Double = 0.0

    /** Set to false at the start of each turn. May be set to true during [Commander.update] */
    var valid: Boolean = false
        private set

    fun update(intel: Intelligence) {
        // Check existing ships
        this.assignedShips.retainAll { intel.shipExists(it) }

        // Set objective on each ship
        for (shipId in this.assignedShips) {
            intel.getShip(shipId)?.objective = this
        }

        this.valid = this.onPreUpdate(intel) && this.isValid(intel)

        if (this.valid) {
            val (score, allocation) = this.computeScoreAndAllocation(intel)
            this.score = score
            this.allocation = allocation

            if (allocation == 0) {
                for (shipId in this.assignedShips) {
                    intel.getShip(shipId)?.objective = null
                }
                this.assignedShips.clear()
            }
        }
    }

    fun isFree(): Boolean = this.assignedShips.count() < this.allocation

    open fun assign(ship: Ship) {
        this.assignedShips.add(ship.id)
        ship.objective = this
    }

    open fun unassign(ship: Ship) {
        if (this.assignedShips.remove(ship.id)) {
            ship.objective = null
        }
    }

    override fun toString() = "[$=$score, ${assignedShips.count()}/$allocation]"

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

        val settleBoost = if (intel.freePlanets.ratioOf(intel.totalPlanets) < 0.4) 400.0 else 0.0
        val distanceScore = Math.min(100.0, 1000.0 / intel.kingdomCenter.getDistanceTo(this.planet))
        val unsettledScore: Pair<Double, Int> = when {
            planet.isOwned && intel.isOwn(planet) ->  {
                val freeSpots = planet.dockingSpots - planet.dockedShips.count()
                Pair(freeSpots.toDouble() / planet.dockingSpots * 150.0, planet.dockingSpots)
            }
            !planet.isOwned -> Pair(100.0, planet.dockingSpots)
            else -> Pair(0.0, 0)
        }

        return Pair(settleBoost + distanceScore + unsettledScore.first, unsettledScore.second)
    }

    override fun toString() = "Settle(${this.planet.id}) ${super.toString()}"
}

class AttackPlanetObjective(planet: Planet) : PlanetObjective(planet) {

    override fun isValid(intel: Intelligence): Boolean = true

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        if (!this.planet.isOwned) return Pair(0.0, 0)

        if (intel.isOwn(planet)) return Pair(0.0, 0)

        val aggressive = (intel.freePlanets == 0)
        val attackBoost = if (aggressive) 500.0 else 0.0
        val distanceScore = Math.min(50.0, 500.0 / intel.kingdomCenter.getDistanceTo(this.planet))
        val enemyShips = planet.dockedShips.count()
        val occupyScore = if (enemyShips > 0) (5 - planet.dockedShips.count()) * 10.0 else 0.0

        val multiplier = if (aggressive) 5 else 2

        return Pair(attackBoost + distanceScore + occupyScore, planet.dockedShips.count() * multiplier)
    }

    override fun toString() = "Attack ${this.planet.id}, has ${this.planet.dockedShips.count()} ships ${super.toString()}"
}
