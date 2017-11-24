package halite

class Navigation(private val ship: Ship, private val target: Entity, private val gameMap: GameMap) {

    fun navigateToDock(maxThrust: Int, angularStepMultiplier: Int = 1): ThrustMove? {
        val maxCorrections = Constants.MAX_NAVIGATION_CORRECTIONS / angularStepMultiplier
        val angularStepRad = Math.PI / 180.0 * angularStepMultiplier
        val targetPos = ship.getClosestPoint(target)

        return navigateTowards(targetPos, maxThrust, EntitySelection.ALL, maxCorrections, angularStepRad)
    }

    fun navigateToShootEnemy(): ThrustMove? {
        val maxCorrections = Constants.MAX_NAVIGATION_CORRECTIONS
        val angularStepRad = Math.PI / 180.0
        return navigateTowards(ship.getClosestPoint(target, -1), Constants.MAX_SPEED, EntitySelection.ALL, maxCorrections, angularStepRad)
    }

    fun kamikazeEnemy(): ThrustMove? {
        val maxCorrections = Constants.MAX_NAVIGATION_CORRECTIONS
        val angularStepRad = Math.PI / 180.0

        return navigateTowards(target, Constants.MAX_SPEED, EntitySelection.PLANETS_AND_OWN_SHIPS, maxCorrections, angularStepRad)
    }

    fun navigateTowards(targetPos: Position,
                        maxThrust: Int,
                        obstacleDetection: EntitySelection,
                        maxCorrections: Int,
                        angularStepRad: Double): ThrustMove? {
        if (maxCorrections <= 0) {
            return null
        }

        val distance = ship.getDistanceTo(targetPos)
        val angleRad = ship.orientTowardsInRad(targetPos)

        if (!gameMap.objectsBetween(ship, targetPos, obstacleDetection).isEmpty()) {
            val newTargetDx = Math.cos(angleRad + angularStepRad) * distance
            val newTargetDy = Math.sin(angleRad + angularStepRad) * distance
            val newTarget = Position(ship.xPos + newTargetDx, ship.yPos + newTargetDy)

            return navigateTowards(newTarget, maxThrust, obstacleDetection, maxCorrections - 1, angularStepRad)
        }

        val thrust: Int
        thrust = if (distance < maxThrust) {
            // Do not round up, since overshooting might cause collision.
            distance.toInt()
        } else {
            maxThrust
        }

        val angleDeg = Util.angleRadToDegClipped(angleRad)

        return ThrustMove(ship, angleDeg, thrust)
    }
}
