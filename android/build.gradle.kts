plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("kotlin-kapt") version "1.9.22" apply false
    id("kotlin-parcelize") version "1.9.22" apply false
    id("com.google.dagger.hilt") version "2.51" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}