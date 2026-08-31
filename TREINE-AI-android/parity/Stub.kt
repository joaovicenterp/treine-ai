package com.treineai.app.data

/* Espelho sem serialização das classes usadas pela camada de movimento.
   Serve apenas ao arnês de paridade: o app real usa data/Models.kt. */
data class RepRange(val joint: String, val top: Double, val bottom: Double)

data class Exercise(
    val id: String,
    val name: String,
    val group: String = "",
    val equipment: String = "",
    val level: String = "",
    val pattern: String,
    val view: String = "front",
    val rep: RepRange,
    val free: Boolean = false,
    val hold: Boolean = false,
    val checks: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val tips: List<String> = emptyList()
)
