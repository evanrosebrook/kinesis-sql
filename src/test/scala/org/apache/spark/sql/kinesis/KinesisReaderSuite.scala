/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.spark.sql.kinesis

import com.amazonaws.services.kinesis.model.{ListShardsRequest, ListShardsResult}
import com.amazonaws.services.kinesis.{AmazonKinesis, AmazonKinesisClient}
import org.apache.spark.SparkException
import org.apache.spark.sql.kinesis.KinesisTestUtils.{envVarNameForEnablingTests, shouldRunTests}
import org.apache.spark.sql.test.SharedSparkSession
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.PrivateMethodTester
import org.scalatest.mock.MockitoSugar

import scala.util.Try

class KinesisReaderSuite extends SharedSparkSession with PrivateMethodTester with MockitoSugar {

  protected var testUtils: KinesisTestUtils = _

  /** Run the test if environment variable is set or ignore the test */
  def testIfEnabled(testName: String)(testBody: => Unit) {
    if (shouldRunTests) {
      test(testName)(testBody)
    } else {
      ignore(s"$testName [enable by setting env var $envVarNameForEnablingTests=1]")(testBody)
    }
  }

  test("Should throw exception when there is no InstanceProfile") {
    val ex = intercept[ SparkException  ] {
      val kinesisReader =
        new KinesisReader(
          Map.empty[String, String],
          "Test",
          () => new AmazonKinesisClient(InstanceProfileCredentials.provider),
          KinesisTestUtils.endpointUrl
        )
      kinesisReader.getShards()
    }
  }

  test("Should throw exception when STSCredentials are incorrect") {
    val ex = intercept[ SparkException ] {
      val kinesisReader = new KinesisReader(
        Map.empty[ String, String],
        "Test",
        () => new AmazonKinesisClient(STSCredentials("role-arn", "session-name").provider),
        KinesisTestUtils.endpointUrl)
      kinesisReader.getShards()
    }
  }

  test("Should throw exception when BasicCredentials are incorrect") {
    val ex = intercept[ SparkException  ] {
      val kinesisReader =
        new KinesisReader(
          Map.empty[String, String],
          "Test",
          () => new AmazonKinesisClient(BasicCredentials("access-key", "secret-key").provider),
          KinesisTestUtils.endpointUrl
        )
      kinesisReader.getShards()
    }
  }

  test("Should page using nexToken without streamName") {

    val clientMock = mock[AmazonKinesis]

    when(clientMock.listShards(any[ListShardsRequest])).thenReturn(new ListShardsResult)
    
    val kinesisReader =
      new KinesisReader(
        Map.empty[String, String],
        "Test",
        () => clientMock,
        KinesisTestUtils.endpointUrl
      )
    kinesisReader.getShards()
  }

  testIfEnabled("Should succeed for valid Credentials") {
    Try {
      val kinesisReader =
        new KinesisReader(
          Map.empty[String, String],
          "Test",
          () => new AmazonKinesisClient(BasicCredentials(
            KinesisTestUtils.getAWSCredentials().getAWSAccessKeyId,
            KinesisTestUtils.getAWSCredentials().getAWSSecretKey
          ).provider),
          KinesisTestUtils.endpointUrl
        )
      kinesisReader.getShards()
    }.isSuccess
  }

  testIfEnabled("getShardIterator should return null when shard-id is incorrect" +
    " and failOnDataLoss is false") {
    val kinesisReader =
      new KinesisReader(
        Map.empty[String, String],
        "Test",
        () => new AmazonKinesisClient(BasicCredentials(
          KinesisTestUtils.getAWSCredentials().getAWSAccessKeyId,
          KinesisTestUtils.getAWSCredentials().getAWSSecretKey
        ).provider),
        KinesisTestUtils.endpointUrl
      )
    val shardIterator = kinesisReader.getShardIterator("BAD-SHARD-ID", "LATEST",
      "", false)
    assert(shardIterator === null)
  }

  testIfEnabled("getShardIterator should throw exception when shard-id is incorrect" +
    " and failOnDataLoss is true") {
    val ex = intercept[ SparkException  ] {
      val kinesisReader =
        new KinesisReader(
          Map.empty[String, String],
          "Test",
          () => new AmazonKinesisClient(BasicCredentials(
            KinesisTestUtils.getAWSCredentials().getAWSAccessKeyId,
            KinesisTestUtils.getAWSCredentials().getAWSSecretKey
          ).provider),
          KinesisTestUtils.endpointUrl
        )
      val shardIterator = kinesisReader.getShardIterator("BAD-SHARD-ID", "LATEST",
        "", true)
    }
  }
}
