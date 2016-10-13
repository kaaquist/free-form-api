package com.example

import akka.actor.Actor
import spray.http.MediaTypes._
import spray.routing._

class ExampleServiceActor extends Actor with ExampleService {
  def actorRefFactory = context

  def receive = runRoute(route)
}

trait ExampleService extends HttpService {
  val route =
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          complete {
            <html>
              <body>
                <h1>Hello World!</h1>
              </body>
            </html>
          }
        }
      }
    }
}