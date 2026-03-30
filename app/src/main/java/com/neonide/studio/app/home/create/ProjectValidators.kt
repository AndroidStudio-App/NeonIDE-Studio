package com.neonide.studio.app.home.create

object ProjectValidators {

    private val PROJECT_NAME = Regex("^[A-Za-z][A-Za-z0-9_\\-\\+]{1,50}$")
    private val PACKAGE_NAME = Regex("^[a-zA-Z]+(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValidProjectName(name: String): Boolean = PROJECT_NAME.matches(name)

    fun isValidPackageName(pkg: String): Boolean = PACKAGE_NAME.matches(pkg)
}
