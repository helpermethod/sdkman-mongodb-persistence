package support

import java.util.concurrent.TimeUnit

import io.sdkman.db.MongoConnectivity
import io.sdkman.repos.{Application, Candidate, Version}
import org.mongodb.scala._
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{and, equal}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait Mongo {

  self: MongoConnectivity =>

  import Helpers._

  def insertApplication(app: Application) = appCollection.insertOne(app).results()

  def insertVersions(vs: Seq[Version]) = versionsCollection.insertMany(vs).results()

  def insertVersion(v: Version) = versionsCollection.insertOne(v).results()

  def insertCandidates(cs: Seq[Candidate]) = candidatesCollection.insertMany(cs).results()

  def dropAllCollections() = {
    appCollection.drop().results()
    versionsCollection.drop().results()
    candidatesCollection.drop().results()
  }

  def isDefault(candidate: String, version: String): Boolean = Await.result(
    candidatesCollection
      .find(and(equal("candidate", candidate), equal("default", version)))
      .headOption()
      .map(_.nonEmpty), 5.seconds)

  def hasDefault(candidate: String): Boolean = Await.result(
    candidatesCollection
      .find(equal("candidate", candidate))
      .first
      .toFuture, 5.seconds).default.nonEmpty

  def versionPublished(candidate: String, version: String, url: String, platform: String): Boolean = Await.result(
    versionsCollection
      .find(and(equal("candidate", candidate), equal("version", version), equal("platform", platform)))
      .first
      .headOption()
      .map(_.nonEmpty), 5.seconds)
}

object Helpers {

  implicit class DocumentObservable[C](val observable: Observable[Document]) extends ImplicitObservable[Document] {
    override val converter: Document => String = doc => doc.toJson
  }

  implicit class GenericObservable[C](val observable: Observable[C]) extends ImplicitObservable[C] {
    override val converter: C => String = doc => doc.toString
  }

  trait ImplicitObservable[C] {
    val observable: Observable[C]
    val converter: C => String

    def results(): Seq[C] = Await.result(observable.toFuture(), Duration(10, TimeUnit.SECONDS))

    def headResult() = Await.result(observable.head(), Duration(10, TimeUnit.SECONDS))

    def printResults(initial: String = ""): Unit = {
      if (initial.length > 0) print(initial)
      results().foreach(res => println(converter(res)))
    }

    def printHeadResult(initial: String = ""): Unit = println(s"$initial${converter(headResult())}")
  }

}