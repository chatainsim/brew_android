package fr.easter.brewhome.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Formes plus généreusement arrondies que les valeurs par défaut de Material 3
// (4/8/12/16/28dp), pour un rendu plus chaleureux et actuel cohérent avec la
// palette ambre - surtout visible sur les cartes (recettes, brassins, stock)
// et les feuilles/dialogues.
val BrewHomeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
