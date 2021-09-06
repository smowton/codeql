package com.github.codeql

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

class KotlinExtractorComponentRegistrar : ComponentRegistrar {
    override fun registerProjectComponents(
        project: MockProject,
        configuration: CompilerConfiguration
    ) {
        val invocationTrapFile = configuration[KEY_INVOCATION_TRAP_FILE]
        if(invocationTrapFile == null) {
            throw Exception("Required argument for TRAP invocation file not given")
        }
        val checkTrapIdentical = configuration[KEY_CHECK_TRAP_IDENTICAL]
        IrGenerationExtension.registerExtension(project, KotlinExtractorExtension(invocationTrapFile, checkTrapIdentical ?: false))
    }
}
