import halite.*


fun main(args: Array<String>) {
    val networking = Networking()
    val gameMap = networking.initialize("BrokenSword6")
    val commander by lazy { Commander(gameMap) }
    val executor = Executor(gameMap, commander)

    var turn = 0

    while (true) {
        Log.log("--- TURN $turn ---")
        gameMap.updateMap(Networking.readLineIntoMetadata())
        commander.update()

        for (ship in gameMap.myPlayer.ships.values) {
            if (ship.dockingStatus != DockingStatus.Undocked) continue

            commander.assignBestObjective(ship)

            executor.direct(ship)
        }

        executor.execute()
        commander.logObjectives(10)

        turn += 1
    }
}
