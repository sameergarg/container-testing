import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class DBIntegrationSpec extends WordSpec with Matchers with DockerTestKit with DockerPostgresSetup
  with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(20, Seconds), interval = Span(5, Millis))

  "DB connection" must {
    "be obtained from container" in new InitDockerDB {
      private val eventualCoffees: Future[Seq[Coffee]] = for {
        _       <- Future(Thread.sleep(2000))//need to wait for postgres to start in container
        _       <- runFlywayMigration
        coffees <- db.run(Coffees.coffees.result.map {
                      _.map {
                        case (name, price) => Coffee(name, price)
                      }
                    })
      } yield coffees

      whenReady(eventualCoffees) { result =>
        result.length should be > 0
      }
    }
  }
}
