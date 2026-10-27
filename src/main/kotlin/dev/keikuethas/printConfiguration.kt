package dev.keikuethas

fun Configuration.print() {
    println("packageName: $packageName")
    println("address: $address")
    println("repositoryType: $repositoryType")
    println("outFileName: $outFileName")
    println("tree: $tree")
    println("maxDepth: $maxDepth")
}