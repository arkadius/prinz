package pl.touk.nussknacker.prinz

import java.io.File
import java.time.Duration
import scala.sys.process.Process
import com.dimafeng.testcontainers.DockerComposeContainer.{ComposeFile, Def}
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.{ContainerDef, WaitingForService}
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import pl.touk.nussknacker.prinz.UnitIntegrationTest.{BRIDGE_NET_NAME, DOCKER_COMPOSE_FILE, MLFLOW_SERVER_SERVICE_NAME,
  MODEL_1_SERVED_READY_REGEX, MODEL_2_SERVED_READY_REGEX, MODEL_3_SERVED_READY_REGEX, TIMEOUT_MINUTES}

abstract class UnitIntegrationTest extends UnitTest with TestContainerForAll {

  override def startContainers(): containerDef.Container = {
    Process(s"docker network rm $BRIDGE_NET_NAME").!
    val res = Process(s"docker network create $BRIDGE_NET_NAME").!
    if(res != 0) {
      throw new IllegalStateException("Non 0 code returned from docker network create command.")
    }

    super.startContainers()
  }

  override val containerDef: ContainerDef = Def(
    composeFiles = ComposeFile(Left(DOCKER_COMPOSE_FILE)),
    waitingFor = Some(
      WaitingForService(MLFLOW_SERVER_SERVICE_NAME, new LogMessageWaitStrategy()
        .withRegEx(MODEL_1_SERVED_READY_REGEX)
        .withRegEx(MODEL_2_SERVED_READY_REGEX)
        .withRegEx(MODEL_3_SERVED_READY_REGEX)
        .withStartupTimeout(Duration.ofMinutes(TIMEOUT_MINUTES)))
    )
  )
}

object UnitIntegrationTest {

  private val DOCKER_COMPOSE_FILE = new File("../dev-environment/docker-compose.yaml")

  private val TIMEOUT_MINUTES = 5

  private val MODEL_1_SERVED_READY_REGEX = s".*Listening at.*1111.*"

  private val MODEL_2_SERVED_READY_REGEX = s".*Listening at.*2222.*"

  private val MODEL_3_SERVED_READY_REGEX = s".*Listening at.*3333.*"

  private val MLFLOW_SERVER_SERVICE_NAME = "mlflow-server"

  private val BRIDGE_NET_NAME = "dev-bridge-net"
}
