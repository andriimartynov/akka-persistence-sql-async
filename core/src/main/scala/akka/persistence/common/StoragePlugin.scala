/*
 * Copyright 2014 okumin.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.common

import akka.actor.{Actor, ActorLogging}
import akka.serialization.{Serialization, SerializationExtension}
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scalikejdbc._
import scalikejdbc.async._

private[persistence] trait StoragePlugin extends Actor with ActorLogging {
  implicit protected[this] def persistenceExecutor: ExecutionContext = context.dispatcher
  protected[this] val serialization: Serialization = SerializationExtension(context.system)
  protected[this] lazy val extension: ScalikeJDBCExtension = ScalikeJDBCExtension(context.system)
  protected[this] lazy val sessionProvider: ScalikeJDBCSessionProvider = extension.sessionProvider

  protected[this] lazy val metadataTable = {
    val tableName = extension.config.metadataTableName
    SQLSyntaxSupportFeature.verifyTableName(tableName)
    SQLSyntax.createUnsafely(tableName)
  }

  // PersistenceId => its surrogate key
  private[this] val persistenceIds: TrieMap[String, Long] = TrieMap.empty
  protected[this] def surrogateKeyOf(persistenceId: String)(
      implicit session: TxAsyncDBSession): Future[Long] = {
    persistenceIds.get(persistenceId) match {
      case Some(id) => Future.successful(id)
      case None =>
        val select =
          sql"SELECT persistence_key FROM $metadataTable WHERE persistence_id = $persistenceId"
        select.map(_.long("persistence_key")).single().future().flatMap {
          case Some(id) =>
            persistenceIds.update(persistenceId, id)
            Future.successful(id)
          case None =>
            val insert =
              sql"INSERT INTO $metadataTable (persistence_id, sequence_nr) VALUES ($persistenceId, 0)"
            for {
              _ <- insert.update().future()
              id <- lastInsertId()
            } yield {
              persistenceIds.update(persistenceId, id)
              id
            }
        }
    }
  }

  protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long]

  protected[this] def logging(sql: SQL[Nothing, NoExtractor]): SQL[Nothing, NoExtractor] = {
    log.debug(s"Execute {} binding {}", sql.statement, sql.parameters)
    sql
  }
}

private[persistence] trait MySQLPlugin extends StoragePlugin {
  override protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long] = {
    val sql = sql"SELECT LAST_INSERT_ID() AS id;"
    sql
      .map(_.long("id"))
      .single()
      .future()
      .map(_.getOrElse(sys.error("Failed to fetch a last insert id.")))
  }
}

private[persistence] trait PostgreSQLPlugin extends StoragePlugin {
  override protected[this] def lastInsertId()(implicit session: TxAsyncDBSession): Future[Long] = {
    val sql = sql"SELECT LASTVAL() AS id;"
    sql
      .map(_.long("id"))
      .single()
      .future()
      .map(_.getOrElse(sys.error("Failed to fetch a last insert id.")))
  }
}
