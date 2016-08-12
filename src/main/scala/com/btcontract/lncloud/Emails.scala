package com.btcontract.lncloud

import com.btcontract.lncloud.Utils._
import collection.JavaConverters.mapAsScalaConcurrentMapConverter
import concurrent.ExecutionContext.Implicits.global
import com.btcontract.lncloud.database.Database
import java.util.concurrent.ConcurrentHashMap
import org.bitcoinj.core.ECKey.ECDSASignature
import org.bitcoinj.core.Utils.HEX
import org.bitcoinj.core.ECKey
import courier.Multipart


class Emails(db: Database) {
  val cache = new ConcurrentHashMap[String, CacheItemSignedMail].asScala
  val masterPrivECKey = ECKey fromPrivate values.emailPrivKey
  type CacheItemSignedMail = CacheItem[SignedMail]

  val languages = Map.empty updated
    ("eng", "Confirm your email address" :: "Use your wallet to scan an attached QR-code" :: Nil) updated
    ("rus", "Подтвердите ваш почтовый адрес" :: "Отсканируйте QR-код с помощью вашего кошелька" :: Nil)

  def sendEmail(secret: String, lang: String, address: String) = {
    val title :: message :: Nil = languages.getOrElse(lang, languages apply "eng")
    val parts = Multipart(Nil).attachBytes(QRGen.get(s"lncloud:secret$secret", 300), "qr.png", "image/png")
    values.emailParams mailer values.emailParams.to(address).subject(title).content(parts text message)
  }

  def confirmEmail(secret: String) =
    cache get secret map { case CacheItem(data, stamp) =>
      val serverSig = HEX encode masterPrivECKey.sign(data.totalHash).encodeToDER
      val serverSignedMail = ServerSignedMail(data, serverSig)
      db putSignedMail serverSignedMail
      serverSignedMail
    }

  def servSigOk(ssm: ServerSignedMail) = {
    val serverSig = ECDSASignature.decodeFromDER(HEX decode ssm.signature)
    masterPrivECKey.verify(ssm.client.totalHash, serverSig)
  }
}