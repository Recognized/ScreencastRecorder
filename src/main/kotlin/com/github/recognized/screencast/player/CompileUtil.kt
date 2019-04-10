package com.github.recognized.screencast.player

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.testGuiFramework.recorder.actions.PerformScriptAction
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.jar.JarFile

private val log = Logger("compiler")

object CompileUtil {

  private val localCompiler: LocalCompiler by lazy { LocalCompiler() }

  fun compileAndRun(codeString: String) {
    localCompiler.compileAndRunOnPooledThread(MyScriptWrapper.wrapScript(codeString).also { log.info { it } },
        getAllUrls().map { Paths.get(it.toURI()).toFile().path })

  }

  private fun getAllUrls(): List<URL> {
    if (ServiceManager::class.java.classLoader.javaClass.name.contains("Launcher\$AppClassLoader")) {
      //lets substitute jars with a common lib dir to avoid Windows long path error
      val urls = ServiceManager::class.java.classLoader.forcedUrls()
      val libUrl = urls.first { url ->
        (url.file.endsWith("idea.jar") && File(url.path).parentFile.name == "lib")
      }.getParentURL()
      urls.filter { url -> !url.file.startsWith(libUrl.file) }.plus(libUrl).toSet()
      if (!ApplicationManager.getApplication().isUnitTestMode)
        urls.plus(ServiceManager::class.java.classLoader.forcedBaseUrls())
      return urls.toList()
    }

    val set = mutableSetOf<URL>()
    set.addAll(ServiceManager::class.java.classLoader.forcedUrls())
    set.addAll(PerformScriptAction::class.java.classLoader.forcedUrls())
    set.addAll(PerformScriptAction::class.java.classLoader.forcedBaseUrls())
    set.addAll(ScreencastPlayerController::class.java.classLoader.forcedBaseUrls())
    if (!ApplicationManager.getApplication().isUnitTestMode)
      set.addAll(ServiceManager::class.java.classLoader.forcedBaseUrls())
    expandClasspathInJar(set)
    return set.toList()
  }

  private fun expandClasspathInJar(setOfUrls: MutableSet<URL>) {
    val classpathUrl = setOfUrls.firstOrNull { Regex("classpath\\d*.jar").containsMatchIn(it.path) || it.path.endsWith("pathing.jar") }
    if (classpathUrl != null) {
      val classpathFile = Paths.get(classpathUrl.toURI()).toFile()
      if (!classpathFile.exists()) return
      val classpathLine = JarFile(classpathFile).manifest.mainAttributes.getValue("Class-Path")
      val classpathList = classpathLine.split(" ").filter { it.startsWith("file") }.map { URL(it) }
      setOfUrls.addAll(classpathList)
      setOfUrls.remove(classpathUrl)
    }
  }

  private fun URL.getParentURL() = File(this.file).parentFile.toURI().toURL()!!

  private fun ClassLoader.forcedUrls(): List<URL> {
    var methodName = "getUrls"
    val methodAlternativeName = "getURLs"

    if (this.javaClass.methods.any { mtd -> mtd.name == methodAlternativeName }) methodName = methodAlternativeName
    val method = this.javaClass.getMethod(methodName)
    method.isAccessible
    val methodResult = method.invoke(this)
    val myList: List<*> = (methodResult as? Array<*>)?.asList() ?: methodResult as List<*>
    return myList.filterIsInstance(URL::class.java)
  }

  private fun ClassLoader.forcedBaseUrls(): List<URL> {
    try {
      return ((this.javaClass.getMethod("getBaseUrls").invoke(this) as? List<*>)!!
          .filterIsInstance(URL::class.java)
          .map { if (it.protocol == "jar") URL(it.toString().removeSurrounding("jar:", "!/")) else it })

    }
    catch (e: NoSuchMethodException) {
      return emptyList()
    }
  }
}