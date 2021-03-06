package de.tud.cs.peaks.slicing

import java.net.URL

import de.tud.cs.peaks.capabilities.LibraryCapabilityAnalysis
import org.opalj.ai.analyses.cg.{CHACallGraphAlgorithmConfiguration, CallGraph, CallGraphFactory, ExtVTACallGraphAlgorithmConfiguration}
import org.opalj.br.{ClassFile, Method, ObjectType}
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.{GETSTATIC, Instruction}

import scala.collection.mutable

trait ContextSlicing {
  def sliceByContext(project: Project[URL], appContext: Set[String]): Map[ClassFile, Set[Method]] = {
    // 1. determine application

    def toJarSource(u: URL): String = u.toString().substring("jar:file:".length, u.toString().indexOf("!"))


    val projectSources: Iterable[String] = project.projectClassFilesWithSources.map(e => toJarSource(e._2)).toSet.filterNot(s => s.contains("resources/jre_7.0_60/rt.jar"))

    val libSources: Set[String] = projectSources.filterNot(s => appContext.contains(s)).toSet
    val appSources: Set[String] = projectSources.filter(s => appContext.contains(s)).toSet

    val cg = buildCallGraph(project)

    // determine all call into lib from app sources
    var appClasses = project.projectClassFilesWithSources.collect({ case (c, s) if appSources.contains(toJarSource(s)) => c })

    var appMethods: Iterable[Method] = appClasses.map(c => c.methods).flatten
    var appMethodCalls: Iterable[Method] = appMethods.map(m => cg.calls(m).map(_._2).flatten).flatten
    val appToLibCalls: Iterable[Method] = appMethodCalls.filter(m => libSources.contains(toJarSource(project.source(m.classFile.thisType).get)))

    println("appMethod: " + appMethods.size + " - appMethodCalls: " + appMethodCalls.size + " - appToLibCalls: " + appToLibCalls.size)

    //appToLibCalls.foreach(m => println(m.toJava(project.classFile(m))))

    val result = new mutable.HashSet[Method]()
    result ++= appToLibCalls

    result ++= computeNecessaryMethods(appToLibCalls.map(_.classFile).toSet)
    result ++= computeDependencies(appMethods)
    result ++= computeDependencies(appToLibCalls)
    result ++= computeFieldAccess(result)


    def isLibraryMethod(m : Method) : Boolean = {
      if (m == null) return false;
      isLibraryClass(m.classFile)
    }

    def isLibraryClass(c: ClassFile) : Boolean = {
      if (c == null) return false;
      libSources.contains(toJarSource(project.source(ObjectType(c.fqn)).get))
    }


    def computeNecessaryMethods(classes : Set[ClassFile]) : Set[Method] = {
      classes.filter(isLibraryClass(_)).map(c => c.methods).flatten.filter(m => { m.isStaticInitializer }) // || (m.isConstructor && m.parametersCount == 1) })
    }


    def computeDependencies(methods : Iterable[Method]) : Set[Method] = {

      def isSupertypeMethod (referenceMethod : Method, candidateMethod : Method) : Boolean = {
        candidateMethod.hasSignature(referenceMethod.name, referenceMethod.descriptor)
      }

      var result : Set[Method] = Set()
      for (targetMethod <- methods) {
        var superTypes = project.classHierarchy.allSupertypes(ObjectType(targetMethod.classFile.fqn)).map(project.classFile(_).getOrElse(null)).filter(e => e != null)
        result ++= superTypes.map(c => c.methods).flatten.filter(m => isSupertypeMethod(targetMethod, m) && isLibraryMethod(m))
      }

      val usedLibraryAnnotations : Iterable[ClassFile] = methods.map(_.annotations).flatten.flatMap(a => project.classFile(a.annotationType.asObjectType)).filter(isLibraryClass(_))

      result ++= usedLibraryAnnotations.flatMap(c => c.methods)

      result
    }

    def computeFieldAccess(methods : Iterable[Method]) : Set[Method] = {

      val pf: PartialFunction[Instruction, Iterable[Method]] = { _ match {
        case gs : GETSTATIC => computeNecessaryMethods(Set(project.classFile(gs.declaringClass).getOrElse(null)))
      } }

      methods.filter(m => !m.body.isEmpty).map(m => m.body.get.instructions).flatten.collect(pf).flatten.toSet
    }


    val workQueue = new mutable.Queue[Method]()
    workQueue ++= result.toList.sortBy(m => m.toJava)

    while (workQueue.nonEmpty) {
      val current = workQueue.dequeue()

      var newCalls = cg.calls(current).map(_._2).flatten.filter(m => isLibraryMethod(m) && !result.contains(m))
      newCalls ++= computeNecessaryMethods(newCalls.map(_.classFile).toSet)
      newCalls ++= computeDependencies(newCalls)
      newCalls ++= computeFieldAccess(newCalls)

      result ++= newCalls
      workQueue ++= newCalls
    }


    println("Final result: " + result.size)

    result.toSet.groupBy(m => m.classFile)
  }

  /** Returns the CallGraph of the given project.
    *
    * @param project This project is the base of the call graph construction.
    */
  private def buildCallGraph(project: Project[URL]): CallGraph = {
    CallGraphFactory.create(project,
      () ⇒ CallGraphFactory.defaultEntryPointsForLibraries(project),
      new CHACallGraphAlgorithmConfiguration(project)).callGraph
  }
}
