package com.example.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object FpsArenaMap {
    const val WIDTH = 18
    const val HEIGHT = 18

    // 0 = open tactical floor, 1 = concrete perimeter/bunker, 2 = armored cargo crate, 3 = cyber radar pillar
    val grid: Array<IntArray> = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 1, 1, 0, 0, 2, 0, 0, 2, 0, 0, 1, 1, 0, 0, 1),
        intArrayOf(1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 2, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 2, 1),
        intArrayOf(1, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 1),
        intArrayOf(1, 2, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 2, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1),
        intArrayOf(1, 0, 0, 1, 1, 0, 0, 2, 0, 0, 2, 0, 0, 1, 1, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
    )

    val alphaSpawns = listOf(
        2.2f to 2.2f,
        4.5f to 2.0f,
        2.0f to 5.0f,
        5.0f to 4.5f,
        3.0f to 3.0f,
    )

    val bravoSpawns = listOf(
        15.8f to 15.8f,
        13.5f to 16.0f,
        16.0f to 13.0f,
        13.0f to 13.5f,
        15.0f to 15.0f,
    )

    fun isSolid(x: Float, y: Float, margin: Float = 0.25f): Boolean {
        val minX = (x - margin).toInt()
        val maxX = (x + margin).toInt()
        val minY = (y - margin).toInt()
        val maxY = (y + margin).toInt()
        for (cy in minY..maxY) {
            for (cx in minX..maxX) {
                if (cx !in 0 until WIDTH || cy !in 0 until HEIGHT) return true
                if (grid[cy][cx] != 0) return true
            }
        }
        return false
    }

    data class RayHit(
        val distance: Float,
        val tileType: Int,
        val side: Int,
        val wallX: Float,
    )

    fun castRay(originX: Float, originY: Float, angle: Float, maxDist: Float = 26f): RayHit {
        val dirX = cos(angle)
        val dirY = sin(angle)

        var mapX = originX.toInt()
        var mapY = originY.toInt()

        val deltaDistX = if (abs(dirX) < 1e-6f) 1e6f else abs(1f / dirX)
        val deltaDistY = if (abs(dirY) < 1e-6f) 1e6f else abs(1f / dirY)

        var stepX: Int
        var sideDistX: Float
        if (dirX < 0f) {
            stepX = -1
            sideDistX = (originX - mapX) * deltaDistX
        } else {
            stepX = 1
            sideDistX = (mapX + 1f - originX) * deltaDistX
        }

        var stepY: Int
        var sideDistY: Float
        if (dirY < 0f) {
            stepY = -1
            sideDistY = (originY - mapY) * deltaDistY
        } else {
            stepY = 1
            sideDistY = (mapY + 1f - originY) * deltaDistY
        }

        var hit = 0
        var side = 0
        var steps = 0

        while (hit == 0 && steps < 64) {
            steps++
            if (sideDistX < sideDistY) {
                sideDistX += deltaDistX
                mapX += stepX
                side = 0
            } else {
                sideDistY += deltaDistY
                mapY += stepY
                side = 1
            }
            if (mapX !in 0 until WIDTH || mapY !in 0 until HEIGHT) {
                hit = 1
                break
            }
            if (grid[mapY][mapX] > 0) {
                hit = grid[mapY][mapX]
                break
            }
        }

        val perpDist = if (side == 0) {
            (sideDistX - deltaDistX).coerceIn(0.05f, maxDist)
        } else {
            (sideDistY - deltaDistY).coerceIn(0.05f, maxDist)
        }

        var wallX = if (side == 0) {
            originY + perpDist * dirY
        } else {
            originX + perpDist * dirX
        }
        wallX -= wallX.toInt()

        return RayHit(perpDist, hit, side, wallX)
    }

    fun hasLineOfSight(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx, dy)
        if (dist < 0.1f) return true
        val angle = kotlin.math.atan2(dy, dx)
        val ray = castRay(x1, y1, angle, dist + 0.5f)
        return ray.distance >= dist - 0.15f
    }
}
