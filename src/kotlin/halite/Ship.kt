package halite

enum class DockingStatus { Undocked,
    Docking,
    Docked,
    Undocking,}

class Ship(owner: Int, id: Int, xPos: Double, yPos: Double,
           health: Int, val dockingStatus: DockingStatus, val dockedPlanet: Int,
           val dockingProgress: Int, val weaponCooldown: Int) : Entity(owner, id, xPos, yPos, health, Constants.SHIP_RADIUS) {

    var objective: Objective? = null

    var futurePosition: Position? = null

    fun canDock(planet: Planet): Boolean {
        return this.withinDistance(planet, Constants.DOCK_RADIUS)
    }

    fun withinDistance(planet: Planet, distance: Double): Boolean {
        return getDistanceTo(planet) <= Constants.SHIP_RADIUS + distance + planet.radius
    }

    // Returns a series of positions for this ship given a thrust move.
    fun futureShip(move: ThrustMove): List<FutureShip> {
        return (1..move.thrust).map {
            val dx = Math.cos(Math.toRadians(move.angle.toDouble())) * it
            val dy = Math.sin(Math.toRadians(move.angle.toDouble())) * it
            FutureShip(owner, xPos + dx, yPos + dy)
        }
    }

    // Returns future final position of ship given angle and thrust.
    fun futurePosition(angleRad: Double, thrust: Int): Position {
        val dx = Math.cos(angleRad) * thrust
        val dy = Math.sin(angleRad) * thrust
        return Position(xPos + dx, yPos + dy)
    }

    override fun toString(): String {
        return "Ship[" +
                super.toString() +
                ", dockingStatus=" + dockingStatus +
                ", dockedPlanet=" + dockedPlanet +
                ", dockingProgress=" + dockingProgress +
                ", weaponCooldown=" + weaponCooldown +
                "]"
    }
}
