package com.github.dnvriend.sam.serialization
package serializer

import com.github.dnvriend.sam.serialization.crypto.AwsEncryption
import com.github.dnvriend.sam.serialization.record.SamRecord
import com.github.dnvriend.sam.serialization.resolver.SchemaResolver
import com.sksamuel.avro4s.{FromRecord, SchemaFor, ToRecord}
import com.typesafe.scalalogging.LazyLogging

import scala.reflect.ClassTag
import scalaz.Scalaz._

object SamSerializer extends LazyLogging {

  def serialize[A: ToRecord](
      value: A,
      cmkArn: Option[String])
    (implicit schemaFor: SchemaFor[A], ct: ClassTag[A]): DTry[SamRecord] = {
    logger.info(
      """
        |=========================================================================================================
        |Serializing: '{}'
        |=========================================================================================================
        |cmkArn: '{}'
        |namespaceName: '{}'
        |schemaName: '{}'
      """.stripMargin,
      ct.runtimeClass.getCanonicalName,
      cmkArn.getOrElse("No cmk arn"),
      schemaFor().getNamespace,
      schemaFor().getName,
    )
   val serialized: DTry[SamRecord] = for {
      fingerprint <- AvroUtils.fingerPrint(schemaFor())
      fingerprintHex <- AvroUtils.encodeHex(fingerprint)
      avro <- AvroUtils.serialize(value)
      data <- AvroUtils.compress(avro)
      cipher <- cmkArn.fold(success(data))(arn => AwsEncryption(arn).encryptBytes(data))
      hex <- AvroUtils.encodeHex(cipher)
    } yield SamRecord(schemaFor().getNamespace, schemaFor().getName, fingerprintHex, hex)

    if(serialized.isLeft) {
      logger.error("error while serializing class: '{}', error: '{}'",
        ct.runtimeClass.getCanonicalName,
        serialized.swap.foldMap(_.getMessage),
      )
    }

    serialized
  }

  def deserialize[R: FromRecord](
    record: SamRecord,
    resolver: SchemaResolver,
    cmkArn: Option[String])(implicit ct: ClassTag[R], schemaForR: SchemaFor[R]): DTry[R] = {

    logger.info(
      """
        |=========================================================================================================
        |Deserializing: '{}'
        |=========================================================================================================
        |cmkArn: '{}'
        |namespaceName: '{}'
        |schemaName: '{}'
        |fingerprint: '{}'
        |payload: '{}'
      """.stripMargin,
      ct.runtimeClass.getCanonicalName,
      cmkArn.getOrElse("No cmk arn"),
      schemaForR().getNamespace,
      schemaForR().getName,
      record.fingerprint,
      record.payload,
    )

    val deserialized: DTry[R] = for {
      schema <- resolver.resolve(record.fingerprint).orFail("No fingerprint found")
      data <- AvroUtils.decodeHex(record.payload)
      plaintext <- cmkArn.fold(success(data))(arn => AwsEncryption(arn).decryptBytesResult(data))
      avro <- AvroUtils.decompress(plaintext)
      result <- AvroUtils.deserialize(avro, schema)
    } yield result

    if(deserialized.isLeft) {
      logger.error("error while deserializing to reader class: '{}': '{}'",
        ct.runtimeClass.getCanonicalName,
        deserialized.swap.foldMap(_.getMessage),
      )
    }

    // if the type could not be deserialized, return the default instance
    deserialized.orElse(DefaultInstance[R])
  }
}