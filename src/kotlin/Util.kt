import halite.Entity
import halite.Planet
import halite.Position
import halite.Ship

fun Double.clamp(from: Double, to: Double): Double =
    when {
        this < from -> from
        this > to -> to
        else -> this
    }

fun Int.ratioOf(other: Int): Double = this.toDouble() / other.toDouble()

fun <E> List<E>.pickRandom(): E {
    return this[(Math.random() * this.size).toInt()]
}

fun Collection<Entity>.center(): Position? {
    if (this.isEmpty()) return null

    var x = 0.0
    var y = 0.0
    for (entity in this) {
        x += entity.xPos
        y += entity.yPos
    }

    return Position(x / this.count(), y / this.count())
}

fun <E: Entity> Collection<E>.nearestTo(other: Position): E? {
    if (this.isEmpty()) return null

    var nearest: E? = null
    var minDist = Double.MAX_VALUE
    for (entity in this) {
        val dist = entity.getDistanceTo(other)
        if (dist < minDist) {
            minDist = dist
            nearest = entity
        }
    }

    return nearest
}