package dev.keikuethas.utils

import dev.keikuethas.Configuration
import dev.keikuethas.dependencies.Coordinates

fun Configuration.toCoordinates(): Coordinates {
    val parts = packageName.split(":")
    require(parts.size >= 3) {
        "Ожидается groupId:artifactId:version, получено: $packageName"
    }
    return Coordinates(
        groupId = parts[0],
        artifactId = parts[1],
        version = parts[2],
        classifier = parts.getOrNull(3),
        packaging = parts.getOrNull(4)
    )
}