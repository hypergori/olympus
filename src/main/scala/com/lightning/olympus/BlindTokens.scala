package com.lightning.olympus

import com.lightning.wallet.ln._
import collection.JavaConverters._
import com.lightning.olympus.Utils._
import org.json4s.jackson.JsonMethods._

import rx.lang.scala.{Observable => Obs}
import fr.acinq.bitcoin.{BinaryData, MilliSatoshi}
import com.github.kevinsawicki.http.HttpRequest
import com.lightning.olympus.crypto.ECBlindSign
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.DurationInt
import org.json4s.jackson.Serialization
import org.spongycastle.math.ec.ECPoint
import org.bitcoinj.core.Utils.HEX
import org.bitcoinj.core.ECKey
import java.math.BigInteger
import Utils.values


class BlindTokens { me =>
  type SesKeyCacheItem = CacheItem[BigInteger]
  val signer = new ECBlindSign(values.bigIntegerPrivKey)
  val cache = new ConcurrentHashMap[String, SesKeyCacheItem].asScala
  def decodeECPoint(raw: String): ECPoint = ECKey.CURVE.getCurve.decodePoint(HEX decode raw)
  def sign(data: BlindData) = for (tn <- data.tokens) yield signer.blindSign(new BigInteger(tn), data.k).toString

  // Periodically remove used and outdated requests
  Obs.interval(2.hours).map(_ => System.currentTimeMillis) foreach { now =>
    for (hex \ item <- cache if item.stamp < now - 20.minutes.toMillis) cache remove hex
  }

  def generateInvoice(price: MilliSatoshi): PaymentRequest = {
    val params = Map("params" -> List(price.amount, "Storage tokens"), "method" -> "receive")
    val request = HttpRequest.post(values.eclairApi).connectTimeout(5000).contentType("application/json")
    val raw = parse(request.send(Serialization write params).body) \ "result"
    PaymentRequest read raw.values.toString
  }

  def isFulfilled(hash: BinaryData): Boolean = {
    val params = Map("params" -> List(hash.toString), "method" -> "checkpayment")
    val request = HttpRequest.post(values.eclairApi).connectTimeout(5000).contentType("application/json")
    val raw = parse(request.send(Serialization write params).body) \ "result"
    raw.extract[Boolean]
  }
}