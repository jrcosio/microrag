package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class RuntimeInfoTest {

    @Test
    fun liteRtLmVersion_matchesTheVersionCatalog() {
        // Unit tests run with the module directory as working directory.
        val catalog = File("../gradle/libs.versions.toml").readText()
        val version = Regex("""(?m)^litertlm\s*=\s*"([^"]+)"""").find(catalog)?.groupValues?.get(1)

        assertThat(version).isEqualTo(RuntimeInfo.LITERT_LM_VERSION)
    }
}
