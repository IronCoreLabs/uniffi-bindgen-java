plugins {
    java
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // JNA for FFI - pick up from CLASSPATH or UNIFFI_JNA_CLASSPATH env var (provided by nix)
    val jnaClasspath = System.getenv("UNIFFI_JNA_CLASSPATH")
        ?: System.getenv("CLASSPATH")
        ?: ""
    if (jnaClasspath.isNotEmpty()) {
        implementation(files(jnaClasspath.split(":")))
    }
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(2)
    resultsFile.set(layout.buildDirectory.file("results/jmh/results.txt"))
    // Allow overriding JMH args via -PjmhArgs="..."
    if (project.hasProperty("jmhArgs")) {
        val args = (project.property("jmhArgs") as String).split(" ")
        val iter = args.iterator()
        while (iter.hasNext()) {
            when (val arg = iter.next()) {
                "-f" -> if (iter.hasNext()) fork.set(iter.next().toInt())
                "-wi" -> if (iter.hasNext()) warmupIterations.set(iter.next().toInt())
                "-i" -> if (iter.hasNext()) iterations.set(iter.next().toInt())
                "-w" -> if (iter.hasNext()) warmup.set(iter.next())
                "-r" -> if (iter.hasNext()) timeOnIteration.set(iter.next())
                else -> {
                    if (!arg.startsWith("-")) {
                        includes.add(arg)
                    }
                }
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-sources/uniffi"))
        }
        resources {
            // Native library packaged for JNA resource loading
            srcDir(layout.buildDirectory.dir("native-resources"))
        }
    }
}
