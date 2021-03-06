package com.github.dnvriend.repo.dynamodb

import java.util.UUID

import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{ AmazonDynamoDB, AmazonDynamoDBClientBuilder }
import com.github.dnvriend.lambda.SamContext
import com.github.dnvriend.repo.JsonRepository
import play.api.libs.json.{ Json, Reads, Writes }

import scala.collection.JavaConverters._
import scalaz.Scalaz._
import scalaz._

object DynamoDBJsonRepository {
  def apply(
    tableName: String,
    ctx: SamContext,
    idAttributeName: String = "id",
    payloadAttributeName: String = "json"): JsonRepository = {
    new DynamoDBJsonRepository(tableName, ctx, idAttributeName, payloadAttributeName)
  }
}

/**
 * JsonDynamoDBRepository is a repository with only two attributes,
 * an 'id' and 'json' attribute. It stores a payload as JSON string
 * in the 'json' attribute. The attribute names of 'id' and 'json' are
 * configurable.
 */
class DynamoDBJsonRepository(
    tableName: String,
    ctx: SamContext,
    idAttributeName: String = "id",
    payloadAttributeName: String = "json") extends JsonRepository {
  val table: String = ctx.dynamoDbTableName(tableName)
  val db: AmazonDynamoDB = AmazonDynamoDBClientBuilder.defaultClient()

  /**
   * Returns a random UUID as String
   */
  def id(): String = UUID.randomUUID.toString

  /**
   * Marshal value 'A' to Json String
   */
  private def marshal[A: Writes](value: A): String = {
    Json.toJson(value).toString
  }

  /**
   * Unmarshal Json String to value 'A'
   */
  private def unmarshal[A: Reads](json: String): A = {
    Json.parse(json).as[A]
  }

  /**
   * Stores a value with key 'id'
   */
  override def put[A: Writes](id: String, value: A): Unit = {
    val result: String = Disjunction.fromTryCatchNonFatal {
      db.putItem(
        new PutItemRequest()
          .withTableName(table)
          .withReturnValues(ReturnValue.NONE)
          .withItem(
            Map(
              idAttributeName -> new AttributeValue(id),
              payloadAttributeName -> new AttributeValue(marshal(value))
            ).asJava
          )
      )
    }.bimap(t => t.getMessage, result => result.toString).merge
    ctx.logger.log(result)
  }

  /**
   * Returns a value, if present with key 'id'
   */
  override def find[A: Reads](id: String): Option[A] = {
    val result: Disjunction[String, A] = for {
      attributes <- Disjunction.fromTryCatchNonFatal(db.getItem(table, Map(idAttributeName -> new AttributeValue(id)).asJava).getItem.asScala).leftMap(_.getMessage)
      jsonField <- Validation.lift(attributes)(attr => attr.get(payloadAttributeName).isEmpty, s"No '$payloadAttributeName' attribute in table").map(_.get(payloadAttributeName)).disjunction
      jsonString <- jsonField.toRightDisjunction(s"No '$payloadAttributeName' attribute in table").map(_.getS)
      value <- Disjunction.fromTryCatchNonFatal(Json.parse(jsonString).as[A]).leftMap(_.getMessage)
    } yield value

    if (result.isLeft) {
      val message: String = result.swap.getOrElse(s"Error while finding value for key '$id'")
      ctx.logger.log(message)
    }

    result.toOption
  }

  /**
   * Updates a value with key 'id'
   */
  override def update[A: Writes](id: String, value: A): Unit = {
    val result: String = Disjunction.fromTryCatchNonFatal {
      db.updateItem(table, Map(idAttributeName -> new AttributeValue(id)).asJava, Map(payloadAttributeName -> new AttributeValueUpdate(new AttributeValue(marshal(value)), AttributeAction.PUT)).asJava)
    }.bimap(t => t.getMessage, result => result.toString).merge
    ctx.logger.log(result)
  }

  /**
   * Deletes a value with key 'id'
   */
  override def delete(id: String): Unit = {
    val result: String = Disjunction.fromTryCatchNonFatal {
      db.deleteItem(table, Map(idAttributeName -> new AttributeValue(id)).asJava)
    }.bimap(t => t.getMessage, result => result.toString).merge
    ctx.logger.log(result)
  }

  /**
   * Returns a list of values, default 100 items
   */
  override def list[A: Reads](limit: Int = 100): List[(String, A)] = {
    Disjunction.fromTryCatchNonFatal {
      db.scan(new ScanRequest()
        .withTableName(table)
        .withAttributesToGet(idAttributeName, payloadAttributeName)
        .withLimit(limit)
      ).getItems.asScala.map(m => (m.get(idAttributeName).getS, m.get(payloadAttributeName).getS))
        .map { case (id, json) => (id, Json.parse(json).as[A]) }
        .toList
    }.valueOr { error =>
      ctx.logger.log(error.getMessage)
      List.empty[(String, A)]
    }
  }
}
