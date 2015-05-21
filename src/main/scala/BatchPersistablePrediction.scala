package org.template.recommendation

import grizzled.slf4j.Logger
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.Engine
import io.prediction.controller.EngineFactory
import io.prediction.controller.EngineParams
import io.prediction.controller.EngineParamsGenerator
import io.prediction.controller.Evaluation
import io.prediction.controller.Params
import io.prediction.core.BaseEvaluator
import io.prediction.core.BaseEvaluatorResult
import io.prediction.workflow.WorkflowParams
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.native.Serialization

case class BatchDataSourceParams(
  appName: String,
  evalParams: Option[DataSourceEvalParams]) extends Params

class BatchDataSource(dsp: BatchDataSourceParams)
  extends DataSource(DataSourceParams(appName = dsp.appName, evalParams = None)) {
  override
  def readEval(sc: SparkContext)
  : Seq[(TrainingData, EmptyEvaluationInfo, RDD[(Query, ActualResult)])] = {
    // This function only return one evaluation data set

    // Create your own queries here. Below are provided as examples.
    val batchQueries = Seq(
      Query(user = "1", num = 10),
      Query(user = "3", num = 15),
      Query(user = "5", num = 20))

    // This is a dummy value to make the evaluation pipeline happy, we are not
    // using it.
    val dummyActual = ActualResult(
      ratings = Array(Rating(user = "1", item = "1", rating = 0.0)))

    val evalDataSet = (
      readTraining(sc),
      new EmptyEvaluationInfo(),
      sc.parallelize(batchQueries).map { q => (q, dummyActual) })

    Seq(evalDataSet)
  }
}

// A helper object for the json4s serialization
case class Row(query: Query, predictedResult: PredictedResult)
  extends Serializable

class BatchPersistableEvaluatorResult extends BaseEvaluatorResult {}

class BatchPersistableEvaluator extends BaseEvaluator[
  EmptyEvaluationInfo,
  Query,
  PredictedResult,
  ActualResult,
  BatchPersistableEvaluatorResult] {
  @transient lazy val logger = Logger[this.type]

  def evaluateBase(
    sc: SparkContext,
    evaluation: Evaluation,
    engineEvalDataSet: Seq[(
      EngineParams,
      Seq[(EmptyEvaluationInfo, RDD[(Query, PredictedResult, ActualResult)])])],
    params: WorkflowParams): BatchPersistableEvaluatorResult = {

    /** Extract the first data, as we are only interested in the first
      * evaluation. It is possible to relax this restriction, and have the
      * output logic below to write to different directory for different engine
      * params.
      */

    require(
      engineEvalDataSet.size == 1, "There should be only one engine params")

    val evalDataSet = engineEvalDataSet.head._2

    require(evalDataSet.size == 1, "There should be only one RDD[(Q, P, A)]")

    val qpaRDD = evalDataSet.head._2

    // qpaRDD contains 3 queries we specified in readEval, the corresponding
    // predictedResults, and the dummy actual result.

    /** The output directory. Better to use absolute path if you run on cluster.
      * If your database has a Hadoop interface, you can also convert the
      * following to write to your database in parallel as well.
      */
    val outputDir = "batch_result"

    logger.info("Writing result to disk") 
    qpaRDD
      .map { case (q, p, a) => Row(q, p) }
      .map { row =>
        // Convert into a json
        implicit val formats: Formats = DefaultFormats
        Serialization.write(row)
      }
      .saveAsTextFile(outputDir)

    logger.info(s"Result can be found in $outputDir")

    new BatchPersistableEvaluatorResult()
  }
}

// Define a new engine that includes `BatchDataSource`
object BatchClassificationEngine extends EngineFactory {
  def apply() = {
    new Engine(
      Map(
        "" -> classOf[DataSource],
        "batch" -> classOf[BatchDataSource]),
      Map("" -> classOf[Preparator]),
      Map("als" -> classOf[ALSAlgorithm]),
      Map("" -> classOf[Serving]))
  }
}

object BatchEvaluation extends Evaluation {
  // Define Engine and Evaluator used in Evaluation

  /** This is not covered in our tutorial. Instead of specifying a Metric, we
    * can actually specify an Evaluator.
    */
  engineEvaluator =
    (BatchClassificationEngine(), new BatchPersistableEvaluator())
}

object BatchEngineParamsList extends EngineParamsGenerator {
  // We only interest in a single engine params.
  engineParamsList = Seq(
    EngineParams(
      dataSourceName = "batch",
      dataSourceParams =
        BatchDataSourceParams(appName = "rec", evalParams = None),
      algorithmParamsList = Seq(("als", ALSAlgorithmParams(
        rank = 10,
        numIterations = 20,
        lambda = 0.01,
        seed = Some(3L))))))
}
