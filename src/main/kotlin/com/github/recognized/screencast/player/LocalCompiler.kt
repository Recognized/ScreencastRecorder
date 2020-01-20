package com.github.recognized.screencast.player

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.recorder.compile.ModuleXmlBuilder
import com.intellij.util.download.DownloadableFileService
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class LocalCompiler {
    private val kotlinCompilerJarUrl = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler/1.3.61/kotlin-compiler-1.3.61.jar"
    private val kotlinRuntimeJarUrl = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-runtime/1.2.71/kotlin-runtime-1.2.71.jar"
    private val kotlinCompilerJarName = "kotlin-compiler-1.3.61.jar"
    private val kotlinRuntimeJarName = "kotlin-runtime-1.2.71.jar"
    
    private val tempDir by lazy { FileUtil.createTempDirectory("kotlin-compiler-tmp", null, true) }
    private var tempFile: File? = null
    
    fun compileAndGet(code: String, classpath: List<String>): Scenario {
        compile(code, classpath)
        return findCompiled()
    }
    
    // alternative way to run compiled code with pluginClassloader built especially for this file
    private fun findCompiled(): Scenario {
        val testUrl = tempDir.toURI().toURL()
        val classLoadersArray = try {
            // run testGuiTest gradle configuration
            ApplicationManager::class.java.classLoader.loadClass("com.intellij.testGuiFramework.impl.GuiTestCase")
            arrayOf(ApplicationManager::class.java.classLoader)
        } catch (cfe: ClassNotFoundException) {
            arrayOf(ApplicationManager::class.java.classLoader, this.javaClass.classLoader)
        }
        val myPluginClassLoader = PluginClassLoader(listOf(testUrl), classLoadersArray, PluginId.getId("SubGuiScriptRecorder"), null, null)
        val currentTest = myPluginClassLoader.loadClass(MyScriptWrapper.CLASS_NAME) ?: throw Exception(
                "Unable to load by pluginClassLoader ${MyScriptWrapper.CLASS_NAME}.class file")
        val testCase = currentTest.newInstance()
        val testMethod = currentTest.getMethod(MyScriptWrapper.METHOD_NAME)
        return testMethod.invoke(testCase) as Scenario
    }
    
    private fun compile(code: String, classpath: List<String>): Boolean = compile(createTempFile(code), classpath)
    
    private fun compile(fileKt: File, classpath: List<String>): Boolean {
        val kotlinCompilerJar = getKotlinCompilerJar()
        val libDirLocation = getApplicationLibDir().parentFile
        val classPath = classpath.filter { it.contains("plugins") || it.contains("jbre") }
        
        val compilationProcessBuilder = if (SystemInfo.isWindows) {
            getProcessBuilderForWin(kotlinCompilerJar, libDirLocation, classPath, fileKt)
        } else {
            ProcessBuilder("java", "-jar", kotlinCompilerJar.path, "-kotlin-home", "/home/recog/.local/share/JetBrains/Toolbox/apps/IDEA-U/ch-1/193.5662.31/plugins/Kotlin/", "-d", tempDir.path, "-cp", buildClasspath(classPath), fileKt.path)
        }
        val process = compilationProcessBuilder.start()
        val wait = process.waitFor(120, TimeUnit.MINUTES)
        assert(wait)
        if (process.exitValue() == 1) {
            throw CompilationException(BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining("\n")))
        }
        return wait
    }
    
    private fun getKotlinCompilerJar(): File {
        val kotlinCompilerDir = getPluginKotlincDir()
        if (!isKotlinCompilerDir(kotlinCompilerDir)) {
            downloadKotlinCompilerJar(kotlinCompilerDir.path, kotlinCompilerJarUrl, kotlinCompilerJarName)
        }
        return kotlinCompilerDir.listFiles().orEmpty().firstOrNull { file -> file.name.contains("kotlin-compiler") }
                ?: throw FileNotFoundException("Unable to find kotlin-compiler*.jar in ${kotlinCompilerDir.path} directory")
    }
    
    private fun getApplicationLibDir(): File {
        return File(PathManager.getLibPath())
    }
    
    private fun getPluginKotlincDir(): File {
        val tempDirFile = File(PathManager.getTempPath())
        FileUtil.ensureExists(tempDirFile)
        return tempDirFile
    }
    
    private fun isKotlinCompilerDir(dir: File): Boolean = dir.listFiles().orEmpty().any { file ->
        file.name.contains("kotlin-compiler")
    }
    
    private fun downloadKotlinCompilerJar(destDirPath: String?, url: String, file: String): File {
        val downloader = DownloadableFileService.getInstance()
        val description = downloader.createFileDescription(url, file)
        ApplicationManager.getApplication().invokeAndWait {
            downloader.createDownloader(listOf(description), file).downloadFilesWithProgress(destDirPath, null, null)
        }
        return File(destDirPath + File.separator + file)
    }
    
    class CompilationException(string: String) : Exception(string)
    
    private fun buildClasspath(cp: List<String>): String {
        if (SystemInfo.isWindows) {
            val ideaJar = "idea.jar"
            val ideaJarPath = cp.find { pathStr -> pathStr.endsWith("${File.separator}$ideaJar") }
            val ideaLibPath = ideaJarPath!!.substring(startIndex = 0, endIndex = ideaJarPath.length - ideaJar.length - File.separator.length)
            return cp.filterNot { pathStr -> pathStr.startsWith(ideaLibPath) }.plus(ideaLibPath).joinToString(";")
        } else return cp.joinToString(":")
    }
    
    private fun getProcessBuilderForWin(kotlinCompilerJar: File, libDirLocation: File, classpath: List<String>,
                                        scriptKt: File): ProcessBuilder {
        
        fun createTempFile(content: String, fileName: String, extension: String): File {
            val tempFile = FileUtil.createTempFile(fileName, extension, true)
            FileUtil.writeToFile(tempFile, content, false)
            this.tempFile = tempFile
            return tempFile
        }
        
        val moduleXmlFile = createTempFile(
                content = ModuleXmlBuilder.build(outputDir = tempDir.path, classPath = classpath, sourcePath = scriptKt.path),
                fileName = "module",
                extension = ".xml"
        )
        return ProcessBuilder(
                "java",
                "-jar",
                kotlinCompilerJar.path,
                "-kotlin-home",
                libDirLocation.path,
                "-module",
                moduleXmlFile.path,
                scriptKt.path
        )
    }
}
