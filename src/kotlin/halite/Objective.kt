package halite

import com.sun.org.apache.xml.internal.utils.IntVector
import ratioOf
import java.util.*

interface Intelligence {
    val self: Player
    var kingdomCenter: Position
    var freePlanets: Int
    var totalPlanets: Int
    var enemyPlanets: Int
    var unownedPlanets: Int
    var players: Int

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
        if (RESET_ASSIGNMENTS) {
            this.assignedShips.clear()
        } else {
            // Check existing ships
            this.assignedShips.retainAll { intel.shipExists(it) }

            // Set objective on each ship
            for (shipId in this.assignedShips) {
                intel.getShip(shipId)?.objective = this
            }
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

    abstract fun distancePenalty(ship: Ship): Double

    companion object {
        val RESET_ASSIGNMENTS = true
    }
}

abstract class PlanetObjective(var planet: Planet): Objective() {
    override fun onPreUpdate(intel: Intelligence): Boolean {

        val planet = intel.getPlanet(this.planet.id) ?: return false

        this.planet = planet

        return true
    }

    override fun distancePenalty(ship: Ship): Double {
        val distance = this.planet.getDistanceTo(ship)
        return distance * 2
    }
}

class SettlePlanetObjective(planet: Planet): PlanetObjective(planet) {
    override fun isValid(intel: Intelligence): Boolean = true

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        if (this.planet.isFull) return Pair(0.0, 0)

        // Higher threshold means less early boosting
        val freePlanetsThreshold = if (intel.players < 3) 0.6 else 0.8

        val settleBoost = if (intel.freePlanets.ratioOf(intel.totalPlanets) > freePlanetsThreshold) 400.0 else 0.0
        val distanceScore = Math.min(100.0, 700.0 / intel.kingdomCenter.getDistanceTo(this.planet))
        val unsettledScore: Pair<Double, Int> = when {
            planet.isOwned -> when {
                intel.isOwn(planet) -> Pair(planet.freeRatio * 200.0, planet.freeSpots)
                else -> Pair(80.0, planet.dockingSpots)
            }

            // Planet is free for grabs
            else -> Pair(100.0, planet.dockingSpots)
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
        val distanceScore = Math.min(30.0, 100.0 / intel.kingdomCenter.getDistanceTo(this.planet))
        val enemyShips = planet.dockedShips.count()
        val occupyScore = if (enemyShips > 0) (5 - planet.dockedShips.count()) * 10.0 else 0.0

        val assignment: Int = when {
            intel.self.ships.count() < 2 * (intel.unownedPlanets * 4) -> {
                val multiplier = if (aggressive) 5 else 2
                planet.dockedShips.count() * multiplier
            }

            // When we have lots of ships
            else -> {
                intel.self.ships.count() / intel.enemyPlanets
            }
        }

        return Pair(attackBoost + distanceScore + occupyScore, assignment)
    }

    override fun toString() = "Attack ${this.planet.id}, has ${this.planet.dockedShips.count()} ships ${super.toString()}"
}
