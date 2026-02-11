buildscript {
    dependencies {
        classpath(libs.aboutLibraries.gradle)
        classpath(libs.dagger.hilt.gradle)
        classpath(libs.sqldelight.gradle)
    }
}

plugins {
    alias(libs.plugins.kotlinKsp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.sqldelight) apply false
}

val kotlinVersion = "2.3.0"
allprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
        )
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    delete("app/src/main/res/values-in/")
    delete("app/src/main/res/values-iw/")
    delete("app/src/main/res/xml/locales_config.xml")
    // delete("app/src/main/res/raw/ne_50m_admin_0_countries.json")
}
