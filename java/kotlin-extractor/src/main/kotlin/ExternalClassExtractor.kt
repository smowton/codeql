package com.github.codeql

import com.semmle.extractor.java.OdasaOutput
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.packageFqName
import java.io.File
import java.util.ArrayList
import java.util.HashSet
import java.util.zip.GZIPOutputStream

class ExternalClassExtractor(val logger: FileLogger, val invocationTrapFile: String, val sourceFilePath: String, val primitiveTypeMapping: PrimitiveTypeMapping, val pluginContext: IrPluginContext, val genericSpecialisationsExtracted: MutableSet<String>) {

    val externalClassesDone = HashSet<IrClass>()
    val externalClassWorkList = ArrayList<IrClass>()

    fun extractLater(c: IrClass): Boolean {
        val ret = externalClassesDone.add(c)
        if(ret) externalClassWorkList.add(c)
        return ret
    }

    fun extractExternalClasses() {
        val output = OdasaOutput(false, logger)
        output.setCurrentSourceFile(File(sourceFilePath))
        do {
            val nextBatch = ArrayList(externalClassWorkList)
            externalClassWorkList.clear()
            nextBatch.forEach { irClass ->
                output.getTrapLockerForClassFile(irClass).useAC { locker ->
                    locker.trapFileManager.useAC { manager ->
                        if(manager == null) {
                            logger.info("Skipping extracting class ${irClass.name}")
                        } else {
                            val trapFile = manager.file
                            val binaryPath = getIrClassBinaryPath(irClass)
                            try {
                                GZIPOutputStream(trapFile.outputStream()).bufferedWriter().use { trapFileBW ->
                                    // We want our comments to be the first thing in the file,
                                    // so start off with a mere TrapWriter
                                    val tw = TrapWriter(TrapLabelManager(), trapFileBW)
                                    tw.writeComment("Generated by the CodeQL Kotlin extractor for external dependencies")
                                    tw.writeComment("Part of invocation $invocationTrapFile")
                                    // Now elevate to a SourceFileTrapWriter, and populate the
                                    // file information
                                    val ftw = tw.makeFileTrapWriter(binaryPath, true)

                                    val fileExtractor = KotlinFileExtractor(logger, ftw, binaryPath, manager, this, primitiveTypeMapping, pluginContext, genericSpecialisationsExtracted)

                                    // Populate a location and compilation-unit package for the file. This is similar to
                                    // the beginning of `KotlinFileExtractor.extractFileContents` but without an `IrFile`
                                    // to start from.
                                    val pkg = irClass.packageFqName?.asString() ?: ""
                                    val pkgId = fileExtractor.extractPackage(pkg)
                                    ftw.writeHasLocation(ftw.fileId, ftw.getWholeFileLocation())
                                    ftw.writeCupackage(ftw.fileId, pkgId)

                                    fileExtractor.extractClassSource(irClass)
                                }
                            } catch (e: Exception) {
                                manager.closeWithoutAdditionalFiles()
                                trapFile.delete()
                                logger.error("Failed to extract '$binaryPath'", e)
                            }
                        }
                    }
                }
            }
        } while (externalClassWorkList.isNotEmpty())
        output.writeTrapSet()
    }

}
