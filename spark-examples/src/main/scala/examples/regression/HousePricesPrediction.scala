package examples.regression

import scala.util.control.Exception.catching
import org.apache.spark.SparkContext
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import examples.common.Application.configLocalMode
import examples.common.DataLoader.localFile
import examples.PrintUtils.printMetrics
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import examples.classification.BikeBuyerModel
import org.apache.spark.mllib.tree.DecisionTree
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.rdd.RDD

object HousePricesPrediction {
  
  def createLinearRegressionModel(rdd: RDD[LabeledPoint]) = {
    LinearRegressionWithSGD.train(rdd, numIterations = 100, stepSize = 0.01)    
  }
  
  def createDecisionTreeRegressionModel(rdd: RDD[LabeledPoint]) = {
    val impurity = "variance" 
    val maxDepth = 10
    val maxBins = 20
    DecisionTree.trainRegressor(rdd, Map[Int, Int](), impurity, maxDepth, maxBins)
  }
  
  def main(args: Array[String]): Unit = {

    val sc = new SparkContext(configLocalMode)
    val hdFile = localFile("house-data.csv")(sc)

    val houses = hdFile.filter { s =>
      catching(classOf[NumberFormatException]).opt(s.split(",")(0).toLong).isDefined
    }.map { s => HouseModel(s.split(",")).toLabeledPoint() }
  
    val Array(train, test) = houses.randomSplit(Array(.9, .1), 10204L)
        
    /*
     * In regression it is recommended that the input variables have a mean of 0. 
     * This is easy to achieve by using the StandardScaler from Spark ML 
     */
    val scaler = new StandardScaler(withMean = true, withStd = true).fit(train.map(dp => dp.features))
    val scaledTrain = train.map(dp => new LabeledPoint(dp.label, scaler.transform(dp.features))).cache()
    val scaledTest = test.map(dp => new LabeledPoint(dp.label, scaler.transform(dp.features))).cache()

    val stats2 = Statistics.colStats(scaledTrain.map { x => x.features })
    println(s"Max : ${stats2.max}, Min : ${stats2.min}, and Mean : ${stats2.mean} and Variance : ${stats2.variance}")

    val model = createDecisionTreeRegressionModel(scaledTrain)
    
    scaledTest.take(5).foreach {
      x => println(s"Predicted: ${model.predict(x.features)}, Label: ${x.label}")
    }

    /*
     * Calculate predictions and store them with actual prices
     */
    val predictionsAndValues = scaledTest.map {
      point => (model.predict(point.features), point.label)
    }
    
    println("Mean house price: " + scaledTest.map { x => x.label }.mean())
    println("Max prediction error: " + predictionsAndValues.map { case (p, v) => math.abs(v - p) }.max)
    val rmse = math.sqrt(predictionsAndValues.map { case (p, v) => math.pow((v - p), 2) }.mean())
    println("Root Mean Squared Error: " + rmse)

    sc.stop
  }  
}