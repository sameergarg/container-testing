import com.whisk.docker.scalatest.DockerTestKit
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile.api._

class DB2IntegrationSpec extends WordSpec with Matchers with DockerTestKit with DockerPostgresSetup
  with ScalaFutures {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout =  Span(20, Seconds), interval = Span(5, Millis))

  "DB connection" must {
    "be obtained from container" in new {
      val eventualCoffees: Task[Seq[Coffee]] = for {
        _       <- runFlywayMigration
        coffees <- Task.deferFuture(db.run(Coffees.coffees.result.map {
          _.map {
            case (name, price) => Coffee(name, price)
          }
        }))
      } yield coffees

      whenReady(eventualCoffees.runAsync) { result =>
        result.length should be > 0
      }
    }
  }
}
