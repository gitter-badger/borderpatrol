/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Lookout, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.lookout.borderpatrol.example

import argonaut.Argonaut._
import argonaut._
import com.lookout.borderpatrol.sessionx._
import com.twitter.bijection.twitter_util.UtilBijections
import com.twitter.finagle.httpx.Request
import com.twitter.util.{Future, Return, Throw}
import io.finch.request._

object reader {

  import com.lookout.borderpatrol.sessionx.Session._
  import com.lookout.borderpatrol.sessionx.SessionIdInjections._
  import model._

  implicit val secretStore = SecretStores.InMemorySecretStore(Secrets(Secret(), Secret()))
  implicit val sessionStore = SessionStores.InMemoryStore

  implicit val sessionIdDecoder: DecodeRequest[SessionId] =
    DecodeRequest[SessionId](s => UtilBijections.twitter2ScalaTry.inverse(SessionId.from[String](s)))

  implicit val userCodec: CodecJson[User] =
    casecodec2(User.apply, User.unapply)("e", "p")

  implicit val tokenCodec: CodecJson[Token] =
    casecodec2(Token.apply, Token.unapply)("s", "u")

  implicit val tokenDecoder: DecodeRequest[Token] =
    DecodeRequest[Token](s => Parse.decodeOption[Token](s) match {
      case Some(v) => Return(v)
      case None => Throw(new Exception("unparsable"))
    })

  val userReader: RequestReader[User] = (
      param("e") :: param("p")
      ).as[User]

  val sessionIdReader: RequestReader[SessionId] =
    cookie("border_session").map(_.value).as[SessionId]

  val sessionReader: RequestReader[Session[Request]] =
    sessionIdReader.embedFlatMap(sessionStore.get[Request]).embedFlatMap {
      case Some(s) => Future.value(s)
      case None => Future.exception(new RequestError("invalid session"))
    }

  val authHeaderReader: RequestReader[Token] =
    header("X-AUTH-TOKEN").should("start with secret")(_.startsWith("supersecret")).as[Token]
}
