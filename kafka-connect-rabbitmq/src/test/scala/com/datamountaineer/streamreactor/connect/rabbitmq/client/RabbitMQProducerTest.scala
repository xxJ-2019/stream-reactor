package com.datamountaineer.streamreactor.connect.rabbitmq.client

import com.datamountaineer.streamreactor.connect.rabbitmq.TestBase
import com.rabbitmq.client.{AMQP, DefaultConsumer, Envelope}
import org.apache.kafka.connect.data.Struct
import org.apache.kafka.connect.sink.SinkRecord
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}

class RabbitMQProducerTest extends WordSpec with TestBase with Matchers with BeforeAndAfterEach {
    val schema = getMeasurementSchema()
    val struct = getMeasurementStruct(schema)
    private val props = getProps4KCQLsWithAllParametersNoConverters()
    private val producer = getMockedRabbitMQProducer(props)
    private val consumerChannel = producer.connection.createChannel()
    private val DEFAULT_QUEUE = consumerChannel.queueDeclare().getQueue()
    private val consumer = new DefaultConsumer(consumerChannel) {
        var messages: List[String] = List.empty

        override def handleDelivery(consumerTag: String,
                                    envelope: Envelope,
                                    properties: AMQP.BasicProperties,
                                    body: Array[Byte]) {
            messages = messages :+ new String(body, "UTF-8")
        }

        def empty() = messages = List.empty
    }
    for (elem <- (0 to 3)) {
        consumerChannel.exchangeDeclare(TARGETS(elem), "fanout", false, false, false, null)
        consumerChannel.queueBind(DEFAULT_QUEUE,TARGETS(elem),"")
    }
    consumerChannel.basicConsume(DEFAULT_QUEUE,true,consumer)
    producer.start()

    override def beforeEach(): Unit = {
        consumer.empty()
    }

    "RabbitMQProducer" should {
        "write nothing if an empty list is passed" in {
            producer.write(List.empty)
            Thread.sleep(PUBLISH_WAIT_TIME)
            consumer.messages.size shouldBe 0
        }

        "write the elements provided exactly once" in {
            val messages = generateSinkRecords()
            producer.write(messages)
            Thread.sleep(PUBLISH_WAIT_TIME)
            consumer.messages.size shouldBe 1000
            consumer.messages.foreach(e => e shouldBe TEST_MESSAGES.JSON_STRING)
        }

        "not write any elements when stopped" in {
            producer.stop()
            val record = new SinkRecord(SOURCES(0), 0, null, null, schema, struct, 1)
            producer.write(List.fill(100)(record))
            Thread.sleep(PUBLISH_WAIT_TIME)
            consumer.messages.size shouldBe 0
        }
    }

    private def generateSinkRecords(): List[SinkRecord] = {
        val record0 = new SinkRecord(SOURCES(0), 0, null, null, schema, struct, 1)
        val record1 = new SinkRecord(SOURCES(1), 0, null, null, schema, struct, 1)
        val record2 = new SinkRecord(SOURCES(2), 0, null, null, schema, struct, 1)
        val record3 = new SinkRecord(SOURCES(3), 0, null, null, schema, struct, 1)

        List.fill(240)(record0) ::: List.fill(270)(record1) ::: List.fill(230)(record2) ::: List.fill(260)(record3)
    }
}