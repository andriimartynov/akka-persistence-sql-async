package akka.persistence.journal.sqlasync

import akka.persistence.common.PluginSettings
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try
import scalikejdbc._
import scalikejdbc.async._

private[sqlasync] trait ScalikeJDBCWriteJournal extends AsyncWriteJournal with PluginSettings {
  private[this] lazy val journalTable = {
    val tableName = extension.config.journalTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  protected[this] def updateSequenceNr(persistenceId: String, sequenceNr: Long)(implicit session: TxAsyncDBSession): Future[Unit]

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    log.debug("Write messages, {}", messages)
    sessionProvider.localTx { implicit session =>
      val batch = messages.foldLeft(Future.successful[Vector[Try[Vector[SQLSyntax]]]](Vector.empty)) { (acc, writes) =>
        for {
          lists <- acc
          key <- surrogateKeyOf(writes.persistenceId)
        } yield {
          lists :+ writes.payload.foldLeft(Try[Vector[SQLSyntax]](Vector.empty)) { (ss, x) =>
            for {
              xs <- ss
              bytes <- serialization.serialize(x)
            } yield xs :+ sqls"($key, ${x.sequenceNr}, $bytes)"
          }
        }
      }

      for {
        b <- batch
        records = sqls.csv(b.map(_.getOrElse(Nil)).flatten: _*)
        sql = sql"INSERT INTO $journalTable (persistence_key, sequence_nr, message) VALUES $records"
        _ <- logging(sql).update().future()
        result <- b.foldLeft(Future.successful[Vector[Try[Unit]]](Vector.empty)) { (acc, x) =>
          acc.map(_ :+ x.map(_ => ()))
        }
      } yield result
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    log.debug("Delete messages, persistenceId = {}, toSequenceNr = {}", persistenceId, toSequenceNr)
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        select = sql"SELECT sequence_nr FROM $journalTable WHERE persistence_key = $key ORDER BY sequence_nr DESC LIMIT 1"
        delete = sql"DELETE FROM $journalTable WHERE persistence_key = $key AND sequence_nr <= $toSequenceNr"
        highest <- logging(select).map(_.long("sequence_nr")).single().future().map(_.getOrElse(0L))
        _ <- logging(delete).update().future()
        _ <- if (highest <= toSequenceNr) updateSequenceNr(persistenceId, highest) else Future.successful(())
      } yield ()
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug("Replay messages, persistenceId = {}, fromSequenceNr = {}, toSequenceNr = {}", persistenceId, fromSequenceNr, toSequenceNr)
    sessionProvider.localTx { implicit session =>
      for {
        key <- surrogateKeyOf(persistenceId)
        sql = sql"SELECT message FROM $journalTable WHERE persistence_key = $key AND sequence_nr >= $fromSequenceNr AND sequence_nr <= $toSequenceNr ORDER BY sequence_nr ASC LIMIT $max"
        _ <- logging(sql).map(_.bytes("message")).list().future().map { messages =>
          messages.foreach { bytes =>
            val message = serialization.deserialize(bytes, classOf[PersistentRepr]).get
            replayCallback(message)
          }
        }
      } yield ()
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug("Read the highest sequence number, persistenceId = {}, fromSequenceNr = {}", persistenceId, fromSequenceNr)
    sessionProvider.localTx { implicit session =>
      val fromPersistenceIdTable = sql"SELECT persistence_key, sequence_nr FROM $persistenceIdTable WHERE persistence_id = $persistenceId"
      logging(fromPersistenceIdTable).map { result =>
          (result.long("persistence_key"), result.long("sequence_nr"))
      }.single().future().flatMap {
        case None => Future.successful(fromSequenceNr)
        case Some((key, sequenceNr)) =>
          val fromJournalTable = sql"SELECT sequence_nr FROM $journalTable WHERE persistence_key = $key ORDER BY sequence_nr DESC LIMIT 1"
          logging(fromJournalTable).map(_.long("sequence_nr")).single().future().map(_.getOrElse(0L))
      }
    }
  }
}
