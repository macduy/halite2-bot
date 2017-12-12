package halite

import center
import com.sun.org.apache.xml.internal.utils.IntVector
import nearestTo
import ratioOf
import java.lang.Math.sqrt
import java.util.*

interface Intelligence {
    var kingdomCenter: Position
    var freePlanets: Int
    var totalPlanets: Int
    var enemyPlanets: Int
    var unownedPlanets: Int

    val gameMap: GameMap
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
        return Math.pow(distance / 5.0, 2.0)
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
        val defenseMultiplier = 6.0 / intel.self.ships.size + 1.0
        val unsettledScore: Pair<Double, Int> = when {
            planet.isOwned -> when {
                intel.isOwn(planet) -> {
                    val score = planet.freeRatio * 150.0 + Math.sqrt(nearbyEnemyShips.toDouble()) * 350.0 * defenseMultiplier
                    val assignment = planet.freeSpots + nearbyEnemyShips * 2
                    Pair(score, assignment)
                }

                else -> Pair(80.0, planet.dockingSpots + nearbyEnemyShips)
            }

            // Planet is free for grabs
            else -> Pair(100.0, planet.dockingSpots + nearbyEnemyShips)
        }

        return Pair(settleBoost + distanceScore + unsettledScore.first, unsettledScore.second)
    }

    override fun toString() = "Settle(${this.planet.id}) ${super.toString()}"
}

class AttackPlanetObjective(planet: Planet) : PlanetObjective(planet) {

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        if (!this.planet.isOwned) return Pair(0.0, 0)

        if (intel.isOwn(planet)) return Pair(0.0, 0)

        val nearbyEnemies = planet.nearbyEnemyShips.size
        val totalEnemyShips = planet.dockedShips.count() + nearbyEnemies
        val aggressive = (intel.freePlanets == 0)

        val attackBoost = if (aggressive) 500.0 else 0.0
        val distanceScore = Math.min(30.0, 100.0 / intel.kingdomCenter.getDistanceTo(this.planet))
        val occupyScore = if (totalEnemyShips > 0) (1 - planet.dockedRatio) * 80.0 else 0.0
        val enemyScore = if (aggressive) sqrt(nearbyEnemies.toDouble()) * 100.0 else sqrt(nearbyEnemies.toDouble()) * -10.0

        // Attack strong enemies first
        val enemyStrengthScore = this.enemyStrengthScore(intel)

        val assignment: Int = when {
            intel.self.ships.count() < 2 * (intel.unownedPlanets * 4) -> {
                val multiplier = if (aggressive) 4 else 2
                (planet.dockedShips.count() + nearbyEnemies) * multiplier
            }

            // When we have lots of ships
            else -> {
                intel.self.ships.count() / intel.enemyPlanets + nearbyEnemies
            }
        }

        return Pair(attackBoost + distanceScore + occupyScore + enemyScore + enemyStrengthScore, assignment)
    }

    private fun enemyStrengthScore(intel: Intelligence): Double {
        if (intel.players == 2) return 0.0

        val enemyOwner = intel.gameMap.getPlayer(this.planet.owner) ?: return 0.0

        return 40.0 * (Math.sqrt(enemyOwner.ships.size.toDouble()) - 1.0)
    }

    override fun toString() = "Attack ${this.planet.id}, has ${this.planet.dockedShips.count()} ships ${super.toString()}"
}

class TurtleShipObjective(val position: Position): Objective() {
    override fun onPreUpdate(intel: Intelligence): Boolean {
        return ((intel.gameMap.turn > 50) && (intel.self.ships.size <= 5)) || ((intel.gameMap.turn > 100) && (intel.self.ships.size <= 8))
    }

    override fun distancePenalty(ship: Ship): Double {
        val distance = ship.getDistanceTo(this.position)
        return distance * distance
    }

    override fun computeScoreAndAllocation(intel: Intelligence): Pair<Double, Int> {
        return 100000000.0 to 2
    }
}

class EarlyAttackObjective: Objective() {
    var target: Ship? = null

    override fun onPreUpdate(intel: Intelligence): Boolean {
        // Only attempt this if we have exactly 3 ships
        if (intel.self.ships.size != 3) return false

        // If there are too many enemies, bail out
        if (intel.enemyShips.size > EARLY_ATTACK_ENEMY_LIMIT[intel.players]) return false

        // Find nearest docked enemy or just enemy
        val target = intel.enemyShips.filter { it.dockingStatus != DockingStatus.Undocked }.nearestTo(intel.kingdomCenter)
                ?: intel.enemyShips.nearestTo(intel.kingdomCenter)
                ?: return false

        // Bail if we are too far away. Maximum distance depends on how many turns we got left to execute it.
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
        val EARLY_ATTACK_MAX_TURN = configFor(26, 20, 18)
        val EARLY_ATTACK_ENEMY_LIMIT = configFor(6, 10, 12)

        val AVERAGE_SPEED = 5

        private fun configFor(two: Int, three: Int, four: Int): Array<Int> = arrayOf(0, 0, two, three, four)
    }
}
