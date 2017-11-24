package halite

import java.util.ArrayList
import java.util.TreeMap
import java.util.Collections

enum class EntitySelection {
    ALL, PLANETS_AND_OWN_SHIPS
}

open class GameMap(val width: Int, val height: Int, val myPlayerId: Int) {
    val players: MutableList<Player>
    val allPlayers: List<Player>
    val planets: MutableMap<Int, Planet>
    val allShips: MutableMap<Int, Ship>
    val enemyShips: MutableList<Ship>

    // used only during parsing to reduce memory allocations
    private val currentShips = ArrayList<Ship>()

    val myPlayer: Player
        get() = allPlayers[myPlayerId]

    val allPlanets: Map<Int, Planet>
        get() = planets

    init {
        players = ArrayList(Constants.MAX_PLAYERS)
        allPlayers = Collections.unmodifiableList(players)
        planets = TreeMap()
        allShips = TreeMap()
        enemyShips = mutableListOf()
    }

    fun objectsBetween(start: Position, target: Position, selection: EntitySelection = EntitySelection.ALL): ArrayList<Entity> {
        val entitiesFound = ArrayList<Entity>()

        when (selection) {
            EntitySelection.ALL -> {
                addEntitiesBetween(entitiesFound, start, target, allPlanets.values)
                addEntitiesBetween(entitiesFound, start, target, allShips.values)
            }
            EntitySelection.PLANETS_AND_OWN_SHIPS -> {
                addEntitiesBetween(entitiesFound, start, target, allPlanets.values)
                addEntitiesBetween(entitiesFound, start, target, myPlayer.ships.values)
            }
        }


        return entitiesFound
    }

    private fun addEntitiesBetween(entitiesFound: MutableList<Entity>,
                                   start: Position, target: Position,
                                   entitiesToCheck: Collection<Entity>) {

        entitiesToCheck.filterTo(entitiesFound) { it != start && it != target && Collision.segmentCircleIntersect(start, target, it, Constants.FORECAST_FUDGE_FACTOR) }
    }

    fun nearbyEntitiesByDistance(entity: Entity): Map<Double, Entity> {
        val entityByDistance = TreeMap<Double, Entity>()

        planets.values
                .filter { it != entity }
                .forEach { entityByDistance.put(entity.getDistanceTo(it), it) }

        allShips.values
                .filter { it != entity }
                .forEach { entityByDistance.put(entity.getDistanceTo(it), it) }

        return entityByDistance
    }

    open fun updateMap(mapMetadata: Metadata): GameMap {
        val numberOfPlayers = MetadataParser.parsePlayerNum(mapMetadata)

        players.clear()
        planets.clear()
        allShips.clear()

        // update players info
        (0 until numberOfPlayers).forEach {
            currentShips.clear()
            val currentPlayerShips = TreeMap<Int, Ship>()
            val playerId = MetadataParser.parsePlayerId(mapMetadata)

            val currentPlayer = Player(playerId, currentPlayerShips)
            MetadataParser.populateShipList(currentShips, playerId, mapMetadata)
            if (playerId != this.myPlayerId) {
                enemyShips.addAll(currentShips)
            }

            for (ship in currentShips) {
                currentPlayerShips.put(ship.id, ship)
            }
            allShips.putAll(currentPlayerShips)

            players.add(currentPlayer)
        }

        val numberOfPlanets = Integer.parseInt(mapMetadata.pop())

        (0 until numberOfPlanets).forEach { _ ->
            val dockedShips = ArrayList<Int>()
            val planet = MetadataParser.newPlanetFromMetadata(dockedShips, mapMetadata)
            planets.put(planet.id, planet)
        }

        if (!mapMetadata.isEmpty) {
            throw IllegalStateException("Failed to parse data from Halite game engine. Please contact maintainers.")
        }

        return this
    }
}
