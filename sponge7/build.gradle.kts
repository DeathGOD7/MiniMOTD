import java.util.Locale

plugins {
  id("minimotd.shadow-platform")
}

dependencies {
  implementation(projects.minimotdCommon) {
    exclude("org.slf4j", "slf4j-api")
  }
  implementation(libs.adventurePlatformSpongeApi)
  compileOnly(libs.spongeApi7)
  implementation(libs.bstatsSponge)
}

tasks {
  processResources {
    val replacements = mapOf(
      "modid" to rootProject.name.toLowerCase(Locale.ENGLISH),
      "name" to rootProject.name,
      "version" to project.version.toString(),
      "description" to project.description.toString(),
      "url" to Constants.GITHUB_URL
    )
    inputs.properties(replacements)
    filesMatching("mcmod.info") {
      expand(replacements)
    }
  }
  shadowJar {
    commonRelocation("io.leangen.geantyref")
    commonRelocation("net.kyori")
    commonRelocation("org.bstats")
  }
}
