import halite.*

class Executor(private val gameMap: GameMap) {
    private val moveList = mutableListOf<Move>()

    fun navigateToPlanet(ship: Ship, planet: Planet) {
        if (ship.canDock(planet)) {
            this.addMove(DockMove(ship, planet))
        } else {
            // Attempt fast navigation if there are less ships around
            if (gameMap.myPlayer.ships.size < 50) {
                this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(1f), 5))
            } else {
                this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(0.8f), 2))
            }
        }
    }

    fun navigateToAttackPlanet(ship: Ship, planet: Planet) {
        if (ship.withinDistance(planet, Constants.DOCK_RADIUS + 10.0)) {
            // Pick an enemy on the planet
            val enemyShip = gameMap.allShips.get(planet.dockedShips.first())
            if (enemyShip != null) {
//                this.addMove(Navigation(ship, enemyShip, gameMap).kamikazeEnemy())
                this.addMove(Navigation(ship, enemyShip, gameMap).navigateToShootEnemy())
            }
        } else {
            // Navigate to the planet for attack
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(1.0f), 5))
        }
    }

    private fun addMove(move: Move?) {
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
