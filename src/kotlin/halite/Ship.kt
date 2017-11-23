package halite

enum class DockingStatus { Undocked,
    Docking,
    Docked,
    Undocking,}

class Ship(owner: Int, id: Int, xPos: Double, yPos: Double,
           health: Int, val dockingStatus: DockingStatus, val dockedPlanet: Int,
           val dockingProgress: Int, val weaponCooldown: Int) : Entity(owner, id, xPos, yPos, health, Constants.SHIP_RADIUS) {

    var objective: Objective? = null

    fun canDock(planet: Planet): Boolean {
        return this.withinDistance(planet, Constants.DOCK_RADIUS)
    }

    fun withinDistance(planet: Planet, distance: Double): Boolean {
        return getDistanceTo(planet) <= Constants.SHIP_RADIUS + distance + planet.radius
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
