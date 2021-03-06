/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2015
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package analyses.toString

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.net.URL
import org.opalj.br.analyses.Analysis
import org.opalj.br.analyses.AnalysisExecutor
import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.OneStepAnalysis
import org.opalj.br.MethodWithBody
import org.opalj.br.instructions.INVOKEVIRTUAL
import org.opalj.br.NoArgumentMethodDescriptor
import org.opalj.br.ObjectType

object ToStringCallsOnObject extends AnalysisExecutor {

  val analysis = new OneStepAnalysis[URL, BasicReport] {

    override def description: String = "Return the toString() calls, that where the reciever type is java/lang/Object"

    def doAnalyze(project: Project[URL], parameters: Seq[String] = List.empty, isInterrupted: () => Boolean) = {
      val callSites =
        (for {
          classFile <- project.allClassFiles;
          method @ MethodWithBody(body) <- classFile.methods
        } yield {
          val pcs = for {
            pc <- body.collectWithIndex {
              case (pc,
                INVOKEVIRTUAL(ObjectType.Object,
                  "toString",
                  NoArgumentMethodDescriptor(ObjectType.String))
                ) => pc
            }
          } yield pc
          (classFile, method, pcs)
        }).filter(_._3.size > 0)

      BasicReport("Found " + callSites.size + " call sites of Object.toString()" + 
          callSites.map(t => t._1.fqn + " " + t._2.name + " PCs( " + t._3 + " )").mkString("\n\n", "\n\t", "\n"))
    }
  }
}