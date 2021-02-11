plugins {
    kotlin("jvm")
    maven
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "6.1.0" apply false
}

val ktlintVersion: String by project
val kotlinVersion: String by project
val jvmVersion: String by project
val ktorVersion: String by project
val logbackVersion: String by project
val opencensusVersion: String by project

allprojects {

    group = "com.github.otkmnb2783"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://kotlin.bintray.com/ktor")
        mavenLocal()
    }

    apply(plugin = "org.jetbrains.kotlin.jvm")

    val ktlint by configurations.creating

    dependencies {
        ktlint("com.pinterest:ktlint:$ktlintVersion")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.opencensus:opencensus-api:$opencensusVersion")
        implementation("io.opencensus:opencensus-exporter-trace-jaeger:$opencensusVersion")
        implementation("io.opencensus:opencensus-exporter-trace-util:$opencensusVersion")
        implementation("io.opencensus:opencensus-contrib-http-util:$opencensusVersion")
        runtimeOnly("io.opencensus:opencensus-impl:$opencensusVersion")
    }

    tasks {
        compileKotlin {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
                jvmTarget = jvmVersion
            }
        }
        val sourcesJar by creating(Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets.main.get().allSource)
        }

        val javadocJar by creating(Jar::class) {
            dependsOn.add(javadoc)
            archiveClassifier.set("javadoc")
            from(javadoc)
        }

        artifacts {
            archives(sourcesJar)
            archives(javadocJar)
            archives(jar)
        }
    }

    task("ktlint", JavaExec::class) {
        group = "verification"
        description = "Check Kotlin code style."
        main = "com.pinterest.ktlint.Main"
        classpath = configurations.getByName("ktlint")
        args("src/main/**/*.kt")
    }

    task("ktlintFormat", JavaExec::class) {
        group = "Ktlint"
        description = "Fix Kotlin code style deviations."
        main = "com.pinterest.ktlint.Main"
        classpath = configurations.getByName("ktlint")
        args("-F", "src/main/**/*.kt")
    }
}
