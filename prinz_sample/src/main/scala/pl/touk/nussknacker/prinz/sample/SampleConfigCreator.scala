package pl.touk.nussknacker.prinz.sample

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.transformer.PeriodicSourceFactory
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.prinz.enrichers.PrinzEnricher
import pl.touk.nussknacker.prinz.mlflow.MLFConfig
import pl.touk.nussknacker.prinz.mlflow.repository.MLFRepository

class SampleConfigCreator extends EmptyProcessConfigCreator {

  private val logger = Logger[this.type]

  protected def allCategories[T](obj: T): WithCategories[T] = WithCategories(obj, "FraudDetection", "Recommendations")

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "periodic" -> allCategories(PeriodicSourceFactory)
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "empty" -> allCategories(SinkFactory.noParam(EmptySink))
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = {
    implicit val mlfConfig: MLFConfig = MLFConfig()(processObjectDependencies.config)
    val repo = new MLFRepository()
    val response = repo.listModels

    val result = response.right.map(
      modelsList => modelsList.foldLeft(Map.empty[String, WithCategories[Service]])(
        (services, model) => services + (model.getName.name -> allCategories(new PrinzEnricher(model)))
    ))
    result match {
      case Left(exception) => throw exception
      case Right(services) => services
    }
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))
}