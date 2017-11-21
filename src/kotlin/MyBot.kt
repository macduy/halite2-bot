import halite.*


fun main(args: Array<String>) {
    val networking = Networking()
    val gameMap = networking.initialize("BrokenSword2")
    val commander by lazy { Commander(gameMap) }
    val executor = Executor(gameMap)

    while (true) {
        gameMap.updateMap(Networking.readLineIntoMetadata())
        commander.update()

        commander.logObjectives()

        gameMap.myPlayer.ships.values
                .filter { it.dockingStatus == DockingStatus.Undocked }
                .forEach { ship ->
                    val objective = commander.getNextObjective()

                    when(objective) {
                        is SettlePlanetObjective -> executor.navigateToPlanet(ship, objective.planet)
                        is AttackPlanetObjective -> { }
                    }
                }
        executor.execute()
    }
}

class Executor(private val gameMap: GameMap) {
    private val moveList = mutableListOf<Move>()

    fun navigateToPlanet(ship: Ship, planet: Planet) {
        if (ship.canDock(planet)) {
            moveList.add(DockMove(ship, planet))
        } else {
            val newThrustMove = Navigation(ship, planet).navigateToDock(this.gameMap, Constants.MAX_SPEED / 2)

            if (newThrustMove != null) {
                moveList.add(newThrustMove)
            }
        }
    }

    fun execute() {
        Networking.sendMoves(this.moveList)
        this.moveList.clear()
    }
}
