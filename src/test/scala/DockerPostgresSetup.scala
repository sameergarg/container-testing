import java.sql.DriverManager

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{DockerCommandExecutor, DockerContainer, DockerContainerState, DockerFactory, DockerKit, DockerReadyChecker}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

trait DBConfig {
  val hostPort = 44444
  val containerPort = 5432
  val user = "postgres"
  val password = "safepassword"
  val database = "postgres" //same as user
  val driver = "org.postgresql.Driver"
  val dockerImage = "postgres:10.4"
}

trait DockerPostgresSetup extends DockerKit with DBConfig {

  private lazy val client: DockerClient = DefaultDockerClient.fromEnv().build()
  override implicit lazy val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  lazy val postgresContainer = DockerContainer(dockerImage)
    .withPorts((containerPort, Some(hostPort)))
    .withEnv(s"POSTGRES_USER=$user", s"POSTGRES_PASSWORD=$password", s"POSTGRES_DB=$database")
    .withReadyChecker(new PostgresReadyChecker(user, password, Some(containerPort)).looped(10, 1.second))

  // adds our container to the DockerKit's list
  abstract override def dockerContainers: List[DockerContainer] =
    postgresContainer :: super.dockerContainers
}

class PostgresReadyChecker(user: String,
                           password: String,
                           port: Option[Int] = None) extends DockerReadyChecker {
  override def apply(container: DockerContainerState
                    )(implicit docker: DockerCommandExecutor, ec: ExecutionContext) = {

    container.getPorts().map { ports =>
      val url = s"jdbc:postgresql://${docker.host}:${port.getOrElse(ports.values.head)}/"
      Try {
        Class.forName("org.postgresql.Driver")
        Option(DriverManager.getConnection(url, user, password))
          .map(_.close)
          .isDefined
      }.getOrElse(false)
    }
  }
}

trait InitDockerDB extends DBConfig {

  private lazy val dbUrl = s"jdbc:postgresql://localhost:$hostPort/$database?ssl=false"

  private lazy val config = {
    val hConfig = new HikariConfig
    hConfig.setJdbcUrl(dbUrl)
    hConfig.setUsername(user)
    hConfig.setPassword(password)
    hConfig
  }

  private lazy val dataSource = new HikariDataSource(config)

  def runFlywayMigration(): Future[Unit] = Future.successful {
    lazy val flyway = Flyway
      .configure()
      .dataSource(dbUrl, user, password)
      //.dataSource(dataSource)
      .load()

    flyway.migrate()
    flyway.validate()
  }

  import slick.jdbc.PostgresProfile.api._

  lazy val db = Database.forDataSource(dataSource, None)
}



