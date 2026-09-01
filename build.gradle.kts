plugins {
    java
}

val deployLocalPlugin by tasks.registering(Copy::class) {
    description = "Copies Judgment to the local Minecraft server's plugins folder."
    group = "build"
    dependsOn(tasks.jar)
    mustRunAfter(tasks.check)
    from(tasks.jar.flatMap { it.archiveFile })
    into(file("${System.getProperty("user.home")}/Documents/Minecraft localhost/plugins"))
    // Keep one installed filename across plugin version changes.
    rename { "Judgment.jar" }
}

val deployTigerMceTestServerPlugin by tasks.registering(Copy::class) {
    description = "Copies Judgment to the TigerMCE test server's plugins folder."
    group = "build"
    dependsOn(tasks.jar)
    mustRunAfter(tasks.check)
    from(tasks.jar.flatMap { it.archiveFile })
    into(file("${System.getProperty("user.home")}/Documents/TigerMCE Test Server/plugins"))
    rename { "Judgment.jar" }
}

tasks.build {
    dependsOn(deployLocalPlugin, deployTigerMceTestServerPlugin)
}

group = "com.bountysmp"
version = "0.4.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.120-stable")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.2.build.120-stable")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    test {
        useJUnitPlatform()
    }
}
