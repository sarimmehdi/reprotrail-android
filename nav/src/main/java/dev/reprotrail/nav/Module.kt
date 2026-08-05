package dev.reprotrail.nav

import org.koin.dsl.module

/** Creates the navigation dependency graph rooted at [startDestination]. */
fun navModule(startDestination: Route) =
    module {
        single { Navigator(startDestination) }
    }
