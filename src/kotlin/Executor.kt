import halite.*

class Executor(private val gameMap: GameMap, private val intel: Intelligence) {
    private val moveList = mutableListOf<Move>()

    fun navigateToPlanet(ship: Ship, planet: Planet) {
        if (ship.canDock(planet)) {
            if (!planet.isOwned || intel.isOwn(planet)) {
                this.addMove(DockMove(ship, planet))
            } else {
                // Navigate to shoot nearest enemy
                val enemyShip = gameMap.allShips[planet.dockedShips.first()]
                if (enemyShip != null) {
                    this.addMove(Navigation(ship, enemyShip, gameMap).navigateToShootEnemy())
                }
            }
        } else {
            // Attempt fast navigation if there are less ships around
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(1f)))
        }
    }

    fun navigateToAttackPlanet(ship: Ship, planet: Planet) {
        if (ship.withinDistance(planet, Constants.DOCK_RADIUS + 20.0)) {
            // Pick an enemy on the planet
            val enemyShip = gameMap.allShips[planet.dockedShips.first()]
            if (enemyShip != null) {
                this.addMove(Navigation(ship, enemyShip, gameMap).navigateToShootEnemy())
            }
        } else {
            // Navigate to the planet for attack
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(1.0f)))
        }
    }

    private fun addMove(move: Move?) {
        if (move is ThrustMove) {
            gameMap.futureShips.addAll(move.ship.futureShip(move))
        }
        if (move != null) {
            moveList.add(move)
        }
    }

    fun execute() {
        Networking.sendMoves(this.moveList)
        this.moveList.clear()
    }

    fun speed(ratio: Float): Int {
        return Math.round(ratio * Constants.MAX_SPEED.toFloat())
    }
}
