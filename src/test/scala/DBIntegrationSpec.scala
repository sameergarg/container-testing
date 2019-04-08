import com.whisk.docker.scalatest.DockerTestKit
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

class DBIntegrationSpec extends WordSpec with Matchers with DBConfig with DockerTestKit with DockerPostgresSetup
    with ScalaFutures {

  trait InitDockerDB {
    this: DBConfig =>

    lazy val dbUrl = s"jdbc:postgresql://localhost:$internalPort/$database?autoReconnect=true&useSSL=false"

    lazy val config = new HikariConfig
    config.setJdbcUrl(dbUrl)
    config.setUsername(user)
    config.setPassword(password)

    lazy val dataSource = new HikariDataSource(config)

    lazy val l = new Location("classpath:/db/migration")
    lazy val loc = getClass.getClassLoader.getResourceAsStream(l.getPath)

    // our Flyway migration
    lazy val flyway = Flyway.configure()
      .dataSource(dataSource)
      .locations("db/migration", "classpath:/db/migration")
      .load()

    import slick.jdbc.PostgresProfile.api._

    lazy val db = Database.forDataSource(dataSource, None)

  }

  "DB connection" must {
    "be obtained from container" in new InitDockerDB with DBConfig {
      flyway.migrate()

      val eventualTypes: Future[Seq[Coffee]] = db.run(Coffees.coffees.result.map {
        _.map {
          case (name, price) => Coffee(name, price)
        }
      })
      whenReady(eventualTypes) { result =>
        result.length should be > 0
      }
    }
  }

  override def afterAll(): Unit = {

  }
}
