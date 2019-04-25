import java.sql.DriverManager

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{DockerCommandExecutor, DockerContainer, DockerContainerState, DockerFactory, DockerKit, DockerReadyChecker}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import monix.eval.Task
import org.flywaydb.core.Flyway

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Random, Try}

trait DockerPostgresSetup extends DockerKit {
  lazy val hostPort = 2000 + Random.nextInt(8000)
  val containerPort = 5432
  val user = "postgres"
  val password = "safepassword"
  val database = "postgres" //same as user
  val driver = "org.postgresql.Driver"
  val dockerImage = "postgres:10.4"
  lazy val dbUrl = s"jdbc:postgresql://localhost:$hostPort/$database?ssl=false"


  private lazy val client: DockerClient = DefaultDockerClient.fromEnv().build()
  override implicit lazy val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  lazy val postgresContainer: DockerContainer = DockerContainer(dockerImage)
    .withPorts((containerPort, Some(hostPort)))
    .withEnv(s"POSTGRES_USER=$user", s"POSTGRES_PASSWORD=$password", s"POSTGRES_DB=$database")
    .withReadyChecker(new PostgresReadyChecker(dbUrl, user, password, driver).looped(10, 1.second))

  // adds our container to the DockerKit's list
  abstract override def dockerContainers: List[DockerContainer] =
    postgresContainer :: super.dockerContainers

  private lazy val config = {
    val hConfig = new HikariConfig
    hConfig.setJdbcUrl(dbUrl)
    hConfig.setUsername(user)
    hConfig.setPassword(password)
    hConfig
  }

  private lazy val dataSource = new HikariDataSource(config)

  lazy val runFlywayMigration: Task[Unit] = Task.evalOnce {
    val flyway = Flyway
      .configure()
      .dataSource(dbUrl, user, password)
      .load()

    flyway.migrate()
    flyway.validate()
  }

  import slick.jdbc.PostgresProfile.api._

  lazy val db = Database.forDataSource(dataSource, None)

}

class PostgresReadyChecker(dbUrl: String, user: String, password: String, driver: String) extends DockerReadyChecker {
  override def apply(container: DockerContainerState
                    )(implicit docker: DockerCommandExecutor, ec: ExecutionContext) : Future[Boolean] = {

    container.getPorts().map { _ =>
      Try {
        Class.forName(driver)
        Option(DriverManager.getConnection(dbUrl, user, password))
          .map(_.close)
          .isDefined
      }.getOrElse(false)
    }
  }
}


