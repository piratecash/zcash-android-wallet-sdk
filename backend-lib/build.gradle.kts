import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.proto

plugins {
    id("org.mozilla.rust-android-gradle.rust-android")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("zcash-sdk.android-conventions")
    id("org.jetbrains.dokka")
    id("com.google.protobuf")
    id("wtf.emulator.gradle")
    id("zcash-sdk.emulator-wtf-conventions")
    id("maven-publish")
    id("signing")
    id("zcash-sdk.publishing-conventions")
}

// Publishing information

mavenPublishing {
    coordinates(
        artifactId = "zcash-android-backend"
    )
}

android {
    namespace = "cash.z.ecc.android.backend"

    useLibrary("android.test.runner")

    defaultConfig {
        consumerProguardFiles("proguard-consumer.txt")
    }

    buildTypes {
        getByName("debug").apply {
            // test builds exceed the dex limit because they pull in large test libraries
            isMinifyEnabled = false
        }
        getByName("release").apply {
            isMinifyEnabled = project.property("IS_MINIFY_SDK_ENABLED").toString().toBoolean()
            proguardFiles.addAll(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    File("proguard-project.txt")
                )
            )
        }
        create("benchmark") {
            // We provide the extra benchmark build type just for benchmarking purposes
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    sourceSets.getByName("main") {
        proto { srcDir("src/main/proto") }
    }

    lint {
        baseline = File("lint-baseline.xml")
    }
}

val defaultCargoTargets = listOf("arm", "arm64", "x86", "x86_64")
val cargoTargets = project.providers.gradleProperty("ZCASH_ANDROID_RUST_TARGETS")
    .map { targets ->
        targets.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    .getOrElse(defaultCargoTargets)

fun cargoBuildTaskName(target: String) = when (target) {
    "arm" -> "cargoBuild"
    "arm64" -> "cargoBuildArm64"
    "x86" -> "cargoBuildX86"
    "x86_64" -> "cargoBuildX86_64"
    else -> error("Unsupported cargo target: $target")
}

cargo {
    module = "."
    libname = "zcashwalletsdk"
    targets = cargoTargets
    val minSdkVersion = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
    apiLevels = cargoTargets.associateWith { minSdkVersion }
    profile = "release"
    prebuiltToolchains = true
    // To force the compiler to use the given page size
    // See the new Android 16 KB page size requirement for more details:
    // https://developer.android.com/about/versions/15/behavior-changes-all#16-kb
    exec = { spec, _ ->
        spec.environment["RUST_ANDROID_GRADLE_CC_LINK_ARG"] = "-Wl,-z,max-page-size=16384"
    }
}

// As a workaround to the Gradle (starting from v7.4.1) and Rust Android Gradle plugin (starting from v0.9.3)
// incompatibility issue we need to add rust jni directory manually. See
// https://github.com/mozilla/rust-android-gradle/issues/118
project.afterEvaluate {
    tasks
        .matching {
            name.contains("^merge.+JniLibFolders$".toRegex())
        }
        .configureEach {
            dependsOn(cargoTargets.map(::cargoBuildTaskName))
            // Fix for mergeDebugJniLibFolders UP-TO-DATE
            inputs.dir(layout.buildDirectory.dir("rustJniLibs/android").get().asFile)
        }
}

protobuf {
    protoc {
        artifact = libs.protoc.compiler.get().asCoordinateString()
    }
    plugins {
        id("java") {
            artifact = libs.protoc.gen.java.get().asCoordinateString()
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("java") {
                    option("lite")
                }
            }
            it.builtins {
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    api(projects.lightwalletClientLib)
    api(libs.ktor.core)

    implementation(libs.androidx.annotation)
    implementation(libs.bundles.protobuf)

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Tests
    testImplementation(libs.kotlin.test)

    androidTestImplementation(libs.androidx.multidex)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.zcashwalletplgn)
    androidTestImplementation(libs.bip39)
}

tasks {
    getByName("preBuild").dependsOn(register("bugfixTask") {
        doFirst {
            mkdir("build/extracted-include-protos/main")
        }
    })

    /*
     * The Mozilla Rust Gradle plugin caches the native build data under the "target" directory,
     * which does not normally get deleted during a clean. The following task and dependency solves
     * that.
     */
    getByName<Delete>("clean").dependsOn(register<Delete>("cleanRustBuildOutput") {
        delete("target")
    })
}

project.afterEvaluate {
    val cargoTasks = cargoTargets.map(::cargoBuildTaskName)
    tasks.getByName("javaPreCompileDebug").dependsOn(cargoTasks)
    tasks.getByName("javaPreCompileRelease").dependsOn(cargoTasks)
}

fun MinimalExternalModuleDependency.asCoordinateString() =
    "${module.group}:${module.name}:${versionConstraint.displayName}"
