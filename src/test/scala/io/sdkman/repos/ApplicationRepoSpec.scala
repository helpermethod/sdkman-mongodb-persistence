package io.sdkman.repos

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.sdkman.db.{MongoConfiguration, MongoConnectivity}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, OptionValues, WordSpec}
import support.Mongo

class ApplicationRepoSpec extends WordSpec with Matchers with BeforeAndAfter with ScalaFutures with OptionValues with ForAllTestContainer {
  override val container = MongoDBContainer("mongo:3.2")

  "application repository" should {

    "attempt to find a single Application" when {

      "that row is available" in new TestRepo {

        val alive = "OK"
        val stableCliVersion = "8.8.8+888"
        val betaCliVersion = "9.9.9+999"

        insertApplication(Application(alive, stableCliVersion, betaCliVersion))

        whenReady(findApplication()) { maybeApp =>
          maybeApp.value.alive shouldBe alive
          maybeApp.value.stableCliVersion shouldBe stableCliVersion
          maybeApp.value.betaCliVersion shouldBe betaCliVersion
        }
      }

      "that row is missing" in new TestRepo {
        whenReady(findApplication()) { maybeApp =>
          maybeApp should not be defined
        }
      }
    }
  }

  before {
    new TestRepo {
      dropAllCollections()
    }
  }

  private trait TestRepo extends ApplicationRepo with MongoConnectivity with MongoConfiguration with Mongo {
    override val config = ConfigFactory.load().withValue("mongo.url.port", ConfigValueFactory.fromAnyRef(container.mappedPort(27017)))
  }
}
