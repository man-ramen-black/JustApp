package com.black.feature.pokerogue.model

import androidx.annotation.DrawableRes
import com.black.feature.pokerogue.R

enum class PokeType(
    @DrawableRes val iconRes: Int,
) {
    Normal(R.drawable.ic_type_normal),
    Fire(R.drawable.ic_type_fire),
    Water(R.drawable.ic_type_water),
    Grass(R.drawable.ic_type_grass),
    Electric(R.drawable.ic_type_electric),
    Ice(R.drawable.ic_type_ice),
    Fighting(R.drawable.ic_type_fight),
    Poison(R.drawable.ic_type_poison),
    Ground(R.drawable.ic_type_ground),
    Flying(R.drawable.ic_type_fly),
    Psychic(R.drawable.ic_type_esper),
    Bug(R.drawable.ic_type_bug),
    Rock(R.drawable.ic_type_rock),
    Ghost(R.drawable.ic_type_ghost),
    Dragon(R.drawable.ic_type_dragon),
    Dark(R.drawable.ic_type_dark),
    Steel(R.drawable.ic_type_steel),
    Fairy(R.drawable.ic_type_fairy)
}