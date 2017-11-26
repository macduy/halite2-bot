package halite

import center
import com.sun.org.apache.xml.internal.utils.IntVector
import nearestTo
import ratioOf
import java.util.*

interface Intelligence {
    var kingdomCenter: Position
    var freePlanets: Int
    var totalPlanets: Int
    var enemyPlanets: Int
    var unownedPlanets: Int

    val self: Player
    val players: Int
    val turn: Int
    val enemyShips: List<Ship>

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
        this.assignedShips.clear()

        this.valid = this.onPreUpdate(intel)

        if (this.valid) {
            val (score, allocation) = this.computeScoreAndAllocation(intel)
            this.score = score
            this.allocation = allocation
        } else {
            this.score = 0.0
            this.allocation = 0
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

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        val nearbyEnemyShips = this.planet.nearbyEnemyShips.size
        if (intel.isOwn(planet) && planet.isFull && nearbyEnemyShips == 0) return Pair(0.0, 0)

        // Higher threshold means less early boosting
        val freePlanetsThreshold = if (intel.players < 3) 0.5 else 0.7

        val settleBoost = if (intel.freePlanets.ratioOf(intel.totalPlanets) > freePlanetsThreshold) 400.0 else 0.0
        val distanceScore = Math.min(100.0, 700.0 / intel.kingdomCenter.getDistanceTo(this.planet))
        val unsettledScore: Pair<Double, Int> = when {
            planet.isOwned -> when {
                intel.isOwn(planet) ->
                    Pair(planet.freeRatio * 150.0 + Math.sqrt(nearbyEnemyShips.toDouble()) * 200.0, planet.freeSpots + nearbyEnemyShips)
                else -> Pair(80.0, planet.dockingSpots)
            }

            // Planet is free for grabs
            else -> Pair(100.0, planet.dockingSpots)
        }

        return Pair(settleBoost + distanceScore + unsettledScore.first, unsettledScore.second + nearbyEnemyShips)
    }

    override fun toString() = "Settle(${this.planet.id}) ${super.toString()}"
}

class AttackPlanetObjective(planet: Planet) : PlanetObjective(planet) {

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

class EarlyAttackObjective: Objective() {
    var target: Ship? = null

    override fun onPreUpdate(intel: Intelligence): Boolean {
        // Only attempt this if we have exactly 3 ships
        if (intel.self.ships.size != 3) return false

        // Similarly, only attempt this in 2 player games
//        if (intel.players != 2) return false

        // If there are too many enemies, bail out
        if (intel.enemyShips.size > EARLY_ATTACK_ENEMY_LIMIT[intel.players]) return false

        // Bail if we are too far away. Maximum distance depends on how many turns we got left to execute it.
//        val target = intel.enemyShips.center() ?: return false
        val target = intel.enemyShips.nearestTo(intel.kingdomCenter) ?: return false
        Log.log("Nearest $target from ${intel.enemyShips.size}")

        val maxDistance = Math.max(15.0, ((EARLY_ATTACK_MAX_TURN[intel.players] - intel.turn) * AVERAGE_SPEED).toDouble())
        Log.log("EA: ${target.getDistanceTo(intel.kingdomCenter)} > $maxDistance")
        if (target.getDistanceTo(intel.kingdomCenter) > maxDistance) return false

        // Otherwise go for it!
        this.target = target
        return true
    }

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        return Pair(100000000.0, 3)
    }

    override fun distancePenalty(ship: Ship): Double = 0.0

    override fun toString() = "EarlyAttack ${super.toString()}"

    companion object {
        val EARLY_ATTACK_MAX_TURN = configFor(30, 20, 15)
        val EARLY_ATTACK_ENEMY_LIMIT = configFor(8, 10, 12)

        val AVERAGE_SPEED = 5

        fun configFor(two: Int, three: Int, four: Int): Array<Int> {
            return arrayOf(0, 0, two, three, four)
        }
    }
}
