import java.sql.DriverManager

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{DockerCommandExecutor, DockerContainer, DockerContainerState, DockerFactory, DockerKit, DockerReadyChecker}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

//TODO read from config
trait DBConfig {
  val internalPort = 44444
  val externalPort = 5432
  val user = "postgres"
  val password = "safepassword"
  val database = "postgres"//same as user
  val driver = "org.postgresql.Driver"
  val dockerImage = "postgres:10.4"
}

trait DockerPostgresSetup extends DockerKit {
  this: DBConfig =>

  private lazy val client: DockerClient = DefaultDockerClient.fromEnv().build()
  override implicit lazy val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  //val postgresContainer = configureDockerContainer("docker.postgres")
  lazy val postgresContainer = DockerContainer(dockerImage)
    .withPorts((externalPort, Some(internalPort)))
    .withEnv(s"POSTGRES_USER=$user", s"POSTGRES_PASSWORD=$password", s"POSTGRES_DB=$database")
    .withReadyChecker(new PostgresReadyChecker(user, password, Some(externalPort))
      .looped(15, 1.second))

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


