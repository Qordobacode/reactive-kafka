package com.softwaremill.react.kafka

import java.util.UUID

import com.softwaremill.react.kafka.KafkaMessages._
import org.reactivestreams.tck.SubscriberWhiteboxVerification.{SubscriberPuppet, WhiteboxSubscriberProbe}
import org.reactivestreams.tck.{SubscriberWhiteboxVerification, TestEnvironment}
import org.reactivestreams.{Subscriber, Subscription}
import org.scalatest.testng.TestNGSuiteLike

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

class ReactiveKafkaSubscriberWhiteboxSpec(defaultTimeout: FiniteDuration)
    extends SubscriberWhiteboxVerification[StringProducerMessage](new TestEnvironment(defaultTimeout.toMillis))
    with TestNGSuiteLike with ReactiveStreamsTckVerificationBase {

  def this() = this(300 millis)

  override def createSubscriber(whiteboxSubscriberProbe: WhiteboxSubscriberProbe[StringProducerMessage]): Subscriber[StringProducerMessage] = {
    val topic = UUID.randomUUID().toString
    new SubscriberDecorator(kafka.publish(ProducerProperties(kafkaHost, topic, serializer)), whiteboxSubscriberProbe)
  }

  override def createElement(i: Int) = ProducerMessage(i.toString)
}

class SubscriberDecorator[T](decoratee: Subscriber[T], probe: WhiteboxSubscriberProbe[T]) extends Subscriber[T] {

  override def onSubscribe(subscription: Subscription): Unit = {
    decoratee.onSubscribe(subscription)

    // register a successful Subscription, and create a Puppet,
    // for the WhiteboxVerification to be able to drive its tests:
    probe.registerOnSubscribe(new SubscriberPuppet() {
      override def triggerRequest(elements: Long): Unit = {
        subscription.request(elements)
      }

      override def signalCancel(): Unit = {
        subscription.cancel()
      }
    })
  }

  override def onNext(t: T): Unit = {
    decoratee.onNext(t)
    probe.registerOnNext(t)
  }

  override def onError(throwable: Throwable): Unit = {
    decoratee.onError(throwable)
    probe.registerOnError(throwable)
  }

  override def onComplete(): Unit = {
    decoratee.onComplete()
    probe.registerOnComplete()
  }
}