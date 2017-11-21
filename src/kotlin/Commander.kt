import halite.*
import java.lang.Integer.min
import java.util.*

class Commander(private val gameMap: GameMap): Intelligence {
    private val self: Player by lazy { gameMap.myPlayer }

    private val ownedPlanets = LinkedList<Planet>()

    override var kingdomCenter = Position(0.0, 0.0)

    var objectives = LinkedList<Objective>()

    override fun getPlanet(id: Int): Planet? {
        return gameMap.allPlanets[id]
    }

    override fun isOwn(planet: Planet) = planet.owner == this.self.id

    init {
        // Set up objectives for each planet
        for (planet in this.gameMap.planets.values) {
            this.objectives.add(SettlePlanetObjective(planet))
            this.objectives.add(AttackPlanetObjective(planet))
        }
    }

    fun getNextObjective(): Objective {
        return this.objectives.first
    }

    fun getPlanetToExplore(ship: Ship): Planet? {
        var nearest: Planet? = null
        var minDist = Double.MAX_VALUE
        for (planet in this.gameMap.allPlanets.values) {
            if (planet.isOwned) {
                if (planet.owner != this.self.id) {
                    continue
                } else if (planet.isFull) {
                    continue
                }
            }

            val dist = planet.getDistanceTo(ship)
            if (dist < minDist) {
                minDist = dist
                nearest = planet
            }
        }
        return nearest
    }

    fun update() {
        // Find own planets
        this.updateOwnedPlanets()

        this.computeKingdomCenter()

        this.updateObjectives()
    }

    fun updateObjectives() {
        // Loop through objectives and update them
        for (objective in this.objectives) {
            objective.update(this)
        }

        // Remove any invalid objectives
        this.objectives.filter { it.valid }

        // Sort by score
        this.objectives.sortByDescending { it.score }
    }

    private fun computeKingdomCenter() {
        // If there are planets, use them.
        val planetCenter = this.getCenter(this.ownedPlanets)
        if (planetCenter != null) {
            this.kingdomCenter = planetCenter
            return
        }

        // Otherwise use ships
        var shipCenter = this.getCenter(this.self.ships.values)
        if (shipCenter != null) {
            this.kingdomCenter = shipCenter
            return
        }

        this.kingdomCenter = Position(0.0, 0.0)
    }

    private fun updateOwnedPlanets() {
        this.ownedPlanets.clear()
        this.gameMap.planets.values.filterTo(ownedPlanets) {
            it.isOwned && it.owner == this.self.id
        }
    }

    private fun getCenter(entities: Collection<Entity>): Position? {
        if (entities.isEmpty()) return null

        var x = 0.0
        var y = 0.0
        for (entity in entities) {
            x += entity.xPos
            y += entity.yPos
        }

        return Position(x / entities.count(), y / entities.count())
    }

    public fun logObjectives(count: Int = 5) {
        for (i in (0 until Integer.min(count, this.objectives.count()))) {
            Log.log(this.objectives[i].toString())
        }
    }
}