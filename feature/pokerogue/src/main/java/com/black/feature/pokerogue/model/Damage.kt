package com.black.feature.pokerogue.model

enum class Damage(val multiplier: Double) {
    X4(4.0),
    X2(2.0),
    X1(1.0),
    X05(0.5),
    X025(0.25),
    X0(0.0);

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