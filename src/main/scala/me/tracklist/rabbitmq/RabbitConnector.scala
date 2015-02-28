package me.tracklist.rabbitmq

/**
 * RabbitMQ java client library imports
 **/
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.AMQP

/**
 * Constructor may through IOException
 **/
class RabbitConnector(
  val host: String,
  val port: Int, 
  val username: String,
  val password: String,
  val queue: String,
  val queueType: RabbitConnector.QueueType) {

  /**
   * Factory of RabbitMQ connections
   **/ 
  val factory = new ConnectionFactory()
  factory.setHost(host)
  factory.setPort(port)
  factory.setUsername(username)
  factory.setPassword(password)
  /**
   * A rabbitMQ connection
   **/ 
  val connection = factory.newConnection()
  /**
   * A rabbitMQ channel, API interface to the AMQP broker
   **/
  val channel = connection.createChannel()
  /**
   * We force each consumer to prefetch no more than 1 message
   * at a time
   **/
  channel.basicQos(1, false)
  /**
   * We declare the queue, in case it does not exist
   **/
  queueType match {
    case RabbitConnector.DURABLE => channel.queueDeclare(queue, true, false, false, null)
    case RabbitConnector.NON_DURABLE => channel.queueDeclare(queue, false, false, false, null)
  }
  /**
   * Counter of consumers that have been registered by this connector
   **/
  var consumerCounter = 0L

  /**
   * Consumes a message in a non-blocking manner, callback is called once the message is received
   **/
  def nonBlockingConsume(callback: String => Unit) {
    val autoAck = false;
    channel.basicConsume(queue, autoAck, "Consumer"+consumerCounter,
      new DefaultConsumer(channel) {
        var disabled = false;

        override def handleDelivery(consumerTag: String,
                                    envelope: Envelope,
                                    properties: AMQP.BasicProperties,
                                    body: Array[Byte]) {

          val routingKey = envelope.getRoutingKey()
          val contentType = properties.getContentType()
          val deliveryTag = envelope.getDeliveryTag()

          // We should never get here as we disable the 
          // consumer after the first fetch
          if (disabled) {
            channel.basicNack(deliveryTag, false, true)
            return
          }

          val bodyString = new String(body)

          println ("Consumer "+consumerTag+" consuming")
          callback(bodyString)

          // We unregister the consumer as we only want to consume one message at a time
          disabled = true
          channel.basicCancel(consumerTag)

          // We acknoledge the message
          channel.basicAck(deliveryTag, false)

        }
      })
    consumerCounter = consumerCounter + 1
  }

  /**
   * Publishes a message to the connector's queue
   * message: message content as a string
   * contentType: type of the message (e.g. application/json)
   * persistent: true if the message should survive to server
   **/
  def blockingPublish(message: String, contentType: String, persistent: Boolean) {
    var propertiesBuilder = new AMQP.BasicProperties.Builder()
                      .contentType(contentType)
                      .priority(1)
    if (persistent) propertiesBuilder = propertiesBuilder.deliveryMode(2)
    channel.basicPublish("", queue, propertiesBuilder.build(), message.getBytes());
  }

  /**
   * Closes the connector
   **/
  def close() {
    channel.close();
    connection.close();
  }

}


object RabbitConnector {
  def apply(host: String, port: Int, username: String, password: String, queue: String, queueType: QueueType) = 
    new RabbitConnector(host, port, username, password, queue, queueType)

  /**
   * Trait of queue types, can be estended only here
   **/
  sealed trait QueueType
  case object DURABLE extends QueueType
  case object NON_DURABLE extends QueueType
}
