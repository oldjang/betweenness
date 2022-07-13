package edu.cuhk.rain.partitioner

import edu.cuhk.rain.graph.Graph
import edu.cuhk.rain.util.ParamsPaser.Params
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.util.LongAccumulator

case object Partitioner {
  var context: SparkContext = _
  var config: Params = _
  def setup(context: SparkContext, param: Params): this.type = {
    this.context = context
    this.config = param
    this
  }

  def partition(): Unit = {
    val adj: RDD[(Long, Array[(Long, Double)])] = Graph.setup(context, config).loadTriplet().toAdj
    val tuples: Array[(Long, Array[Long])] = adj.mapValues(_.map(_._1)).collect()
    val partitioner = new LDGPartitioner(config.partitions, 10312)
    tuples.foreach{case (u, vs) => partitioner.addNode(u, vs)}

    val node2partition: Map[Long, Int] = partitioner.node2partition
    println(node2partition.size)

//    var sum = 0
//    tuples.foreach{case (u, vs) => vs.foreach{v =>
//      if (node2partition(u) != node2partition(v)) sum += 1
//    }}

    val bcMap: Broadcast[Map[Long, Int]] = context.broadcast(node2partition)
    val sum: LongAccumulator = context.longAccumulator("cut")
    Graph.edgeList.foreachPartition{it =>
      var s = 0
      it.foreach{ case (u, v, _) =>
        if (bcMap.value(u) != bcMap.value(v)) s += 1
      }
      sum.add(s)
    }
    println(sum.value)
    println(sum.value.toDouble / Graph.edgeList.count())
  }
}