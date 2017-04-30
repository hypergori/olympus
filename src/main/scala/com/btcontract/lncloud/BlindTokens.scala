package com.btcontract.lncloud

import com.btcontract.lncloud.Utils._
import rx.lang.scala.{Observable => Obs}
import fr.acinq.bitcoin.{BinaryData, MilliSatoshi}

import collection.JavaConverters.mapAsScalaConcurrentMapConverter
import concurrent.ExecutionContext.Implicits.global
import com.btcontract.lncloud.crypto.ECBlindSign
import com.github.kevinsawicki.http.HttpRequest
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.DurationInt
import org.spongycastle.math.ec.ECPoint
import com.lightning.wallet.ln.Invoice
import org.bitcoinj.core.Utils.HEX
import org.bitcoinj.core.ECKey
import scala.concurrent.Future
import java.math.BigInteger


class BlindTokens { me =>
  type StampOpt = Option[Long]
  type SesKeyCacheItem = CacheItem[BigInteger]
  val signer = new ECBlindSign(values.privKey.bigInteger)
  val cache: collection.concurrent.Map[String, SesKeyCacheItem] =
    new ConcurrentHashMap[String, SesKeyCacheItem].asScala

  // Periodically remove used and outdated requests
  Obs.interval(1.minute).map(_ => System.currentTimeMillis) foreach { now =>
    for (Tuple2(hex, item) <- cache if item.stamp < now - twoHours) cache remove hex
  }

  def isFulfilled(paymentHash: BinaryData): Future[Boolean] = Future {
    val httpRequest = HttpRequest post values.eclairUrl connectTimeout 5000
    val response = httpRequest.send("status=" + paymentHash.toString).body
    toClass[StampOpt](response).isDefined
  }

  def generateInvoice(price: MilliSatoshi): Future[Invoice] = Future {
    val httpRequest = HttpRequest post values.eclairUrl connectTimeout 5000
    val response = httpRequest.send("receive=" + price.amount).body
    Invoice parse response
  }

  def makeBlind(tokens: TokenSeq, k: BigInteger): Future[BlindData] =
    for (invoice: Invoice <- me generateInvoice values.price)
      yield BlindData(invoice, k, tokens)

  def signTokens(bd: BlindData): TokenSeq = for (token <- bd.tokens)
    yield signer.blindSign(new BigInteger(token), bd.k).toString

  def decodeECPoint(raw: String): ECPoint =
    ECKey.CURVE.getCurve.decodePoint(HEX decode raw)
}