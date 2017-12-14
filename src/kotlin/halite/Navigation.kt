package halite

import java.util.*

class Navigation(private val ship: Ship, private val target: Entity, private val gameMap: GameMap) {

    fun navigateToDock(maxThrust: Int = Constants.MAX_SPEED): ThrustMove? {
        val targetPos = ship.getClosestPoint(target)

        return navigateTowards(targetPos, maxThrust)
    }

    fun navigateToShootEnemy(entitySelection: EntitySelection = EntitySelection.PLANETS_AND_ENEMY_SHIPS): ThrustMove? {
        return navigateTowards(ship.getClosestPoint(target, 1), Constants.MAX_SPEED, obstacleDetection=entitySelection)
    }

    fun kamikazeEnemy(): ThrustMove? {
        return navigateTowards(target, Constants.MAX_SPEED, obstacleDetection=EntitySelection.PLANETS_AND_OWN_SHIPS)
    }

    fun navigateTowards(targetPos: Position,
                        maxThrust: Int,
                        maxCorrections: Int = Constants.MAX_NAVIGATION_CORRECTIONS,
                        angularStepRad: Double = Math.PI / 180.0,
                        obstacleDetection: EntitySelection = Flags.DEFAULT_COLLISION_DETECTION): ThrustMove? {

        return navigateTowardsInternal(targetPos, maxThrust, obstacleDetection, maxCorrections, angularStepRad)
                ?: this.navigateTowardsInternal(targetPos, maxThrust, obstacleDetection, maxCorrections, -angularStepRad)
    }

    private fun navigateTowardsInternal(targetPos: Position,
            maxThrust: Int,
            obstacleDetection: EntitySelection,
            maxCorrections: Int,
            angularStepRad: Double): ThrustMove? {
        if (maxCorrections <= 0) {
            return null
        }

        val distance = ship.getDistanceTo(targetPos)
        val angleRad = ship.orientTowardsInRad(targetPos)

        val useVectorPrediction = obstacleDetection == EntitySelection.PLANETS_AND_ENEMY_SHIPS

        if (this.willCollide(targetPos, obstacleDetection) || (useVectorPrediction && this.vectorPredictionWillCollide(angleRad, maxThrust))) {
            val newTargetDx = Math.cos(angleRad + angularStepRad) * distance
            val newTargetDy = Math.sin(angleRad + angularStepRad) * distance
            val newTarget = Position(ship.xPos + newTargetDx, ship.yPos + newTargetDy)

            return navigateTowardsInternal(newTarget, maxThrust, obstacleDetection, maxCorrections - 1, angularStepRad)
        }

        val thrust: Int

        // Do not round up, since overshooting might cause collision.
        thrust = if (distance < maxThrust) distance.toInt() else maxThrust

        val angleDeg = Util.angleRadToDegClipped(angleRad)

        return ThrustMove(ship, angleDeg, thrust)
    }

    private fun willCollide(targetPos: Position, obstacleDetection: EntitySelection): Boolean {
        return !gameMap.objectsBetween(ship, targetPos, obstacleDetection).isEmpty()
    }

    private fun vectorPredictionWillCollide(angleRad: Double, thrust: Int): Boolean {
        // Vector collision prediction
        for (otherShip in this.gameMap.myPlayer.ships.values) {
            if (otherShip.id >= this.ship.id) break

            Log.log("${this.ship.id} vs ${otherShip.id}")

            val futureShip = this.ship.futurePosition(angleRad, thrust)
            val futureOtherShip = otherShip.futurePosition

            // Special check for static ships
            if (futureOtherShip == null) {
                if (Collision.segmentCircleIntersect(this.ship, futureShip, otherShip, Constants.FORECAST_FUDGE_FACTOR)) {
                    return true
                }
                continue
            }

            // Check the distances
            val d1 = this.ship.getDistanceTo(otherShip)
            val d2 = futureShip.getDistanceTo(futureOtherShip)

            Log.log("differential $d1, $d2")

            // Check distance of future ships. Must not be smaller than 2 ship radii
            if (d2 < 2 * Constants.SHIP_RADIUS + 0.05) return true

            // Check total distance difference. If too high, ships are simply too far apart and safe.
            if (d1 + d2 > 2 * Constants.MAX_SPEED) continue

            // Find the angle difference. If it's small, ships will collide
            val a1 = this.ship.orientTowardsInRad(otherShip)
            val a2 = futureOtherShip.orientTowardsInRad(futureShip)

            val da = Math.abs(a1 - a2)
            Log.log("differential $da")
            if (da < VECTOR_ANGLE_DIFFERENTIAL || da > 2 * Math.PI - VECTOR_ANGLE_DIFFERENTIAL) {
                return true
            }
        }

        return false
    }

    companion object {
        val VECTOR_ANGLE_DIFFERENTIAL = Math.PI / 180.0 * 70
    }
}
