import halite.*


fun main(args: Array<String>) {
    val networking = Networking()
    val gameMap = networking.initialize("BrokenSword2")
    val commander by lazy { Commander(gameMap) }
    val executor = Executor(gameMap)

    while (true) {
        gameMap.updateMap(Networking.readLineIntoMetadata())
        commander.update()

        commander.logObjectives(30)

        gameMap.myPlayer.ships.values
                .filter { it.dockingStatus == DockingStatus.Undocked }
                .forEach { ship ->

                    val objective = ship.objective ?: commander.assignObjective(ship)

                    when(objective) {
                        is SettlePlanetObjective -> executor.navigateToPlanet(ship, objective.planet)
                        is AttackPlanetObjective -> executor.navigateToAttackPlanet(ship, objective.planet)
                        null -> { }
                    }
                }
        executor.execute()
    }
}

class Executor(private val gameMap: GameMap) {
    private val moveList = mutableListOf<Move>()

    fun navigateToPlanet(ship: Ship, planet: Planet) {
        if (ship.canDock(planet)) {
            this.addMove(DockMove(ship, planet))
        } else {
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(Constants.MAX_SPEED / 2))
        }
    }

    fun navigateToAttackPlanet(ship: Ship, planet: Planet) {
        val move: Move?
        if (ship.withinDistance(planet, Constants.DOCK_RADIUS + 5.0)) {
            // Pick an enemy on the planet
            Log.log("${planet}")
            val enemyShip = gameMap.allShips.get(planet.dockedShips.first())
            if (enemyShip != null) {
                Log.log("Targetting ${enemyShip.id} on ${planet.id}")
                this.addMove(Navigation(ship, enemyShip, gameMap).navigateToEnemy())
            }
        } else {
            // Navigate to the planet
            this.addMove(Navigation(ship, planet, gameMap).navigateToDock(Constants.MAX_SPEED / 2))
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
}
