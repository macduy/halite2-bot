import halite.*

class Executor(private val gameMap: GameMap, private val intel: Intelligence) {
    private val moveList = mutableListOf<Move>()

    fun direct(ship: Ship) {
        val objective = ship.objective

        when (objective) {
            is SettlePlanetObjective -> this.navigateToPlanet(ship, objective.planet)
            is AttackPlanetObjective -> this.navigateToAttackPlanet(ship, objective.planet)
            is EarlyAttackObjective -> this.earlyAttackShips(ship, objective.target)
            is TurtleShipObjective -> this.turtleShip(ship, objective.position)
            null -> { }
        }
    }

    fun execute() {
        Networking.sendMoves(this.moveList)
        this.moveList.clear()
    }

    private fun navigateToPlanet(ship: Ship, planet: Planet) {
        if (ship.canDock(planet)) {
            if (!planet.isOwned || intel.isOwn(planet)) {
                val enemyShip = planet.nearbyEnemyShips.nearestTo(ship)
                if (enemyShip != null) {
                    // Remove any nearby enemies first
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

    private fun navigateToAttackPlanet(ship: Ship, planet: Planet) {
        if (ship.withinDistance(planet, Constants.DOCK_RADIUS + 20.0)) {
            // Pick an enemy on the planet

            // Otherwise pick nearest undocked enemy
            val undockedEnemy = planet.nearbyEnemyShips.nearestTo(ship)
            if (!planet.dockedShips.isEmpty()) {
                this.maybeAttackEnemy(ship, gameMap.allShips[planet.dockedShips.first()])
            } else {
                if (undockedEnemy != null) {
                    if (undockedEnemy.health > ship.health * 2) {
                        // We are weaker, kamikaze
                        Log.log("Kamikaze")
                        this.addMove(Navigation(ship, undockedEnemy, gameMap).kamikazeEnemy())
                    } else {
                        // Fight normally
                        Log.log("Fight ship")
                        this.addMove(Navigation(ship, undockedEnemy, gameMap).navigateToShootEnemy())
                    }
                }
            }
        } else {
            // Navigate to the planet for attack
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(speed(1.0f)))
        }
    }

    private fun earlyAttackShips(ship: Ship, target: Ship?) {
        this.maybeAttackEnemy(ship, target, EntitySelection.ALL)
    }

    private fun turtleShip(ship: Ship, position: Position) {
        this.addMove(Navigation(ship, position.asFakeEntity(), gameMap).navigateToDock())
    }

    private fun maybeAttackEnemy(ship: Ship, enemyShip: Ship?, entitySelection: EntitySelection = EntitySelection.ALL) {
        if (enemyShip != null) {
            this.addMove(Navigation(ship, enemyShip, gameMap).navigateToShootEnemy(entitySelection))
        }
    }

    private fun addMove(move: Move?) {
        if (move is ThrustMove) {
//            Log.log("Move ${move.ship.id} (angle=${move.angle})")

            val futureShips = move.ship.futureShip(move)
            gameMap.futureShips.addAll(futureShips)

            if (!futureShips.isEmpty()) {
                val lastShip = futureShips.last()
                move.ship.futurePosition = Position(lastShip.xPos, lastShip.yPos)
            }
        }
        if (move != null) {
            moveList.add(move)
        }
    }

    private fun speed(ratio: Float): Int {
        return Math.round(ratio * Constants.MAX_SPEED.toFloat())
    }
}
