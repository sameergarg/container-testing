import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

class DBIntegrationSpec extends WordSpec with Matchers with DockerTestKit with DockerPostgresSetup
  with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(20, Seconds), interval = Span(5, Millis))

  "DB connection" must {
    "be obtained from container" in new InitDockerDB {
      private val eventualCoffees: Future[Seq[Coffee]] = for {
        _ <- Future(Thread.sleep(5000))
        _ <- runFlywayMigration
        result <- db.run(Coffees.coffees.result.map {
          _.map {
            case (name, price) => Coffee(name, price)
          }
        })
      } yield {
        result
      }

      Await.ready(isContainerReady(postgresContainer), 10.seconds)

      whenReady(eventualCoffees) { result =>
        result.length should be > 0
      }
    }
  }
}
