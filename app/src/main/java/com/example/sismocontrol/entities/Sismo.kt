package com.example.sismocontrol.entities


data class Sismo(

    val lugar: String,
    val magnitud: Double, // Cambiando el tipo de String a Double
    val latitud: Double,
    val longitud: Double,
    val tiempo: Long
)
