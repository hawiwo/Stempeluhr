// Root build.gradle.kts (nicht im app/-Unterordner)

plugins {
    // hier nichts – alle Plugins sind im app/build.gradle.kts definiert
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

