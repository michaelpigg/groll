/*
 * Copyright 2011 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package name.heikoseeberger.groll

import sbt._
import sbt.CommandSupport.logger
import sbt.complete.Parsers._
import scala.io.Source

object Groll extends Plugin {

  override def settings = Seq(Keys.commands += nextCommand)

  private val (next, prev) = ("next", "prev")

  private val nextCommand = Command("groll")(_ => (Space ~> next | Space ~> prev)) { (state, args) =>
    try {
      val history = execute("git log --pretty=format:%h master")
      logger(state).debug("History: %s" format (history mkString ", "))
      val current = execute("git log -n 1 --pretty=format:%h").head
      logger(state).debug("Current: %s" format current)
      assert(history contains current, "Commit history must contain current commit!")

      args match {
        case `next` => (history takeWhile { _ != current }).lastOption match {
          case None =>
            logger(state).warn("Already arrived at the last commit!")
            state
          case Some(n) =>
            (Process("git reset --hard") #&& Process("git clean -df") #&& Process("git checkout %s" format n)).!!
            logger(state).info("Moved forward to next commit: %s" format n)
            state.reload
        }
        case `prev` => (history dropWhile { _ != current }).tail.headOption match {
          case None =>
            logger(state).warn("Already arrived at the first commit!")
            state
          case Some(n) =>
            (Process("git reset --hard") #&& Process("git clean -df") #&& Process("git checkout %s" format n)).!!
            logger(state).info("Moved back to previous commit: %s" format n)
            state.reload
        }
      }
    } catch {
      case e: Exception =>
        logger(state).error(e.getMessage)
        state.fail
    }
  }
}