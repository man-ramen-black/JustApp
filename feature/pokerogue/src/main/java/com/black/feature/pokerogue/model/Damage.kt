package com.black.feature.pokerogue.model

enum class Damage(val multiplier: Double, val label: String) {
    X4(4.0, "x4"),
    X2(2.0, "x2"),
    X1(1.0, "x1"),
    X05(0.5, "x0.5"),
    X025(0.25, "x0.25"),
    X0(0.0, "x0");

    companion object {
        fun valueOf(multiplier: Double): Damage {
            return when (multiplier) {
                4.0 -> X4
                2.0 -> X2
                1.0 -> X1
                0.5 -> X05
                0.25 -> X025
                0.0 -> X0
                else -> X1
            }
        }
    }
}