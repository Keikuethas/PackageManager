package dev.keikuethas.utils

import dev.keikuethas.Configuration
import dev.keikuethas.RepositoryType

fun Configuration.toRepoUrl(): String = when (repositoryType) {
    RepositoryType.URL  -> address.trimEnd('/')
    RepositoryType.TEST -> TODO("not supported yet")
}