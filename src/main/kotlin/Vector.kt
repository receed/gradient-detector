class Vector(val x: Float, val y: Float, val z: Float) {
    constructor() : this(0F, 0F, 0F)

    operator fun plus(other: Vector) =
        Vector(x + other.x, y + other.y, z + other.z)

    operator fun times(k: Float) =
        Vector(x * k, y * k, z * k)
}