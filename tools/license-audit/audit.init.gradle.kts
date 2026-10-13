// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Auditoria de licenças (só no CI e quando você pede): gera o SBOM CycloneDX e a lista das versões
// efetivamente resolvidas em cada classpath, sem mexer no build do app. O F-Droid e o build normal
// não usam este arquivo, então nada muda na compilação nem no APK.
//
// Uso:
//   ./gradlew --init-script tools/license-audit/audit.init.gradle.kts :app:cyclonedxDirectBom :app:finanResolvedDependencies
//   python3 tools/license-audit/check_licenses.py
//
// Saídas (em app/build/reports/):
//   cyclonedx-direct/bom.json        SBOM CycloneDX 1.6 (componentes, versões, licenças declaradas nos POMs)
//   license-audit/resolved.json      grupo:nome:versão resolvidos por classpath (release, debug, testes)

import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.gradle.CyclonedxPlugin
import org.cyclonedx.model.Component
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        // CycloneDX Gradle Plugin (Apache-2.0), ferramenta de build: não entra no APK.
        classpath("org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.5.1")
    }
}

// Classpaths auditados. O que vai para o APK publicado é o releaseRuntimeClasspath.
val auditedConfigs = listOf(
    "releaseRuntimeClasspath",        // APK de release (F-Droid e GitHub Releases)
    "debugRuntimeClasspath",          // APK de teste (inclui ferramentas de depuração do Compose)
    "debugUnitTestRuntimeClasspath",  // testes unitários (JUnit, Robolectric, Roborazzi): não distribuído
)

rootProject {
    apply<CyclonedxPlugin>()
}

allprojects {
    if (path == ":app") {
        afterEvaluate {
            tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
                projectType.set(Component.Type.APPLICATION)
                componentName.set("finan-plus-android")
                includeConfigs.set(auditedConfigs)
                // componente marcado como "test" só quando aparece apenas no classpath de testes
                testConfigs.set(listOf(".*UnitTest.*"))
                includeLicenseText.set(false)
            }

            tasks.register("finanResolvedDependencies") {
                description = "Lista grupo:nome:versão efetivamente resolvidos em cada classpath auditado."
                val out = layout.buildDirectory.file("reports/license-audit/resolved.json")
                outputs.file(out)
                outputs.upToDateWhen { false }
                doLast {
                    val json = auditedConfigs.joinToString(",\n", "{\n", "\n}\n") { name ->
                        val ids = project.configurations.getByName(name).incoming.resolutionResult.allComponents
                            .mapNotNull { it.id as? ModuleComponentIdentifier }
                            .map { "${it.group}:${it.module}:${it.version}" }
                            .distinct().sorted()
                        "  \"$name\": [\n" + ids.joinToString(",\n") { "    \"$it\"" } + "\n  ]"
                    }
                    val f = out.get().asFile
                    f.parentFile.mkdirs()
                    f.writeText(json)
                    println("Dependências resolvidas: $f")
                }
            }
        }
    }
}
