package pl.touk.nussknacker.prinz.sample

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, Sink, SinkFactory, Source, SourceFactory, TestDataParserProvider, WithCategories}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.nussknacker.engine.standalone.api.{StandaloneGetSource, StandalonePostSource, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.prinz.enrichers.PrinzEnricher
import pl.touk.nussknacker.prinz.mlflow.MLFConfig
import pl.touk.nussknacker.prinz.mlflow.repository.MLFModelRepository

class SampleConfigCreator extends EmptyProcessConfigCreator {

  protected def allCategories[T](obj: T): WithCategories[T] = WithCategories(obj, "FraudDetection", "Recommendations")

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request" -> allCategories(Request1SourceFactory),
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "fraudDetected" -> allCategories(ResponseSink),
    "fraudNotDetected" -> allCategories(ResponseSink)
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = {
    implicit val mlfConfig: MLFConfig = MLFConfig()(processObjectDependencies.config)
    val repo = new MLFModelRepository()
      val response = repo.listModels

    val result = response.right.map(
      modelsList => modelsList.foldLeft(Map.empty[String, WithCategories[Service]])(
        (services, model) => services + (model.getName.name -> allCategories(PrinzEnricher(model)))
    ))
    result match {
      case Left(exception) => throw exception
      case Right(services) => services
    }
  }

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig =
    ExpressionConfig(globalProcessVariables = Map(
      "businessConfig" -> allCategories(BusinessConfig(123.0))
    ), List.empty)


  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler(_))
}

case class BusinessConfig(threshold: Double) {
  def scenarioByAmount(amount: Double): String = "mlModel"
}

@JsonCodec case class Request1(customerId: String, amount: Double)

case object Request1SourceFactory extends StandaloneSourceFactory[Request1] {

  @MethodToInvoke
  def create(): Source[Request1] = {
    new StandalonePostSource[Request1] with StandaloneGetSource[Request1] with TestDataParserProvider[Request1] {

      override def parse(data: Array[Byte]): Request1 = CirceUtil.decodeJsonUnsafe[Request1](data)

      override def parse(parameters: Map[String, List[String]]): Request1 = {
        def takeFirst(id: String) = parameters.getOrElse(id, List()).headOption.getOrElse("")
        Request1(takeFirst("customerId"), takeFirst("amount").toDouble)
      }

      override def testDataParser: TestDataParser[Request1] = new NewLineSplittedTestDataParser[Request1] {
        override def parseElement(testElement: String): Request1 = {
          CirceUtil.decodeJsonUnsafe[Request1](testElement, "invalid request")
        }
      }
    }
  }

  override def clazz: Class[_] = classOf[Request1]

}

case object ResponseSink extends SinkFactory {
  @MethodToInvoke
  def invoke(): Sink = new Sink {
    override def testDataOutput: Option[(Any) => String] = Some(_.toString)
  }
}
