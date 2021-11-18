package com.github.codeql

import com.github.codeql.comments.CommentExtractor
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.*
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.zip.GZIPOutputStream
import com.semmle.extractor.java.OdasaOutput
import com.semmle.extractor.java.OdasaOutput.TrapFileManager
import com.semmle.util.files.FileUtil
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.types.Variance
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
            // FIXME: FileUtil expects a static global logger
            // which should be provided by SLF4J's factory facility. For now we set it here.
            FileUtil.logger = logger
            val srcDir = File(System.getenv("CODEQL_EXTRACTOR_JAVA_SOURCE_ARCHIVE_DIR").takeUnless { it.isNullOrEmpty() } ?: "kotlin-extractor/src")
            srcDir.mkdirs()
            moduleFragment.files.mapIndexed { index: Int, file: IrFile ->
                val fileTrapWriter = SourceFileTrapWriter(lm, invocationTrapFileBW, file)
                fileTrapWriter.writeCompilation_compiling_files(compilation, index, fileTrapWriter.fileId)
                doFile(invocationTrapFile, fileTrapWriter, checkTrapIdentical, logCounter, trapDir, srcDir, file, pluginContext)
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

interface Label<T>

class IntLabel<T>(val name: Int): Label<T> {
    override fun toString(): String = "#$name"
}

class StringLabel<T>(val name: String): Label<T> {
    override fun toString(): String = "#$name"
}

class StarLabel<T>: Label<T> {
    override fun toString(): String = "*"
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
           pluginContext: IrPluginContext) {
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
            val tw = SourceFileTrapWriter(TrapLabelManager(), trapFileBW, file)
            tw.writeComment("Generated by invocation $invocationTrapFile")
            val externalClassExtractor = ExternalClassExtractor(logger, file.path, pluginContext)
            val fileExtractor = KotlinSourceFileExtractor(logger, tw, file, externalClassExtractor, pluginContext)
            fileExtractor.extractFileContents(tw.fileId)
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

fun <T> fakeLabel(): Label<T> {
    if (true) {
        println("Fake label")
    } else {
        val sw = StringWriter()
        Exception().printStackTrace(PrintWriter(sw))
        println("Fake label from:\n$sw")
    }
    return IntLabel(0)
}

class ExternalClassExtractor(val logger: FileLogger, val sourceFilePath: String, val pluginContext: IrPluginContext) {

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
                    locker.getTrapFileManager().useAC { manager ->
                        if(manager == null) {
                            logger.info("Skipping extracting class ${irClass.name}")
                        } else {
                            GZIPOutputStream(manager.getFile().outputStream()).bufferedWriter().use { trapFileBW ->
                                val tw = ClassFileTrapWriter(TrapLabelManager(), trapFileBW, getIrClassBinaryPath(irClass))
                                val fileExtractor = KotlinFileExtractor(logger, tw, manager, this, pluginContext)
                                fileExtractor.extractClassSource(irClass)
                            }
                        }
                    }
                }
            }
        } while (externalClassWorkList.isNotEmpty())
        output.writeTrapSet()
    }

}

class KotlinSourceFileExtractor(
    logger: FileLogger,
    tw: FileTrapWriter,
    val file: IrFile,
    externalClassExtractor: ExternalClassExtractor,
    pluginContext: IrPluginContext) :
  KotlinFileExtractor(logger, tw, null, externalClassExtractor, pluginContext) {

    val fileClass by lazy {
        extractFileClass(file)
    }

    fun extractFileContents(id: Label<DbFile>) {
        val locId = tw.getWholeFileLocation()
        val pkg = file.fqName.asString()
        val pkgId = extractPackage(pkg)
        tw.writeHasLocation(id, locId)
        tw.writeCupackage(id, pkgId)
        file.declarations.map { extractDeclaration(it, fileClass) }
        CommentExtractor(this).extract()
    }

  @OptIn(kotlin.ExperimentalStdlibApi::class)
  fun extractFileClass(f: IrFile): Label<out DbClass> {
      val fileName = f.fileEntry.name
      val pkg = f.fqName.asString()
      val defaultName = fileName.replaceFirst(Regex(""".*[/\\]"""), "").replaceFirst(Regex("""\.kt$"""), "").replaceFirstChar({ it.uppercase() }) + "Kt"
      var jvmName = defaultName
      for(a: IrConstructorCall in f.annotations) {
          val t = a.type
          if(t is IrSimpleType && a.valueArgumentsCount == 1) {
              val owner = t.classifier.owner
              val v = a.getValueArgument(0)
              if(owner is IrClass) {
                  val aPkg = owner.packageFqName?.asString()
                  val name = owner.name.asString()
                  if(aPkg == "kotlin.jvm" && name == "JvmName" && v is IrConst<*>) {
                      val value = v.value
                      if(value is String) {
                          jvmName = value
                      }
                  }
              }
          }
      }
      val qualClassName = if (pkg.isEmpty()) jvmName else "$pkg.$jvmName"
      val label = "@\"class;$qualClassName\""
      val id: Label<DbClass> = tw.getLabelFor(label)
      val locId = tw.getWholeFileLocation()
      val pkgId = extractPackage(pkg)
      tw.writeClasses(id, jvmName, pkgId, id)
      tw.writeHasLocation(id, locId)
      return id
  }

}

data class PrimitiveTypeInfo(
    val primitiveName: String?,
    val javaPackageName: String, val javaClassName: String,
    val kotlinPackageName: String, val kotlinClassName: String
)

val primitiveTypeMapping = mapOf(
    IdSignatureValues._byte to PrimitiveTypeInfo("byte", "java.lang", "Byte", "kotlin", "Byte"),
    IdSignatureValues._short to PrimitiveTypeInfo("short", "java.lang", "Short", "kotlin", "Short"),
    IdSignatureValues._int to PrimitiveTypeInfo("int", "java.lang", "Integer", "kotlin", "Int"),
    IdSignatureValues._long to PrimitiveTypeInfo("long", "java.lang", "Long", "kotlin", "Long"),

    IdSignatureValues.uByte to PrimitiveTypeInfo("byte", "kotlin", "UByte", "kotlin", "UByte"),
    IdSignatureValues.uShort to PrimitiveTypeInfo("short", "kotlin", "UShort", "kotlin", "UShort"),
    IdSignatureValues.uInt to PrimitiveTypeInfo("int", "kotlin", "UInt", "kotlin", "UInt"),
    IdSignatureValues.uLong to PrimitiveTypeInfo("long", "kotlin", "ULong", "kotlin", "ULong"),

    IdSignatureValues._double to PrimitiveTypeInfo("double", "java.lang", "Double", "kotlin", "Double"),
    IdSignatureValues._float to PrimitiveTypeInfo("float", "java.lang", "Float", "kotlin", "Float"),

    IdSignatureValues._boolean to PrimitiveTypeInfo("boolean", "java.lang", "Boolean", "kotlin", "Boolean"),

    IdSignatureValues._char to PrimitiveTypeInfo("char", "java.lang", "Character", "kotlin", "Char"),

    IdSignatureValues.unit to PrimitiveTypeInfo("void", "java.lang", "Void", "kotlin", "Nothing"), // TODO: Is this right?
    IdSignatureValues.nothing to PrimitiveTypeInfo(null, "java.lang", "Void", "kotlin", "Nothing"), // TODO: Is this right?
)

open class KotlinUsesExtractor(
    open val logger: Logger,
    open val tw: TrapWriter,
    val dependencyCollector: TrapFileManager?,
    val externalClassExtractor: ExternalClassExtractor,
    val pluginContext: IrPluginContext) {
    fun usePackage(pkg: String): Label<out DbPackage> {
        return extractPackage(pkg)
    }

    fun extractPackage(pkg: String): Label<out DbPackage> {
        val pkgLabel = "@\"package;$pkg\""
        val id: Label<DbPackage> = tw.getLabelFor(pkgLabel, {
            tw.writePackages(it, pkg)
        })
        return id
    }

    data class UseClassInstanceResult(val classLabel: Label<out DbClassorinterface>, val javaClass: IrClass)
    data class TypeResult<LabelType>(val id: Label<LabelType>, val signature: String)
    data class TypeResults(val javaResult: TypeResult<out DbType>, val kotlinResult: TypeResult<out DbKt_type>)

    fun useType(t: IrType, canReturnPrimitiveTypes: Boolean = true) =
        when(t) {
            is IrSimpleType -> useSimpleType(t, canReturnPrimitiveTypes)
            else -> {
                logger.warn(Severity.ErrorSevere, "Unrecognised IrType: " + t.javaClass)
                TypeResults(TypeResult(fakeLabel(), "unknown"), TypeResult(fakeLabel(), "unknown"))
            }
        }

    fun getJavaEquivalentClass(c: IrClass) =
        c.fqNameWhenAvailable?.toUnsafe()
            ?.let { JavaToKotlinClassMap.mapKotlinToJava(it) }
            ?.let { pluginContext.referenceClass(it.asSingleFqName()) }
            ?.owner

    /**
     * Gets a KotlinFileExtractor based on this one, except it attributes locations to the file that declares the given class.
     */
    fun withSourceFileOfClass(cls: IrClass, populateFileTables: Boolean): KotlinFileExtractor {
        val clsFile = cls.fileOrNull

        val newTrapWriter =
            if (isExternalDeclaration(cls) || clsFile == null)
                tw.withTargetFile(getIrClassBinaryPath(cls), null, populateFileTables)
            else
                tw.withTargetFile(clsFile.path, clsFile.fileEntry)

        val newLogger = FileLogger(logger.logCounter, newTrapWriter)

        return KotlinFileExtractor(newLogger, newTrapWriter, dependencyCollector, externalClassExtractor, pluginContext)
    }

    fun useClassInstance(c: IrClass, typeArgs: List<IrTypeArgument>): UseClassInstanceResult {
        // TODO: only substitute in class and function signatures
        //       because within function bodies we can get things like Unit.INSTANCE
        //       and List.asIterable (an extension, i.e. static, method)
        // Map Kotlin class to its equivalent Java class:
        val substituteClass = getJavaEquivalentClass(c)

        val extractClass = substituteClass ?: c

        val classId = getClassLabel(extractClass, typeArgs)
        val classLabel : Label<out DbClassorinterface> = tw.getLabelFor(classId, {
            // If this is a generic type instantiation then it has no
            // source entity, so we need to extract it here
            if (typeArgs.isNotEmpty()) {
                this.withSourceFileOfClass(extractClass, false).extractClassInstance(extractClass, typeArgs)
            }

            // Extract both the Kotlin and equivalent Java classes, so that we have database entries
            // for both even if all internal references to the Kotlin type are substituted.
            extractClassLaterIfExternal(c)
            substituteClass?.let { extractClassLaterIfExternal(it) }
        })

        return UseClassInstanceResult(classLabel, extractClass)
    }

    fun isExternalDeclaration(d: IrDeclaration): Boolean {
        return d.origin == IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB ||
               d.origin == IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
    }

    fun isArray(t: IrSimpleType) = t.isBoxedArray || t.isPrimitiveArray()

    fun extractClassLaterIfExternal(c: IrClass) {
        if (isExternalDeclaration(c)) {
            extractExternalClassLater(c)
        }
    }

    fun extractExternalEnclosingClassLater(d: IrDeclaration) {
        when (val parent = d.parent) {
            is IrClass -> extractExternalClassLater(parent)
            is IrFunction -> extractExternalEnclosingClassLater(parent)
            is IrFile -> logger.warn(Severity.ErrorSevere, "extractExternalEnclosingClassLater but no enclosing class.")
            else -> logger.warn(Severity.ErrorSevere, "Unrecognised extractExternalEnclosingClassLater: " + d.javaClass)
        }
    }

    fun extractExternalClassLater(c: IrClass) {
        dependencyCollector?.addDependency(c)
        externalClassExtractor.extractLater(c)
    }

    fun addClassLabel(c: IrClass, typeArgs: List<IrTypeArgument>): Label<out DbClassorinterface> =
        tw.getLabelFor(getClassLabel(c, typeArgs))

    fun shortName(type: IrType, canReturnPrimitiveTypes: Boolean = true): String =
        when(type) {
            is IrSimpleType -> {
                val primitiveInfo = primitiveTypeMapping[type.classifier.signature]
                when {
                    primitiveInfo?.primitiveName != null ->
                        if (type.hasQuestionMark || !canReturnPrimitiveTypes)
                            primitiveInfo.javaClassName
                        else
                            primitiveInfo.primitiveName

                    isArray(type) -> {
                        val elementType = type.getArrayElementType(pluginContext.irBuiltIns)
                        val javaElementType = if (type.isPrimitiveArray()) elementType else elementType.makeNullable()
                        shortName(javaElementType) + "[]"
                    }

                    type.classifier.owner is IrClass -> {
                        val c = type.classifier.owner as IrClass
                        classShortName(getJavaEquivalentClass(c) ?: c, type.arguments)
                    }

                    type.classifier.owner is IrTypeParameter -> (type.classifier.owner as IrTypeParameter).name.asString()

                    else -> "???"
                }
            }
            else -> "???"
        }

    // Pretty-print typeArg the same way the Java extractor would:
    fun typeArgShortName(typeArg: IrTypeArgument): String =
        when(typeArg) {
            is IrStarProjection -> "?"
            is IrTypeProjection -> {
                val prefix = when(typeArg.variance) {
                    Variance.INVARIANT -> ""
                    Variance.OUT_VARIANCE -> "? extends "
                    Variance.IN_VARIANCE -> "? super "
                }
                "$prefix${shortName(typeArg.type, false)}"
            }
            else -> {
                logger.warn(Severity.ErrorSevere, "Unexpected type argument.")
                "???"
            }
        }

    fun typeArgsShortName(typeArgs: List<IrTypeArgument>): String {
        if(typeArgs.isEmpty())
            return ""
        return typeArgs.joinToString(prefix = "<", postfix = ">", separator = ",") { typeArgShortName(it) }
    }

    fun classShortName(c: IrClass, typeArgs: List<IrTypeArgument>) =
        "${c.name}${typeArgsShortName(typeArgs)}"

    fun useSimpleTypeClass(c: IrClass, args: List<IrTypeArgument>, hasQuestionMark: Boolean): TypeResults {
        val classInstanceResult = useClassInstance(c, args)
        val javaClassId = classInstanceResult.classLabel
        val kotlinQualClassName = getUnquotedClassLabel(c, args)
        val javaQualClassName = classInstanceResult.javaClass.fqNameForIrSerialization.asString()
        val javaSignature = javaQualClassName // TODO: Is this right?
        val javaResult = TypeResult(javaClassId, javaSignature)
        val kotlinResult = if (hasQuestionMark) {
                val kotlinSignature = "$kotlinQualClassName?" // TODO: Is this right?
                val kotlinLabel = "@\"kt_type;nullable;$kotlinQualClassName\""
                val kotlinId: Label<DbKt_nullable_type> = tw.getLabelFor(kotlinLabel, {
                    tw.writeKt_nullable_types(it, javaClassId)
                })
                TypeResult(kotlinId, kotlinSignature)
            } else {
                val kotlinSignature = kotlinQualClassName // TODO: Is this right?
                val kotlinLabel = "@\"kt_type;notnull;$kotlinQualClassName\""
                val kotlinId: Label<DbKt_notnull_type> = tw.getLabelFor(kotlinLabel, {
                    tw.writeKt_notnull_types(it, javaClassId)
                })
                TypeResult(kotlinId, kotlinSignature)
            }
        return TypeResults(javaResult, kotlinResult)
    }

    // Given either a primitive array or a boxed array, returns primitive arrays unchanged,
    // but returns boxed arrays with a nullable, invariant component type, with any nested arrays
    // similarly transformed. For example, Array<out Array<in E>> would become Array<Array<E?>?>
    // Array<*> will become Array<Any?>.
    fun getInvariantNullableArrayType(arrayType: IrSimpleType): IrSimpleType =
        if (arrayType.isPrimitiveArray())
            arrayType
        else {
            val componentType = arrayType.getArrayElementType(pluginContext.irBuiltIns)
            val componentTypeBroadened = when (componentType) {
                is IrSimpleType ->
                    if (isArray(componentType)) getInvariantNullableArrayType(componentType) else componentType
                else -> componentType
            }
            val unchanged =
                componentType == componentTypeBroadened &&
                        (arrayType.arguments[0] as? IrTypeProjection)?.variance == Variance.INVARIANT &&
                        componentType.isNullable()
            if (unchanged)
                arrayType
            else
                IrSimpleTypeImpl(
                    arrayType.classifier,
                    true,
                    listOf(makeTypeProjection(componentTypeBroadened, Variance.INVARIANT)),
                    listOf()
                )
        }

    fun useArrayType(arrayType: IrSimpleType, componentType: IrType, elementType: IrType, dimensions: Int, isPrimitiveArray: Boolean): TypeResults {

        // Ensure we extract Array<Int> as Integer[], not int[], for example:
        fun nullableIfNotPrimitive(type: IrType) = if (type.isPrimitiveType() && !isPrimitiveArray) type.makeNullable() else type

        // TODO: Figure out what signatures should be returned

        val componentTypeLabel = useType(nullableIfNotPrimitive(componentType)).javaResult.id
        val elementTypeLabel = useType(nullableIfNotPrimitive(elementType)).javaResult.id

        val id = tw.getLabelFor<DbArray>("@\"array;$dimensions;{${elementTypeLabel}}\"") {
            tw.writeArrays(
                it,
                shortName(arrayType),
                elementTypeLabel,
                dimensions,
                componentTypeLabel)

            extractClassSupertypes(arrayType.classifier.owner as IrClass, it)

            // array.length
            val length = tw.getLabelFor<DbField>("@\"field;{$it};length\"")
            val intTypeIds = useType(pluginContext.irBuiltIns.intType)
            tw.writeFields(length, "length", intTypeIds.javaResult.id, intTypeIds.kotlinResult.id, it, length)
            // TODO: modifiers
            // tw.writeHasModifier(length, getModifierKey("public"))
            // tw.writeHasModifier(length, getModifierKey("final"))

            // Note we will only emit one `clone()` method per Java array type, so we choose `Array<C?>` as its Kotlin
            // return type, where C is the component type with any nested arrays themselves invariant and nullable.
            val kotlinCloneReturnType = getInvariantNullableArrayType(arrayType).makeNullable()
            val kotlinCloneReturnTypeLabel = useType(kotlinCloneReturnType).kotlinResult.id

            val clone = tw.getLabelFor<DbMethod>("@\"callable;{$it}.clone(){$it}\"")
            tw.writeMethods(clone, "clone", "clone()", it, kotlinCloneReturnTypeLabel, it, clone)
            // TODO: modifiers
            // tw.writeHasModifier(clone, getModifierKey("public"))
        }

        val javaSignature = "an array" // TODO: Wrong
        val javaResult = TypeResult(id, javaSignature)

        val arrayClassResult = useSimpleTypeClass(arrayType.classifier.owner as IrClass, arrayType.arguments, arrayType.hasQuestionMark)
        return TypeResults(javaResult, arrayClassResult.kotlinResult)
    }

    fun useSimpleType(s: IrSimpleType, canReturnPrimitiveTypes: Boolean): TypeResults {
        if (s.abbreviation != null) {
            // TODO: Extract this information
            logger.warn(Severity.ErrorSevere, "Type alias ignored for " + s.render())
        }
        // We use this when we don't actually have an IrClass for a class
        // we want to refer to
        fun makeClass(pkgName: String, className: String): Label<DbClass> {
            val pkgId = extractPackage(pkgName)
            val label = "@\"class;$pkgName.$className\""
            val classId: Label<DbClass> = tw.getLabelFor(label, {
                tw.writeClasses(it, className, pkgId, it)
            })
            return classId
        }
        fun primitiveType(kotlinClass: IrClass, primitiveName: String?,
                          javaPackageName: String, javaClassName: String,
                          kotlinPackageName: String, kotlinClassName: String): TypeResults {
            val javaResult = if (canReturnPrimitiveTypes && !s.hasQuestionMark && primitiveName != null) {
                    val label: Label<DbPrimitive> = tw.getLabelFor("@\"type;$primitiveName\"", {
                        tw.writePrimitives(it, primitiveName)
                    })
                    TypeResult(label, primitiveName)
                } else {
                    val label = makeClass(javaPackageName, javaClassName)
                    val signature = "$javaPackageName.$javaClassName" // TODO: Is this right?
                    TypeResult(label, signature)
                }
            val kotlinClassId = useClassInstance(kotlinClass, listOf()).classLabel
            val kotlinResult = if (s.hasQuestionMark) {
                    val kotlinSignature = "$kotlinPackageName.$kotlinClassName?" // TODO: Is this right?
                    val kotlinLabel = "@\"kt_type;nullable;$kotlinPackageName.$kotlinClassName\""
                    val kotlinId: Label<DbKt_nullable_type> = tw.getLabelFor(kotlinLabel, {
                        tw.writeKt_nullable_types(it, kotlinClassId)
                    })
                    TypeResult(kotlinId, kotlinSignature)
                } else {
                    val kotlinSignature = "$kotlinPackageName.$kotlinClassName" // TODO: Is this right?
                    val kotlinLabel = "@\"kt_type;notnull;$kotlinPackageName.$kotlinClassName\""
                    val kotlinId: Label<DbKt_notnull_type> = tw.getLabelFor(kotlinLabel, {
                        tw.writeKt_notnull_types(it, kotlinClassId)
                    })
                    TypeResult(kotlinId, kotlinSignature)
                }
            return TypeResults(javaResult, kotlinResult)
        }

        val primitiveInfo = primitiveTypeMapping[s.classifier.signature]

        when {
/*
XXX delete?
            // temporary fix for type parameters types that would otherwise be primitive types
            !canReturnPrimitiveTypes && (s.isPrimitiveType() || s.isUnsignedType() || s.isString()) -> {
                val classifier: IrClassifierSymbol = s.classifier
                val cls: IrClass = classifier.owner as IrClass

                return useClassInstance(cls, s.arguments)
            }

*/
            primitiveInfo != null -> return primitiveType(
                s.classifier.owner as IrClass,
                primitiveInfo.primitiveName, primitiveInfo.javaPackageName,
                primitiveInfo.javaClassName, primitiveInfo.kotlinPackageName, primitiveInfo.kotlinClassName
            )
/*
TODO: Test case: nullable and has-question-mark type variables:
class X {
    fun <T : Int> f1(t: T?) {
        f1(null)
    }

    fun <T : Int?> f2(t: T) {
        f2(null)
    }
}

TODO: Test case: This breaks kotlinc codegen currently, but up to IR is OK, so we can still have it in a qltest
class X {
    fun <T : Int> f1(t: T?) {
        f1(null)
    }
}
*/

            (s.isBoxedArray && s.arguments.isNotEmpty()) || s.isPrimitiveArray() -> {
                var dimensions = 1
                var isPrimitiveArray = s.isPrimitiveArray()
                val componentType = s.getArrayElementType(pluginContext.irBuiltIns)
                var elementType = componentType
                while (elementType.isBoxedArray || elementType.isPrimitiveArray()) {
                    dimensions++
                    if(elementType.isPrimitiveArray())
                        isPrimitiveArray = true
                    elementType = elementType.getArrayElementType(pluginContext.irBuiltIns)
                }

                return useArrayType(
                    s,
                    componentType,
                    elementType,
                    dimensions,
                    isPrimitiveArray
                )
            }

            s.classifier.owner is IrClass -> {
                val classifier: IrClassifierSymbol = s.classifier
                val cls: IrClass = classifier.owner as IrClass

                return useSimpleTypeClass(cls, s.arguments, s.hasQuestionMark)
            }
            s.classifier.owner is IrTypeParameter -> {
                val javaId = useTypeParameter(s.classifier.owner as IrTypeParameter)
                val javaSignature = "TODO"
                val javaResult = TypeResult(javaId, javaSignature)
                val aClassId = makeClass("kotlin", "TypeParam") // TODO: Wrong
                val kotlinResult = if (s.hasQuestionMark) {
                        val kotlinSignature = "$javaSignature?" // TODO: Wrong
                        val kotlinLabel = "@\"kt_type;nullable;type_param\"" // TODO: Wrong
                        val kotlinId: Label<DbKt_nullable_type> = tw.getLabelFor(kotlinLabel, {
                            tw.writeKt_nullable_types(it, aClassId)
                        })
                        TypeResult(kotlinId, kotlinSignature)
                    } else {
                        val kotlinSignature = "$javaSignature" // TODO: Wrong
                        val kotlinLabel = "@\"kt_type;notnull;type_param\"" // TODO: Wrong
                        val kotlinId: Label<DbKt_notnull_type> = tw.getLabelFor(kotlinLabel, {
                            tw.writeKt_notnull_types(it, aClassId)
                        })
                        TypeResult(kotlinId, kotlinSignature)
                    }
                return TypeResults(javaResult, kotlinResult)
            }
            else -> {
                logger.warn(Severity.ErrorSevere, "Unrecognised IrSimpleType: " + s.javaClass + ": " + s.render())
                return TypeResults(TypeResult(fakeLabel(), "unknown"), TypeResult(fakeLabel(), "unknown"))
            }
        }
    }

    fun useDeclarationParent(dp: IrDeclarationParent): Label<out DbElement> =
        when(dp) {
            is IrFile -> usePackage(dp.fqName.asString())
            is IrClass -> useClassSource(dp)
            is IrFunction -> useFunction(dp)
            else -> {
                logger.warn(Severity.ErrorSevere, "Unrecognised IrDeclarationParent: " + dp.javaClass)
                fakeLabel()
            }
        }

    fun getFunctionLabel(f: IrFunction) : String {
        return getFunctionLabel(f.parent, f.name.asString(), f.valueParameters, f.returnType)
    }

    fun getFunctionLabel(
        parent: IrDeclarationParent,
        name: String,
        parameters: List<IrValueParameter>,
        returnType: IrType
    ): String {
        val paramTypeIds = parameters.joinToString { "{${useType(erase(it.type)).javaResult.id}}" }
        val returnTypeId = useType(erase(returnType)).javaResult.id
        val parentId = useDeclarationParent(parent)
        return "@\"callable;{$parentId}.$name($paramTypeIds){$returnTypeId}\""
    }

    fun <T: DbCallable> useFunction(f: IrFunction): Label<out T> {
        val label = getFunctionLabel(f)
        val id: Label<T> = tw.getLabelFor(label)
        if(isExternalDeclaration(f)) {
            extractExternalEnclosingClassLater(f)
        }
        return id
    }

    fun getTypeArgumentLabel(
        arg: IrTypeArgument
    ): Label<out DbReftype> {

        fun extractBoundedWildcard(wildcardKind: Int, wildcardLabelStr: String, boundLabel: Label<out DbReftype>): Label<DbWildcard> =
            tw.getLabelFor(wildcardLabelStr) { wildcardLabel ->
                tw.writeWildcards(wildcardLabel, typeArgShortName(arg), wildcardKind)
                tw.writeHasLocation(wildcardLabel, tw.unknownLocation)
                tw.getLabelFor<DbTypebound>("@\"bound;0;{$wildcardLabel}\"") {
                    tw.writeTypeBounds(it, boundLabel, 0, wildcardLabel)
                }
            }

        return when (arg) {
            is IrStarProjection -> {
                @Suppress("UNCHECKED_CAST")
                val anyTypeLabel = useType(pluginContext.irBuiltIns.anyType).javaResult.id as Label<out DbReftype>
                extractBoundedWildcard(1, "@\"wildcard;\"", anyTypeLabel)
            }
            is IrTypeProjection -> {
                @Suppress("UNCHECKED_CAST")
                val boundLabel = useType(arg.type, false).javaResult.id as Label<out DbReftype>

                return if(arg.variance == Variance.INVARIANT)
                    boundLabel
                else {
                    val keyPrefix = if (arg.variance == Variance.IN_VARIANCE) "super" else "extends"
                    val wildcardKind = if (arg.variance == Variance.IN_VARIANCE) 2 else 1
                    extractBoundedWildcard(wildcardKind, "@\"wildcard;$keyPrefix{$boundLabel}\"", boundLabel)
                }
            }
            else -> {
                logger.warn(Severity.ErrorSevere, "Unexpected type argument.")
                return fakeLabel()
            }
        }
    }

    /*
    This returns the `X` in c's label `@"class;X"`.
    */
    private fun getUnquotedClassLabel(c: IrClass, typeArgs: List<IrTypeArgument>): String {
        val pkg = c.packageFqName?.asString() ?: ""
        val cls = c.name.asString()
        var label: String
        val parent = c.parent
        label = if (parent is IrClass) {
            // todo: fix this. Ugly string concat to handle nested class IDs.
            // todo: Can the containing class have type arguments?
            "${getUnquotedClassLabel(parent, listOf())}\$$cls"
        } else {
            if (pkg.isEmpty()) cls else "$pkg.$cls"
        }

        for (arg in typeArgs) {
            val argId = getTypeArgumentLabel(arg)
            label += ";{$argId}"
        }

        return label
    }

    fun getClassLabel(c: IrClass, typeArgs: List<IrTypeArgument>) =
        "@\"class;${getUnquotedClassLabel(c, typeArgs)}\""

    fun useClassSource(c: IrClass): Label<out DbClassorinterface> {
        // For source classes, the label doesn't include and type arguments
        val args = listOf<IrTypeArgument>()
        val classId = getClassLabel(c, args)
        return tw.getLabelFor(classId)
    }

    fun getTypeParameterLabel(param: IrTypeParameter): String {
        val parentLabel = useDeclarationParent(param.parent)
        return "@\"typevar;{$parentLabel};${param.name}\""
    }

    fun useTypeParameter(param: IrTypeParameter): Label<out DbTypevariable> {
        val l = getTypeParameterLabel(param)
        val label = tw.getExistingLabelFor<DbTypevariable>(l)
        if (label != null) {
            return label
        }

        logger.warn(Severity.ErrorSevere, "Missing type parameter label")
        return tw.getLabelFor(l)
    }

    fun extractModifier(m: String): Label<DbModifier> {
        val modifierLabel = "@\"modifier;$m\""
        val id: Label<DbModifier> = tw.getLabelFor(modifierLabel, {
            tw.writeModifiers(it, m)
        })
        return id
    }

    fun extractClassModifiers(c: IrClass, id: Label<out DbClassorinterface>) {
        if (c.modality == Modality.ABSTRACT) {
            tw.writeHasModifier(id, extractModifier("abstract"))
        }
    }

    fun extractClassSupertypes(c: IrClass, id: Label<out DbReftype>) {
        for(t in c.superTypes) {
            when(t) {
                is IrSimpleType -> {
                    when (t.classifier.owner) {
                        is IrClass -> {
                            val classifier: IrClassifierSymbol = t.classifier
                            val tcls: IrClass = classifier.owner as IrClass
                            val l = useClassInstance(tcls, t.arguments).classLabel
                            tw.writeExtendsReftype(id, l)
                        }
                        else -> {
                            logger.warn(Severity.ErrorSevere, "Unexpected simple type supertype: " + t.javaClass + ": " + t.render())
                        }
                    }
                } else -> {
                    logger.warn(Severity.ErrorSevere, "Unexpected supertype: " + t.javaClass + ": " + t.render())
                }
            }
        }
    }

    fun useValueDeclaration(d: IrValueDeclaration): Label<out DbVariable> =
        when(d) {
            is IrValueParameter -> useValueParameter(d)
            is IrVariable -> useVariable(d)
            else -> {
                logger.warn(Severity.ErrorSevere, "Unrecognised IrValueDeclaration: " + d.javaClass)
                fakeLabel()
            }
        }

    fun erase (t: IrType): IrType {
        if (t is IrSimpleType) {
            val classifier = t.classifier
            val owner = classifier.owner
            if(owner is IrTypeParameter) {
                return erase(owner.superTypes[0])
            }

            // todo: fix this:
            if (t.makeNotNull().isArray()) {
                val elementType = t.getArrayElementType(pluginContext.irBuiltIns)
                val erasedElementType = erase(elementType)
                return withQuestionMark((classifier as IrClassSymbol).typeWith(erasedElementType), t.hasQuestionMark)
            }

            if (owner is IrClass) {
                return withQuestionMark((classifier as IrClassSymbol).typeWith(), t.hasQuestionMark)
            }
        }
        return t
    }

    fun getValueParameterLabel(vp: IrValueParameter): String {
        @Suppress("UNCHECKED_CAST")
        val parentId: Label<out DbMethod> = useDeclarationParent(vp.parent) as Label<out DbMethod>
        val idx = vp.index
        if (idx < 0) {
            // We're not extracting this and this@TYPE parameters of functions:
            logger.warn(Severity.ErrorSevere, "Unexpected negative index for parameter")
        }
        return "@\"params;{$parentId};$idx\""
    }

    fun useValueParameter(vp: IrValueParameter): Label<out DbParam> =
        tw.getLabelFor(getValueParameterLabel(vp))

    fun getFieldLabel(p: IrField): String {
        val parentId = useDeclarationParent(p.parent)
        return "@\"field;{$parentId};${p.name.asString()}\""
    }

    fun useField(p: IrField): Label<out DbField> =
        tw.getLabelFor(getFieldLabel(p))

    fun getPropertyLabel(p: IrProperty): String {
        val parentId = useDeclarationParent(p.parent)
        return "@\"property;{$parentId};${p.name.asString()}\""
    }

    fun useProperty(p: IrProperty): Label<out DbKt_property> =
        tw.getLabelFor(getPropertyLabel(p))

    private fun getEnumEntryLabel(ee: IrEnumEntry): String {
        val parentId = useDeclarationParent(ee.parent)
        return "@\"field;{$parentId};${ee.name.asString()}\""
    }

    fun useEnumEntry(ee: IrEnumEntry): Label<out DbField> =
        tw.getLabelFor(getEnumEntryLabel(ee))

    private fun getTypeAliasLabel(ta: IrTypeAlias): String {
        val parentId = useDeclarationParent(ta.parent)
        return "@\"type_alias;{$parentId};${ta.name.asString()}\""
    }

    fun useTypeAlias(ta: IrTypeAlias): Label<out DbKt_type_alias> =
        tw.getLabelFor(getTypeAliasLabel(ta))

    fun useVariable(v: IrVariable): Label<out DbLocalvar> {
        return tw.getVariableLabelFor<DbLocalvar>(v)
    }

    fun withQuestionMark(t: IrType, hasQuestionMark: Boolean) = if(hasQuestionMark) t.makeNullable() else t.makeNotNull()

}

open class KotlinFileExtractor(
    override val logger: FileLogger,
    override val tw: FileTrapWriter,
    dependencyCollector: TrapFileManager?,
    externalClassExtractor: ExternalClassExtractor,
    pluginContext: IrPluginContext): KotlinUsesExtractor(logger, tw, dependencyCollector, externalClassExtractor, pluginContext) {

    fun extractDeclaration(declaration: IrDeclaration, parentId: Label<out DbReftype>) {
        when (declaration) {
            is IrClass -> extractClassSource(declaration)
            is IrFunction -> extractFunction(declaration, parentId)
            is IrAnonymousInitializer -> {
                // Leaving this intentionally empty. init blocks are extracted during class extraction.
            }
            is IrProperty -> extractProperty(declaration, parentId)
            is IrEnumEntry -> extractEnumEntry(declaration, parentId)
            is IrTypeAlias -> extractTypeAlias(declaration) // TODO: Pass in and use parentId
            else -> logger.warnElement(Severity.ErrorSevere, "Unrecognised IrDeclaration: " + declaration.javaClass, declaration)
        }
    }



    fun getLabel(element: IrElement) : String? {
        when (element) {
            is IrFile -> return "@\"${element.path};sourcefile\"" // todo: remove copy-pasted code
            is IrClass -> return getClassLabel(element, listOf())
            is IrTypeParameter -> return getTypeParameterLabel(element)
            is IrFunction -> return getFunctionLabel(element)
            is IrValueParameter -> return getValueParameterLabel(element)
            is IrProperty -> return getPropertyLabel(element)
            is IrField -> return getFieldLabel(element)

            // Fresh entities:
            is IrBody -> return null
            is IrExpression -> return null

            // todo add others:
            else -> {
                logger.warnElement(Severity.ErrorSevere, "Unhandled element type: ${element::class}", element)
                return null
            }
        }
    }

    fun extractTypeParameter(tp: IrTypeParameter): Label<out DbTypevariable> {
        val id = tw.getLabelFor<DbTypevariable>(getTypeParameterLabel(tp))

        val parentId: Label<out DbClassorinterfaceorcallable> = when (val parent = tp.parent) {
            is IrFunction -> useFunction(parent)
            is IrClass -> useClassSource(parent)
            else -> {
                logger.warnElement(Severity.ErrorSevere, "Unexpected type parameter parent", tp)
                fakeLabel()
            }
        }

        tw.writeTypeVars(id, tp.name.asString(), tp.index, 0, parentId)
        val locId = tw.getLocation(tp)
        tw.writeHasLocation(id, locId)

        // todo: add type bounds

        return id
    }

    fun extractClassInstance(c: IrClass, typeArgs: List<IrTypeArgument>): Label<out DbClassorinterface> {
        if (typeArgs.isEmpty()) {
            logger.warn(Severity.ErrorSevere, "Instance without type arguments: " + c.name.asString())
        }

        val id = addClassLabel(c, typeArgs)
        val pkg = c.packageFqName?.asString() ?: ""
        val cls = classShortName(c, typeArgs)
        val pkgId = extractPackage(pkg)
        if(c.kind == ClassKind.INTERFACE) {
            @Suppress("UNCHECKED_CAST")
            val interfaceId = id as Label<out DbInterface>
            @Suppress("UNCHECKED_CAST")
            val sourceInterfaceId = useClassSource(c) as Label<out DbInterface>
            tw.writeInterfaces(interfaceId, cls, pkgId, sourceInterfaceId)
        } else {
            @Suppress("UNCHECKED_CAST")
            val classId = id as Label<out DbClass>
            @Suppress("UNCHECKED_CAST")
            val sourceClassId = useClassSource(c) as Label<out DbClass>
            tw.writeClasses(classId, cls, pkgId, sourceClassId)

            if (c.kind == ClassKind.ENUM_CLASS) {
                tw.writeIsEnumType(classId)
            }
        }

        for ((idx, arg) in typeArgs.withIndex()) {
            val argId = getTypeArgumentLabel(arg)
            tw.writeTypeArgs(argId, idx, id)
        }
        tw.writeIsParameterized(id)
        val unbound = useClassSource(c)
        tw.writeErasure(id, unbound)
        extractClassModifiers(c, id)
        extractClassSupertypes(c, id)

        val locId = tw.getLocation(c)
        tw.writeHasLocation(id, locId)

        return id
    }

    fun extractClassSource(c: IrClass): Label<out DbClassorinterface> {
        val id = useClassSource(c)
        val pkg = c.packageFqName?.asString() ?: ""
        val cls = c.name.asString()
        val pkgId = extractPackage(pkg)
        if(c.kind == ClassKind.INTERFACE) {
            @Suppress("UNCHECKED_CAST")
            val interfaceId = id as Label<out DbInterface>
            tw.writeInterfaces(interfaceId, cls, pkgId, interfaceId)
        } else {
            @Suppress("UNCHECKED_CAST")
            val classId = id as Label<out DbClass>
            tw.writeClasses(classId, cls, pkgId, classId)

            if (c.kind == ClassKind.ENUM_CLASS) {
                tw.writeIsEnumType(classId)
            }
        }

        val locId = tw.getLocation(c)
        tw.writeHasLocation(id, locId)

        val parent = c.parent
        if (parent is IrClass) {
            val parentId = useClassInstance(parent, listOf()).classLabel
            tw.writeEnclInReftype(id, parentId)
            if(c.isCompanion) {
                // If we are a companion then our parent has a
                //     public static final ParentClass$CompanionObjectClass CompanionObjectName;
                // that we need to fabricate here
                val instance = useCompanionObjectClassInstance(c)
                if(instance != null) {
                    val type = useSimpleTypeClass(c, emptyList(), false)
                    tw.writeFields(instance.id, instance.name, type.javaResult.id, type.kotlinResult.id, id, instance.id)
                    tw.writeHasLocation(instance.id, locId)
                    tw.writeHasModifier(instance.id, extractModifier("public"))
                    tw.writeHasModifier(instance.id, extractModifier("static"))
                    tw.writeHasModifier(instance.id, extractModifier("final"))
                    @Suppress("UNCHECKED_CAST")
                    tw.writeClass_companion_object(parentId as Label<DbClass>, instance.id, id as Label<DbClass>)
                }
            }
        }

        c.typeParameters.map { extractTypeParameter(it) }
        c.declarations.map { extractDeclaration(it, id) }
        extractObjectInitializerFunction(c, id)
        if(c.isNonCompanionObject) {
            // For `object MyObject { ... }`, the .class has an
            // automatically-generated `public static final MyObject INSTANCE`
            // field that may be referenced from Java code, and is used in our
            // IrGetObjectValue support. We therefore need to fabricate it
            // here.
            val instance = useObjectClassInstance(c)
            val type = useSimpleTypeClass(c, emptyList(), false)
            tw.writeFields(instance.id, instance.name, type.javaResult.id, type.kotlinResult.id, id, instance.id)
            tw.writeHasLocation(instance.id, locId)
            tw.writeHasModifier(instance.id, extractModifier("public"))
            tw.writeHasModifier(instance.id, extractModifier("static"))
            tw.writeHasModifier(instance.id, extractModifier("final"))
            @Suppress("UNCHECKED_CAST")
            tw.writeClass_object(id as Label<DbClass>, instance.id)
        }

        extractClassModifiers(c, id)
        extractClassSupertypes(c, id)

        return id
    }

    data class FieldResult(val id: Label<DbField>, val name: String)

    fun useCompanionObjectClassInstance(c: IrClass): FieldResult? {
        val parent = c.parent
        if(!c.isCompanion) {
            logger.warn(Severity.ErrorSevere, "Using companion instance for non-companion class")
            return null
        }
        else if (parent !is IrClass) {
            logger.warn(Severity.ErrorSevere, "Using companion instance for non-companion class")
            return null
        } else {
            val parentId = useClassInstance(parent, listOf()).classLabel
            val instanceName = c.name.asString()
            val instanceLabel = "@\"field;{$parentId};$instanceName\""
            val instanceId: Label<DbField> = tw.getLabelFor(instanceLabel)
            return FieldResult(instanceId, instanceName)
        }
    }

    fun useObjectClassInstance(c: IrClass): FieldResult {
        if(!c.isNonCompanionObject) {
            logger.warn(Severity.ErrorSevere, "Using instance for non-object class")
        }
        val classId = useClassInstance(c, listOf()).classLabel
        val instanceName = "INSTANCE"
        val instanceLabel = "@\"field;{$classId};$instanceName\""
        val instanceId: Label<DbField> = tw.getLabelFor(instanceLabel)
        return FieldResult(instanceId, instanceName)
    }

    fun extractValueParameter(vp: IrValueParameter, parent: Label<out DbCallable>, idx: Int) {
        val id = useValueParameter(vp)
        val type = useType(vp.type)
        val locId = tw.getLocation(vp)
        tw.writeParams(id, type.javaResult.id, type.kotlinResult.id, idx, parent, id)
        tw.writeHasLocation(id, locId)
        tw.writeParamName(id, vp.name.asString())
    }

    private fun extractObjectInitializerFunction(c: IrClass, parentId: Label<out DbReftype>) {
        if (isExternalDeclaration(c)) {
            return
        }

        // add method:
        val obinitLabel = getFunctionLabel(c, "<obinit>", listOf(), pluginContext.irBuiltIns.unitType)
        val obinitId = tw.getLabelFor<DbMethod>(obinitLabel)
        val signature = "TODO"
        val returnType = useType(pluginContext.irBuiltIns.unitType)
        tw.writeMethods(obinitId, "<obinit>", signature, returnType.javaResult.id, returnType.kotlinResult.id, parentId, obinitId)

        val locId = tw.getLocation(c)
        tw.writeHasLocation(obinitId, locId)

        // add body:
        val blockId = tw.getFreshIdLabel<DbBlock>()
        tw.writeStmts_block(blockId, obinitId, 0, obinitId)
        tw.writeHasLocation(blockId, locId)

        // body content with field initializers and init blocks
        var idx = 0
        for (decl in c.declarations) {
            when (decl) {
                is IrProperty -> {
                    val backingField = decl.backingField
                    val initializer = backingField?.initializer

                    if (backingField == null || backingField.isStatic || initializer == null) {
                        continue
                    }

                    val declLocId = tw.getLocation(decl)
                    val stmtId = tw.getFreshIdLabel<DbExprstmt>()
                    tw.writeStmts_exprstmt(stmtId, blockId, idx++, obinitId)
                    tw.writeHasLocation(stmtId, declLocId)
                    val assignmentId = tw.getFreshIdLabel<DbAssignexpr>()
                    val type = useType(initializer.expression.type)
                    tw.writeExprs_assignexpr(assignmentId, type.javaResult.id, type.kotlinResult.id, stmtId, 0)
                    tw.writeHasLocation(assignmentId, declLocId)
                    tw.writeCallableEnclosingExpr(assignmentId, obinitId)

                    val lhsId = tw.getFreshIdLabel<DbVaraccess>()
                    val lhsType = useType(backingField.type)
                    tw.writeExprs_varaccess(lhsId, lhsType.javaResult.id, lhsType.kotlinResult.id, assignmentId, 0)
                    tw.writeHasLocation(lhsId, declLocId)
                    tw.writeCallableEnclosingExpr(lhsId, obinitId)
                    val vId = useField(backingField)
                    tw.writeVariableBinding(lhsId, vId)

                    extractExpressionExpr(initializer.expression, obinitId, assignmentId, 1)
                }
                is IrAnonymousInitializer -> {
                    if (decl.isStatic) {
                        continue
                    }

                    for (stmt in decl.body.statements) {
                        extractStatement(stmt, obinitId, blockId, idx++)
                    }
                }
                else -> continue
            }
        }
    }

    fun extractFunction(f: IrFunction, parentId: Label<out DbReftype>): Label<out DbCallable> {
        currentFunction = f

        f.typeParameters.map { extractTypeParameter(it) }

        val locId = tw.getLocation(f)
        val signature = "TODO"

        val id: Label<out DbCallable>
        if (f.symbol is IrConstructorSymbol) {
            val returnType = useType(erase(f.returnType))
            id = useFunction<DbConstructor>(f)
            tw.writeConstrs(id, f.returnType.classFqName?.shortName()?.asString() ?: f.name.asString(), signature, returnType.javaResult.id, returnType.kotlinResult.id, parentId, id)
        } else {
            val returnType = useType(f.returnType)
            id = useFunction<DbMethod>(f)
            tw.writeMethods(id, f.name.asString(), signature, returnType.javaResult.id, returnType.kotlinResult.id, parentId, id)

            val extReceiver = f.extensionReceiverParameter
            if (extReceiver != null) {
                val extendedType = useType(extReceiver.type)
                tw.writeKtExtensionFunctions(id, extendedType.javaResult.id, extendedType.kotlinResult.id)
            }
        }

        tw.writeHasLocation(id, locId)
        val body = f.body
        if(body != null) {
            extractBody(body, id)
        }
        f.valueParameters.forEachIndexed { i, vp ->
            extractValueParameter(vp, id, i)
        }

        currentFunction = null
        return id
    }

    fun extractField(f: IrField, parentId: Label<out DbReftype>): Label<out DbField> {
        val id = useField(f)
        val locId = tw.getLocation(f)
        val type = useType(f.type)
        tw.writeFields(id, f.name.asString(), type.javaResult.id, type.kotlinResult.id, parentId, id)
        tw.writeHasLocation(id, locId)
        return id
    }

    fun extractProperty(p: IrProperty, parentId: Label<out DbReftype>) {
        val id = useProperty(p)
        val locId = tw.getLocation(p)
        tw.writeKtProperties(id, p.name.asString())
        tw.writeHasLocation(id, locId)

        val bf = p.backingField
        val getter = p.getter
        val setter = p.setter

        if(getter != null) {
            @Suppress("UNCHECKED_CAST")
            val getterId = extractFunction(getter, parentId) as Label<out DbMethod>
            tw.writeKtPropertyGetters(id, getterId)
        } else {
            if (p.modality != Modality.FINAL || !isExternalDeclaration(p)) {
                logger.warnElement(Severity.ErrorSevere, "IrProperty without a getter", p)
            }
        }

        if(setter != null) {
            if(!p.isVar) {
                logger.warnElement(Severity.ErrorSevere, "!isVar property with a setter", p)
            }
            @Suppress("UNCHECKED_CAST")
            val setterId = extractFunction(setter, parentId) as Label<out DbMethod>
            tw.writeKtPropertySetters(id, setterId)
        } else {
            if (p.isVar && !isExternalDeclaration(p)) {
                logger.warnElement(Severity.ErrorSevere, "isVar property without a setter", p)
            }
        }

        if(bf != null) {
            val fieldId = extractField(bf, parentId)
            tw.writeKtPropertyBackingFields(id, fieldId)
        }
    }

    fun extractEnumEntry(ee: IrEnumEntry, parentId: Label<out DbReftype>) {
        val id = useEnumEntry(ee)
        val parent = ee.parent
        if(parent !is IrClass) {
            logger.warnElement(Severity.ErrorSevere, "Enum entry with unexpected parent: " + parent.javaClass, ee)
        } else if (parent.typeParameters.isNotEmpty()) {
            logger.warnElement(Severity.ErrorSevere, "Enum entry parent class has type parameters: " + parent.name, ee)
        } else {
            val type = useSimpleTypeClass(parent, emptyList(), false)
            tw.writeFields(id, ee.name.asString(), type.javaResult.id, type.kotlinResult.id, parentId, id)
            val locId = tw.getLocation(ee)
            tw.writeHasLocation(id, locId)
        }
    }

    fun extractTypeAlias(ta: IrTypeAlias) {
        if (ta.typeParameters.isNotEmpty()) {
            // TODO: Extract this information
            logger.warn(Severity.ErrorSevere, "Type alias type parameters ignored for " + ta.render())
        }
        val id = useTypeAlias(ta)
        val locId = tw.getLocation(ta)
        // TODO: We don't really want to generate any Java types here; we only want the KT type:
        val type = useType(ta.expandedType)
        tw.writeKt_type_alias(id, ta.name.asString(), type.kotlinResult.id)
        tw.writeHasLocation(id, locId)
    }

    fun extractBody(b: IrBody, callable: Label<out DbCallable>) {
        when(b) {
            is IrBlockBody -> extractBlockBody(b, callable, callable, 0)
            else -> logger.warnElement(Severity.ErrorSevere, "Unrecognised IrBody: " + b.javaClass, b)
        }
    }

    fun extractBlockBody(b: IrBlockBody, callable: Label<out DbCallable>, parent: Label<out DbStmtparent>, idx: Int) {
        val id = tw.getFreshIdLabel<DbBlock>()
        val locId = tw.getLocation(b)
        tw.writeStmts_block(id, parent, idx, callable)
        tw.writeHasLocation(id, locId)
        for((sIdx, stmt) in b.statements.withIndex()) {
            extractStatement(stmt, callable, id, sIdx)
        }
    }

    fun extractVariable(v: IrVariable, callable: Label<out DbCallable>, parent: Label<out DbStmtparent>, idx: Int) {
        val stmtId = tw.getFreshIdLabel<DbLocalvariabledeclstmt>()
        val locId = tw.getLocation(v)
        tw.writeStmts_localvariabledeclstmt(stmtId, parent, idx, callable)
        tw.writeHasLocation(stmtId, locId)
        extractVariableExpr(v, callable, stmtId, 1)
    }

    fun extractVariableExpr(v: IrVariable, callable: Label<out DbCallable>, parent: Label<out DbExprparent>, idx: Int) {
        val varId = useVariable(v)
        val exprId = tw.getFreshIdLabel<DbLocalvariabledeclexpr>()
        val locId = tw.getLocation(v)
        val type = useType(v.type)
        tw.writeLocalvars(varId, v.name.asString(), type.javaResult.id, type.kotlinResult.id, exprId)
        tw.writeHasLocation(varId, locId)
        tw.writeExprs_localvariabledeclexpr(exprId, type.javaResult.id, type.kotlinResult.id, parent, idx)
        tw.writeHasLocation(exprId, locId)
        tw.writeCallableEnclosingExpr(exprId, callable)
        val i = v.initializer
        if(i != null) {
            extractExpressionExpr(i, callable, exprId, 0)
        }
    }

    fun extractStatement(s: IrStatement, callable: Label<out DbCallable>, parent: Label<out DbStmtparent>, idx: Int) {
        when(s) {
            is IrExpression -> {
                extractExpressionStmt(s, callable, parent, idx)
            }
            is IrVariable -> {
                extractVariable(s, callable, parent, idx)
            }
            else -> {
                logger.warnElement(Severity.ErrorSevere, "Unrecognised IrStatement: " + s.javaClass, s)
            }
        }
    }

    fun extractCall(c: IrCall, callable: Label<out DbCallable>, parent: Label<out DbExprparent>, idx: Int) {
        val exprId: Label<out DbExpr> = when (c.origin) {
            PLUS -> {
                val id = tw.getFreshIdLabel<DbAddexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_addexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            MINUS -> {
                val id = tw.getFreshIdLabel<DbSubexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_subexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            DIV -> {
                val id = tw.getFreshIdLabel<DbDivexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_divexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            PERC -> {
                val id = tw.getFreshIdLabel<DbRemexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_remexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            EQEQ -> {
                val id = tw.getFreshIdLabel<DbEqexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_eqexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            EXCLEQ -> {
                val id = tw.getFreshIdLabel<DbNeexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_neexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            LT -> {
                val id = tw.getFreshIdLabel<DbLtexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_ltexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            LTEQ -> {
                val id = tw.getFreshIdLabel<DbLeexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_leexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            GT -> {
                val id = tw.getFreshIdLabel<DbGtexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_gtexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            GTEQ -> {
                val id = tw.getFreshIdLabel<DbGeexpr>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                tw.writeExprs_geexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                id
            }
            else -> {
                val id = tw.getFreshIdLabel<DbMethodaccess>()
                val type = useType(c.type)
                val locId = tw.getLocation(c)
                val methodId = useFunction<DbMethod>(c.symbol.owner)
                tw.writeExprs_methodaccess(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                tw.writeCallableBinding(id, methodId)

                // type arguments at index -2, -3, ...
                extractTypeArguments(c, id, callable, -2, true)
                id
            }
        }
        val dr = c.dispatchReceiver
        if(dr != null) {
            extractExpressionExpr(dr, callable, exprId, -1)
        }
        for(i in 0 until c.valueArgumentsCount) {
            val arg = c.getValueArgument(i)
            if(arg != null) {
                extractExpressionExpr(arg, callable, exprId, i)
            }
        }
    }

    private fun extractTypeArguments(
        c: IrFunctionAccessExpression,
        id: Label<out DbExprparent>,
        callable: Label<out DbCallable>,
        startIndex: Int = 0,
        reverse: Boolean = false
    ) {
        for (argIdx in 0 until c.typeArgumentsCount) {
            val arg = c.getTypeArgument(argIdx)!!
            val argType = useType(arg, false)
            val argId = tw.getFreshIdLabel<DbUnannotatedtypeaccess>()
            val mul = if (reverse) -1 else 1
            tw.writeExprs_unannotatedtypeaccess(argId, argType.javaResult.id, argType.kotlinResult.id, id, argIdx * mul + startIndex)
            tw.writeCallableEnclosingExpr(argId, callable)
        }
    }

    private fun extractConstructorCall(
        e: IrFunctionAccessExpression,
        parent: Label<out DbExprparent>,
        idx: Int,
        callable: Label<out DbCallable>
    ) {
        val id = tw.getFreshIdLabel<DbNewexpr>()
        val type = useType(e.type)
        val locId = tw.getLocation(e)
        val methodId = useFunction<DbConstructor>(e.symbol.owner)
        tw.writeExprs_newexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
        tw.writeHasLocation(id, locId)
        tw.writeCallableEnclosingExpr(id, callable)
        tw.writeCallableBinding(id, methodId)
        for (i in 0 until e.valueArgumentsCount) {
            val arg = e.getValueArgument(i)
            if (arg != null) {
                extractExpressionExpr(arg, callable, id, i)
            }
        }
        val dr = e.dispatchReceiver
        if (dr != null) {
            extractExpressionExpr(dr, callable, id, -2)
        }

        if (e.typeArgumentsCount > 0) {
            val typeAccessId = tw.getFreshIdLabel<DbUnannotatedtypeaccess>()
            tw.writeExprs_unannotatedtypeaccess(typeAccessId, type.javaResult.id, type.kotlinResult.id, id, -3)
            tw.writeCallableEnclosingExpr(typeAccessId, callable)
            extractTypeArguments(e, typeAccessId, callable)
        }
    }

    private val loopIdMap: MutableMap<IrLoop, Label<out DbKtloopstmt>> = mutableMapOf()

    private var currentFunction: IrFunction? = null

    abstract inner class StmtExprParent {
        abstract fun stmt(e: IrExpression, callable: Label<out DbCallable>): StmtParent
        abstract fun expr(e: IrExpression, callable: Label<out DbCallable>): ExprParent
    }

    inner class StmtParent(val parent: Label<out DbStmtparent>, val idx: Int): StmtExprParent() {
        override fun stmt(e: IrExpression, callable: Label<out DbCallable>): StmtParent {
            return this
        }
        override fun expr(e: IrExpression, callable: Label<out DbCallable>): ExprParent {
            val id = tw.getFreshIdLabel<DbExprstmt>()
            val locId = tw.getLocation(e)
            tw.writeStmts_exprstmt(id, parent, idx, callable)
            tw.writeHasLocation(id, locId)
            return ExprParent(id, 0)
        }
    }
    inner class ExprParent(val parent: Label<out DbExprparent>, val idx: Int): StmtExprParent() {
        override fun stmt(e: IrExpression, callable: Label<out DbCallable>): StmtParent {
            val id = tw.getFreshIdLabel<DbStmtexpr>()
            val type = useType(e.type)
            val locId = tw.getLocation(e)
            tw.writeExprs_stmtexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
            tw.writeHasLocation(id, locId)
            tw.writeCallableEnclosingExpr(id, callable)
            return StmtParent(id, 0)
        }
        override fun expr(e: IrExpression, callable: Label<out DbCallable>): ExprParent {
            return this
        }
    }

    fun extractExpressionStmt(e: IrExpression, callable: Label<out DbCallable>, parent: Label<out DbStmtparent>, idx: Int) {
        extractExpression(e, callable, StmtParent(parent, idx))
    }

    fun extractExpressionExpr(e: IrExpression, callable: Label<out DbCallable>, parent: Label<out DbExprparent>, idx: Int) {
        extractExpression(e, callable, ExprParent(parent, idx))
    }

    fun extractExpression(e: IrExpression, callable: Label<out DbCallable>, parent: StmtExprParent) {
        when(e) {
            is IrDelegatingConstructorCall -> {
                val stmtParent = parent.stmt(e, callable)

                val irCallable = currentFunction
                if (irCallable == null) {
                    logger.warnElement(Severity.ErrorSevere, "Current function is not set", e)
                    return
                }

                val delegatingClass = e.symbol.owner.parent as IrClass
                val currentClass = irCallable.parent as IrClass

                val id: Label<out DbStmt>
                if (delegatingClass != currentClass) {
                    id = tw.getFreshIdLabel<DbSuperconstructorinvocationstmt>()
                    tw.writeStmts_superconstructorinvocationstmt(id, stmtParent.parent, stmtParent.idx, callable)
                } else {
                    id = tw.getFreshIdLabel<DbConstructorinvocationstmt>()
                    tw.writeStmts_constructorinvocationstmt(id, stmtParent.parent, stmtParent.idx, callable)
                }

                val locId = tw.getLocation(e)
                val methodId = useFunction<DbConstructor>(e.symbol.owner)

                tw.writeHasLocation(id, locId)
                @Suppress("UNCHECKED_CAST")
                tw.writeCallableBinding(id as Label<DbCaller>, methodId)
                for (i in 0 until e.valueArgumentsCount) {
                    val arg = e.getValueArgument(i)
                    if (arg != null) {
                        extractExpressionExpr(arg, callable, id, i)
                    }
                }
                val dr = e.dispatchReceiver
                if (dr != null) {
                    extractExpressionExpr(dr, callable, id, -1)
                }

                // todo: type arguments at index -2, -3, ...
            }
            is IrThrow -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbThrowstmt>()
                val locId = tw.getLocation(e)
                tw.writeStmts_throwstmt(id, stmtParent.parent, stmtParent.idx, callable)
                tw.writeHasLocation(id, locId)
                extractExpressionExpr(e.value, callable, id, 0)
            }
            is IrBreak -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbBreakstmt>()
                tw.writeStmts_breakstmt(id, stmtParent.parent, stmtParent.idx, callable)
                extractBreakContinue(e, id)
            }
            is IrContinue -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbContinuestmt>()
                tw.writeStmts_continuestmt(id, stmtParent.parent, stmtParent.idx, callable)
                extractBreakContinue(e, id)
            }
            is IrReturn -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbReturnstmt>()
                val locId = tw.getLocation(e)
                tw.writeStmts_returnstmt(id, stmtParent.parent, stmtParent.idx, callable)
                tw.writeHasLocation(id, locId)
                extractExpressionExpr(e.value, callable, id, 0)
            }
            is IrTry -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbTrystmt>()
                val locId = tw.getLocation(e)
                tw.writeStmts_trystmt(id, stmtParent.parent, stmtParent.idx, callable)
                tw.writeHasLocation(id, locId)
                extractExpressionExpr(e.tryResult, callable, id, -1)
                val finallyStmt = e.finallyExpression
                if(finallyStmt != null) {
                    extractExpressionExpr(finallyStmt, callable, id, -2)
                }
                for((catchIdx, catchClause) in e.catches.withIndex()) {
                    val catchId = tw.getFreshIdLabel<DbCatchclause>()
                    tw.writeStmts_catchclause(catchId, id, catchIdx, callable)
                    // TODO: Index -1: unannotatedtypeaccess
                    extractVariableExpr(catchClause.catchParameter, callable, catchId, 0)
                    extractExpressionExpr(catchClause.result, callable, catchId, 1)
                }
            }
            is IrContainerExpression -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbBlock>()
                val locId = tw.getLocation(e)
                tw.writeStmts_block(id, stmtParent.parent, stmtParent.idx, callable)
                tw.writeHasLocation(id, locId)
                e.statements.forEachIndexed { i, s ->
                    extractStatement(s, callable, id, i)
                }
            }
            is IrWhileLoop -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbWhilestmt>()
                loopIdMap[e] = id
                val locId = tw.getLocation(e)
                tw.writeStmts_whilestmt(id, stmtParent.parent, stmtParent.idx, callable)
                tw.writeHasLocation(id, locId)
                extractExpressionExpr(e.condition, callable, id, 0)
                val body = e.body
                if(body != null) {
                    extractExpressionStmt(body, callable, id, 1)
                }
                loopIdMap.remove(e)
            }
            is IrDoWhileLoop -> {
                val stmtParent = parent.stmt(e, callable)
                val id = tw.getFreshIdLabel<DbDostmt>()
                loopIdMap[e] = id
                val locId = tw.getLocation(e)
                tw.writeStmts_dostmt(id, stmtParent.parent, stmtParent.idx, callable)
                tw.writeHasLocation(id, locId)
                extractExpressionExpr(e.condition, callable, id, 0)
                val body = e.body
                if(body != null) {
                    extractExpressionStmt(body, callable, id, 1)
                }
                loopIdMap.remove(e)
            }
            is IrInstanceInitializerCall -> {
                val exprParent = parent.expr(e, callable)
                val irCallable = currentFunction
                if (irCallable == null) {
                    logger.warnElement(Severity.ErrorSevere, "Current function is not set", e)
                    return
                }

                if (irCallable is IrConstructor && irCallable.isPrimary) {
                    // Todo add parameter to field assignments
                }

                // Add call to <obinit>:
                val id = tw.getFreshIdLabel<DbMethodaccess>()
                val type = useType(e.type)
                val locId = tw.getLocation(e)
                val methodLabel = getFunctionLabel(irCallable.parent, "<obinit>", listOf(), e.type)
                val methodId = tw.getLabelFor<DbMethod>(methodLabel)
                tw.writeExprs_methodaccess(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                tw.writeCallableBinding(id, methodId)
            }
            is IrConstructorCall -> {
                val exprParent = parent.expr(e, callable)
                extractConstructorCall(e, exprParent.parent, exprParent.idx, callable)
            }
            is IrEnumConstructorCall -> {
                val exprParent = parent.expr(e, callable)
                extractConstructorCall(e, exprParent.parent, exprParent.idx, callable)
            }
            is IrCall -> {
                val exprParent = parent.expr(e, callable)
                extractCall(e, callable, exprParent.parent, exprParent.idx)
            }
            is IrStringConcatenation -> {
                val exprParent = parent.expr(e, callable)
                val id = tw.getFreshIdLabel<DbStringtemplateexpr>()
                val type = useType(e.type)
                val locId = tw.getLocation(e)
                tw.writeExprs_stringtemplateexpr(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                e.arguments.forEachIndexed { i, a ->
                    extractExpressionExpr(a, callable, id, i)
                }
            }
            is IrConst<*> -> {
                val exprParent = parent.expr(e, callable)
                when(val v = e.value) {
                    is Int, is Short, is Byte -> {
                        val id = tw.getFreshIdLabel<DbIntegerliteral>()
                        val type = useType(e.type)
                        val locId = tw.getLocation(e)
                        tw.writeExprs_integerliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                        tw.writeNamestrings(v.toString(), v.toString(), id)
                    } is Long -> {
                        val id = tw.getFreshIdLabel<DbLongliteral>()
                        val type = useType(e.type)
                        val locId = tw.getLocation(e)
                        tw.writeExprs_longliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                        tw.writeNamestrings(v.toString(), v.toString(), id)
                    } is Float -> {
                        val id = tw.getFreshIdLabel<DbFloatingpointliteral>()
                        val type = useType(e.type)
                        val locId = tw.getLocation(e)
                        tw.writeExprs_floatingpointliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                        tw.writeNamestrings(v.toString(), v.toString(), id)
                    } is Double -> {
                        val id = tw.getFreshIdLabel<DbDoubleliteral>()
                        val type = useType(e.type)
                        val locId = tw.getLocation(e)
                        tw.writeExprs_doubleliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                        tw.writeNamestrings(v.toString(), v.toString(), id)
                    } is Boolean -> {
                        val id = tw.getFreshIdLabel<DbBooleanliteral>()
                        val type = useType(e.type)
                        val locId = tw.getLocation(e)
                        tw.writeExprs_booleanliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                        tw.writeNamestrings(v.toString(), v.toString(), id)
                    } is Char -> {
                        val id = tw.getFreshIdLabel<DbCharacterliteral>()
                        val type = useType(e.type)
                        val locId = tw.getLocation(e)
                        tw.writeExprs_characterliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                        tw.writeNamestrings(v.toString(), v.toString(), id)
                    } is String -> {
                        val id = tw.getFreshIdLabel<DbStringliteral>()
                        val type = useType(e.type)
                        val locId = tw.getLocation(e)
                        tw.writeExprs_stringliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                        tw.writeNamestrings(v.toString(), v.toString(), id)
                    }
                    null -> {
                        val id = tw.getFreshIdLabel<DbNullliteral>()
                        val type = useType(e.type) // class;kotlin.Nothing
                        val locId = tw.getLocation(e)
                        tw.writeExprs_nullliteral(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                        tw.writeHasLocation(id, locId)
                        tw.writeCallableEnclosingExpr(id, callable)
                    }
                    else -> {
                        logger.warnElement(Severity.ErrorSevere, "Unrecognised IrConst: " + v.javaClass, e)
                    }
                }
            }
            is IrGetValue -> {
                val exprParent = parent.expr(e, callable)
                val owner = e.symbol.owner
                if (owner is IrValueParameter && owner.index == -1) {
                    val id = tw.getFreshIdLabel<DbThisaccess>()
                    val type = useType(e.type)
                    val locId = tw.getLocation(e)
                    tw.writeExprs_thisaccess(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                    tw.writeHasLocation(id, locId)
                    tw.writeCallableEnclosingExpr(id, callable)

                    when(val ownerParent = owner.parent) {
                        is IrFunction -> {
                            if (ownerParent.dispatchReceiverParameter == owner &&
                                ownerParent.extensionReceiverParameter != null) {
                                logger.warnElement(Severity.ErrorSevere, "Function-qualifier for this", e)
                            }
                        }
                        is IrClass -> {
                            if (ownerParent.thisReceiver == owner) {
                                val qualId = tw.getFreshIdLabel<DbUnannotatedtypeaccess>()
                                // TODO: Type arguments
                                val qualType = useSimpleTypeClass(ownerParent, listOf(), false)
                                tw.writeExprs_unannotatedtypeaccess(qualId, qualType.javaResult.id, qualType.kotlinResult.id, id, 0)
                                tw.writeHasLocation(qualId, locId)
                                tw.writeCallableEnclosingExpr(qualId, callable)
                            }
                        }
                        else -> {
                            logger.warnElement(Severity.ErrorSevere, "Unexpected owner parent for this access: " + ownerParent.javaClass, e)
                        }
                    }
                } else {
                    val id = tw.getFreshIdLabel<DbVaraccess>()
                    val type = useType(e.type)
                    val locId = tw.getLocation(e)
                    tw.writeExprs_varaccess(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                    tw.writeHasLocation(id, locId)
                    tw.writeCallableEnclosingExpr(id, callable)

                    val vId = useValueDeclaration(owner)
                    tw.writeVariableBinding(id, vId)
                }
            }
            is IrGetField -> {
                val exprParent = parent.expr(e, callable)
                val id = tw.getFreshIdLabel<DbVaraccess>()
                val type = useType(e.type)
                val locId = tw.getLocation(e)
                tw.writeExprs_varaccess(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                val owner = e.symbol.owner
                val vId = useField(owner)
                tw.writeVariableBinding(id, vId)
            }
            is IrGetEnumValue -> {
                val exprParent = parent.expr(e, callable)
                val id = tw.getFreshIdLabel<DbVaraccess>()
                val type = useType(e.type)
                val locId = tw.getLocation(e)
                tw.writeExprs_varaccess(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                val owner = e.symbol.owner
                val vId = useEnumEntry(owner)
                tw.writeVariableBinding(id, vId)
            }
            is IrSetValue,
            is IrSetField -> {
                val exprParent = parent.expr(e, callable)
                val id = tw.getFreshIdLabel<DbAssignexpr>()
                val type = useType(e.type)
                val locId = tw.getLocation(e)
                tw.writeExprs_assignexpr(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)

                val lhsId = tw.getFreshIdLabel<DbVaraccess>()
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(lhsId, callable)

                when (e) {
                    is IrSetValue -> {
                        val lhsType = useType(e.symbol.owner.type)
                        tw.writeExprs_varaccess(lhsId, lhsType.javaResult.id, lhsType.kotlinResult.id, id, 0)
                        val vId = useValueDeclaration(e.symbol.owner)
                        tw.writeVariableBinding(lhsId, vId)
                        extractExpressionExpr(e.value, callable, id, 1)
                    }
                    is IrSetField -> {
                        val lhsType = useType(e.symbol.owner.type)
                        tw.writeExprs_varaccess(lhsId, lhsType.javaResult.id, lhsType.kotlinResult.id, id, 0)
                        val vId = useField(e.symbol.owner)
                        tw.writeVariableBinding(lhsId, vId)
                        extractExpressionExpr(e.value, callable, id, 1)
                    }
                    else -> {
                        logger.warnElement(Severity.ErrorSevere, "Unhandled IrSet* element.", e)
                    }
                }
            }
            is IrWhen -> {
                val exprParent = parent.expr(e, callable)
                val id = tw.getFreshIdLabel<DbWhenexpr>()
                val type = useType(e.type)
                val locId = tw.getLocation(e)
                tw.writeExprs_whenexpr(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                if(e.origin == IF) {
                    tw.writeWhen_if(id)
                }
                e.branches.forEachIndexed { i, b ->
                    val bId = tw.getFreshIdLabel<DbWhenbranch>()
                    val bLocId = tw.getLocation(b)
                    tw.writeWhen_branch(bId, id, i)
                    tw.writeHasLocation(bId, bLocId)
                    extractExpressionExpr(b.condition, callable, bId, 0)
                    extractExpressionStmt(b.result, callable, bId, 1)
                    if(b is IrElseBranch) {
                        tw.writeWhen_branch_else(bId)
                    }
                }
            }
            is IrGetClass -> {
                val exprParent = parent.expr(e, callable)
                val id = tw.getFreshIdLabel<DbGetclassexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_getclassexpr(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                extractExpressionExpr(e.argument, callable, id, 0)
            }
            is IrTypeOperatorCall -> {
                val exprParent = parent.expr(e, callable)
                extractTypeOperatorCall(e, callable, exprParent.parent, exprParent.idx)
            }
            is IrVararg -> {
                val exprParent = parent.expr(e, callable)
                val id = tw.getFreshIdLabel<DbVarargexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_varargexpr(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                e.elements.forEachIndexed { i, arg -> extractVarargElement(arg, callable, id, i) }
            }
            is IrGetObjectValue -> {
                // For `object MyObject { ... }`, the .class has an
                // automatically-generated `public static final MyObject INSTANCE`
                // field that we are accessing here.
                val exprParent = parent.expr(e, callable)
                val c: IrClass = e.symbol.owner
                val instance = if (c.isCompanion) useCompanionObjectClassInstance(c) else useObjectClassInstance(c)

                if(instance != null) {
                    val id = tw.getFreshIdLabel<DbVaraccess>()
                    val type = useType(e.type)
                    val locId = tw.getLocation(e)
                    tw.writeExprs_varaccess(id, type.javaResult.id, type.kotlinResult.id, exprParent.parent, exprParent.idx)
                    tw.writeHasLocation(id, locId)
                    tw.writeCallableEnclosingExpr(id, callable)

                    tw.writeVariableBinding(id, instance.id)
                }
            }
            else -> {
                logger.warnElement(Severity.ErrorSevere, "Unrecognised IrExpression: " + e.javaClass, e)
            }
        }
    }

    fun extractVarargElement(e: IrVarargElement, callable: Label<out DbCallable>, parent: Label<out DbExprparent>, idx: Int) {
        when(e) {
            is IrExpression -> {
                extractExpressionExpr(e, callable, parent, idx)
            }
            else -> {
                logger.warnElement(Severity.ErrorSevere, "Unrecognised IrVarargElement: " + e.javaClass, e)
            }
        }
    }

    fun extractTypeAccess(t: IrType, callable: Label<out DbCallable>, parent: Label<out DbExprparent>, idx: Int, elementForLocation: IrElement) {
        // TODO: elementForLocation allows us to give some sort of
        // location, but a proper location for the type access will
        // require upstream changes
        val type = useType(t)
        val id = tw.getFreshIdLabel<DbUnannotatedtypeaccess>()
        tw.writeExprs_unannotatedtypeaccess(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
        val locId = tw.getLocation(elementForLocation)
        tw.writeHasLocation(id, locId)
        tw.writeCallableEnclosingExpr(id, callable)
    }

    fun extractTypeOperatorCall(e: IrTypeOperatorCall, callable: Label<out DbCallable>, parent: Label<out DbExprparent>, idx: Int) {
        when(e.operator) {
            IrTypeOperator.CAST -> {
                val id = tw.getFreshIdLabel<DbCastexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_castexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                extractTypeAccess(e.typeOperand, callable, id, 0, e)
                extractExpressionExpr(e.argument, callable, id, 1)
            }
            IrTypeOperator.IMPLICIT_CAST -> {
                // TODO: Make this distinguishable from an explicit cast?
                val id = tw.getFreshIdLabel<DbCastexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_castexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                extractTypeAccess(e.typeOperand, callable, id, 0, e)
                extractExpressionExpr(e.argument, callable, id, 1)
            }
            IrTypeOperator.IMPLICIT_NOTNULL -> {
                // TODO: Make this distinguishable from an explicit cast?
                val id = tw.getFreshIdLabel<DbCastexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_castexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                extractTypeAccess(e.typeOperand, callable, id, 0, e)
                extractExpressionExpr(e.argument, callable, id, 1)
            }
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> {
                // TODO: Make this distinguishable from an explicit cast?
                val id = tw.getFreshIdLabel<DbCastexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_castexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                extractTypeAccess(e.typeOperand, callable, id, 0, e)
                extractExpressionExpr(e.argument, callable, id, 1)
            }
            IrTypeOperator.INSTANCEOF -> {
                val id = tw.getFreshIdLabel<DbInstanceofexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_instanceofexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                extractExpressionExpr(e.argument, callable, id, 0)
                extractTypeAccess(e.typeOperand, callable, id, 1, e)
            }
            IrTypeOperator.NOT_INSTANCEOF -> {
                val id = tw.getFreshIdLabel<DbNotinstanceofexpr>()
                val locId = tw.getLocation(e)
                val type = useType(e.type)
                tw.writeExprs_notinstanceofexpr(id, type.javaResult.id, type.kotlinResult.id, parent, idx)
                tw.writeHasLocation(id, locId)
                tw.writeCallableEnclosingExpr(id, callable)
                extractExpressionExpr(e.argument, callable, id, 0)
                extractTypeAccess(e.typeOperand, callable, id, 1, e)
            }
            else -> {
                logger.warnElement(Severity.ErrorSevere, "Unrecognised IrTypeOperatorCall: " + e.render(), e)
            }
        }
    }

    private fun extractBreakContinue(
        e: IrBreakContinue,
        id: Label<out DbBreakcontinuestmt>
    ) {
        val locId = tw.getLocation(e)
        tw.writeHasLocation(id, locId)
        val label = e.label
        if (label != null) {
            tw.writeNamestrings(label, "", id)
        }

        val loopId = loopIdMap[e.loop]
        if (loopId == null) {
            logger.warnElement(Severity.ErrorSevere, "Missing break/continue target", e)
            return
        }

        tw.writeKtBreakContinueTargets(id, loopId)
    }
}
