package io.sdkman.repos

import com.dimafeng.testcontainers.{ForAllTestContainer, MongoDBContainer}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.sdkman.db.{MongoConfiguration, MongoConnectivity}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, OptionValues, WordSpec}
import support.Mongo

class VersionsRepoSpec extends WordSpec with Matchers with BeforeAndAfter with ScalaFutures with OptionValues with ForAllTestContainer {
  override val container = MongoDBContainer("mongo:3.2")

  "versions repository" should {

    "persist a version" in new TestRepo {

      val candidate = "java"
      val version = "8u111"
      val platform = "LINUX_64"
      val url = "http://dl/8u111-b14/jdk-8u111-linux-x64.tar.gz"

      whenReady(saveVersion(Version(candidate, version, platform, url))) { completed =>
        completed.toString shouldBe "The operation completed successfully"
        versionPublished(candidate, version, url, platform) shouldBe true
      }
    }

    "attempt to find one Version by candidate, version and platform" when {

      "that version is available" in new TestRepo {
        val candidate = "java"
        val version = "8u111"
        val platform = "LINUX_64"
        val url = "http://dl/8u111-b14/jdk-8u111-linux-x64.tar.gz"

        insertVersion(Version(candidate, version, platform, url))

        whenReady(findVersion(candidate, version, platform)) { maybeVersion =>
          maybeVersion.value.candidate shouldBe candidate
          maybeVersion.value.version shouldBe version
          maybeVersion.value.platform shouldBe platform
          maybeVersion.value.url shouldBe url
        }
      }

      "find no Version by candidate, version and platform" in new TestRepo {
        whenReady(findVersion("java", "7u65", "LINUX_64")) { maybeVersion =>
          maybeVersion should not be defined
        }
      }
    }

    "attempt to find all versions by candidate and platform ordered by version" in new TestRepo {
      val java8u111 = Version("java", "8u111", "LINUX_64", "http://dl/8u111-b14/jdk-8u111-linux-x64.tar.gz")
      val java8u121 = Version("java", "8u121", "LINUX_64", "http://dl/8u121-b14/jdk-8u121-linux-x64.tar.gz")
      val java8u131 = Version("java", "8u131", "LINUX_64", "http://dl/8u131-b14/jdk-8u131-linux-x64.tar.gz")

      val javaVersions = Seq(java8u111, java8u121, java8u131)

      javaVersions.foreach(insertVersion)

      whenReady(findAllVersionsByCandidatePlatform("java", "LINUX_64")) { versions =>
        versions.size shouldBe 3
        versions(0) shouldBe java8u111
        versions(1) shouldBe java8u121
        versions(2) shouldBe java8u131
      }
    }

    "attempt to find all versions by candidate and mixed platform ordered by version" in new TestRepo {
      val mn2linux = Version("micronaut", "2.0.0", "LINUX_64", "http://dl/mn-2.0.0-linux.tar.gz")
      val mn2darwin = Version("micronaut", "2.0.0", "MAC_OSX", "http://dl/mn-2.0.0-darwin.tar.gz")
      val mn2windows = Version("micronaut", "2.0.0", "WINDOWS_64", "http://dl/mn-2.0.0-windows.zip")
      val mn1universal = Version("micronaut", "1.3.5", "UNIVERSAL", "http://dl/mn-1.3.5.zip")

      val micronautVersions = Seq(mn1universal, mn2darwin, mn2linux, mn2windows)

      micronautVersions.foreach(insertVersion)

      whenReady(findAllVersionsByCandidatePlatform("micronaut", "LINUX_64")) { versions =>
        versions.size shouldBe 2
        versions(0) shouldBe mn1universal
        versions(1) shouldBe mn2linux
      }
    }

    "read versions with optional vendor" when {

      val candidate = "java"
      val version = "8.0.131"
      val platform = "LINUX_64"

      "a vendor is present" in new TestRepo with OptionValues {
        insertVersion(Version(candidate, version, platform, "http://dl/8u131-b14/jdk-8u131-linux-x64.tar.gz", Some("amazon")))
        whenReady(findVersion(candidate, version, platform)) { maybeVersion =>
          maybeVersion.value.vendor.value shouldBe "amazon"
        }
      }

      "a vendor is not present" in new TestRepo {
        insertVersion(Version(candidate, version, platform, "http://dl/8u131-b14/jdk-8u131-linux-x64.tar.gz", None))
        whenReady(findVersion(candidate, version, platform)) { maybeVersion =>
          maybeVersion.value.vendor should not be 'defined
        }
      }
    }

    "attempt to find all Versions by candidate and version" when {

      "more than one version platform is available" in new TestRepo {

        val candidate = "java"
        val version = "8u111"
        val url = "http://dl/8u111-b14/jdk-8u111-linux-x64.tar.gz"

        insertVersion(Version(candidate, version, "LINUX_64", url))
        insertVersion(Version(candidate, version, "MAC_OSX", url))

        whenReady(findAllVersionsByCandidateVersion(candidate, version)) { versions =>
          versions shouldBe 'nonEmpty
          versions.size shouldBe 2
        }
      }
    }
  }

  before {
    new TestRepo {
      dropAllCollections()
    }
  }

  private trait TestRepo extends VersionsRepo with MongoConnectivity with MongoConfiguration with Mongo {
    override val config: Config = ConfigFactory.load().withValue("mongo.url.port", ConfigValueFactory.fromAnyRef(container.mappedPort(27017)))
  }

}
