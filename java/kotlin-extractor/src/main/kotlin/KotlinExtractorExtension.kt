package com.github.codeql

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import com.semmle.util.files.FileUtil
import kotlin.system.exitProcess

class KotlinExtractorExtension(
    private val invocationTrapFile: String,
    private val checkTrapIdentical: Boolean,
    private val compilationStartTime: Long?,
    private val exitAfterExtraction: Boolean)
    : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val startTimeMs = System.currentTimeMillis()
        // This default should be kept in sync with com.semmle.extractor.java.interceptors.KotlinInterceptor.initializeExtractionContext
        val trapDir = File(System.getenv("CODEQL_EXTRACTOR_JAVA_TRAP_DIR").takeUnless { it.isNullOrEmpty() } ?: "kotlin-extractor/trap")
        FileOutputStream(File(invocationTrapFile), true).bufferedWriter().use { invocationTrapFileBW ->
            val lm = TrapLabelManager()
            val tw = TrapWriter(lm, invocationTrapFileBW)
            // The interceptor has already defined #compilation = *
            val compilation: Label<DbCompilation> = StringLabel("compilation")
            tw.writeCompilation_started(compilation)
            if (compilationStartTime != null) {
                tw.writeCompilation_compiler_times(compilation, -1.0, (System.currentTimeMillis()-compilationStartTime)/1000.0)
            }
            tw.flush()
            val logCounter = LogCounter()
            val logger = Logger(logCounter, tw)
            logger.info("Extraction started")
            logger.flush()
            val primitiveTypeMapping = PrimitiveTypeMapping(logger, pluginContext)
            // FIXME: FileUtil expects a static global logger
            // which should be provided by SLF4J's factory facility. For now we set it here.
            FileUtil.logger = logger
            val srcDir = File(System.getenv("CODEQL_EXTRACTOR_JAVA_SOURCE_ARCHIVE_DIR").takeUnless { it.isNullOrEmpty() } ?: "kotlin-extractor/src")
            srcDir.mkdirs()
            val genericSpecialisationsExtracted = HashSet<String>()
            moduleFragment.files.mapIndexed { index: Int, file: IrFile ->
                val fileTrapWriter = tw.makeSourceFileTrapWriter(file, true)
                fileTrapWriter.writeCompilation_compiling_files(compilation, index, fileTrapWriter.fileId)
                doFile(invocationTrapFile, fileTrapWriter, checkTrapIdentical, logCounter, trapDir, srcDir, file, primitiveTypeMapping, pluginContext, genericSpecialisationsExtracted)
            }
            logger.printLimitedWarningCounts()
            // We don't want the compiler to continue and generate class
            // files etc, so we just exit when we are finished extracting.
            logger.info("Extraction completed")
            logger.flush()
            val compilationTimeMs = System.currentTimeMillis() - startTimeMs
            tw.writeCompilation_finished(compilation, -1.0, compilationTimeMs.toDouble() / 1000)
            tw.flush()
        }
        if (exitAfterExtraction) {
            exitProcess(0)
        }
    }
}

fun escapeTrapString(str: String) = str.replace("\"", "\"\"")

private fun equivalentTrap(f1: File, f2: File): Boolean {
    f1.bufferedReader().use { bw1 ->
        f2.bufferedReader().use { bw2 ->
            while(true) {
                val l1 = bw1.readLine()
                val l2 = bw2.readLine()
                if (l1 == null && l2 == null) {
                    return true
                } else if (l1 == null || l2 == null) {
                    return false
                } else if (l1 != l2) {
                    if (!l1.startsWith("//") || !l2.startsWith("//")) {
                        return false
                    }
                }
            }
        }
    }
}

fun doFile(invocationTrapFile: String,
           fileTrapWriter: FileTrapWriter,
           checkTrapIdentical: Boolean,
           logCounter: LogCounter,
           trapDir: File,
           srcDir: File,
           file: IrFile,
           primitiveTypeMapping: PrimitiveTypeMapping,
           pluginContext: IrPluginContext,
           genericSpecialisationsExtracted: MutableSet<String>) {
    val filePath = file.path
    val logger = FileLogger(logCounter, fileTrapWriter)
    logger.info("Extracting file $filePath")
    logger.flush()
    val dest = Paths.get("$srcDir/${file.path}")
    val destDir = dest.getParent()
    Files.createDirectories(destDir)
    val srcTmpFile = File.createTempFile(dest.getFileName().toString() + ".", ".src.tmp", destDir.toFile())
    val srcTmpOS = FileOutputStream(srcTmpFile)
    Files.copy(Paths.get(file.path), srcTmpOS)
    srcTmpOS.close()
    srcTmpFile.renameTo(dest.toFile())

    val trapFile = File("$trapDir/$filePath.trap")
    val trapFileDir = trapFile.getParentFile()
    trapFileDir.mkdirs()
    if (checkTrapIdentical || !trapFile.exists()) {
        val trapTmpFile = File.createTempFile("$filePath.", ".trap.tmp", trapFileDir)
        trapTmpFile.bufferedWriter().use { trapFileBW ->
            // We want our comments to be the first thing in the file,
            // so start off with a mere TrapWriter
            val tw = TrapWriter(TrapLabelManager(), trapFileBW)
            tw.writeComment("Generated by the CodeQL Kotlin extractor for kotlin source code")
            tw.writeComment("Part of invocation $invocationTrapFile")
            // Now elevate to a SourceFileTrapWriter, and populate the
            // file information
            val sftw = tw.makeSourceFileTrapWriter(file, true)
            val externalClassExtractor = ExternalClassExtractor(logger, invocationTrapFile, file.path, primitiveTypeMapping, pluginContext, genericSpecialisationsExtracted)
            val fileExtractor = KotlinSourceFileExtractor(logger, sftw, file.path, externalClassExtractor, primitiveTypeMapping, pluginContext, genericSpecialisationsExtracted)
            fileExtractor.extractFileContents(file, sftw.fileId)
            externalClassExtractor.extractExternalClasses()
        }
        if (checkTrapIdentical && trapFile.exists()) {
            if(equivalentTrap(trapTmpFile, trapFile)) {
                if(!trapTmpFile.delete()) {
                    logger.warn(Severity.WarnLow, "Failed to delete $trapTmpFile")
                }
            } else {
                val trapDifferentFile = File.createTempFile("$filePath.", ".trap.different", trapDir)
                if(trapTmpFile.renameTo(trapDifferentFile)) {
                    logger.warn(Severity.Warn, "TRAP difference: $trapFile vs $trapDifferentFile")
                } else {
                    logger.warn(Severity.WarnLow, "Failed to rename $trapTmpFile to $trapFile")
                }
            }
        } else {
            if(!trapTmpFile.renameTo(trapFile)) {
                logger.warn(Severity.WarnLow, "Failed to rename $trapTmpFile to $trapFile")
            }
        }
    }
}
