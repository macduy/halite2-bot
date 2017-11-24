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