import halite.*

class Executor(private val gameMap: GameMap, private val intel: Intelligence) {
    private val moveList = mutableListOf<Move>()

    fun navigateToPlanet(ship: Ship, planet: Planet) {
        if (ship.canDock(planet)) {
            if (!planet.isOwned || intel.isOwn(planet)) {
                val enemyShip = planet.nearbyEnemyShips.getOrNull(0)
                if (enemyShip != null) {
                    // Remove any enemies first
                    this.maybeAttackEnemy(ship, enemyShip)
                } else {
                    this.addMove(DockMove(ship, planet))
                }
            } else {
                // Planet belongs to enemy. Navigate to shoot nearest docked enemy
                this.maybeAttackEnemy(ship, gameMap.allShips[planet.dockedShips.first()])
            }
        } else {
            // Attempt fast navigation if there are less ships around
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(1f)))
        }
    }

    fun navigateToAttackPlanet(ship: Ship, planet: Planet) {
        if (ship.withinDistance(planet, Constants.DOCK_RADIUS + 20.0)) {
            // Pick an enemy on the planet
            this.maybeAttackEnemy(ship, gameMap.allShips[planet.dockedShips.first()])
        } else {
            // Navigate to the planet for attack
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(1.0f)))
        }
    }

    private fun maybeAttackEnemy(ship: Ship, enemyShip: Ship?) {
        if (enemyShip != null) {
            this.addMove(Navigation(ship, enemyShip, gameMap).navigateToShootEnemy())
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
