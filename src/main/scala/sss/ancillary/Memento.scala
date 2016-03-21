package sss.ancillary

import java.io.File
import java.io.PrintWriter
import scala.concurrent._

/**
 * A trait to mixin in the memento functionality.
 *
 */
trait DefaultMemento {
  val mementoId: String
  val mementoGroupId: Option[String] = None
  lazy val memento = Memento(mementoId, mementoGroupId)
}

/**
 * Simple class to read, write and clear 'small' strings
 * that will survive restarts.
 *
 * See "memento" pattern.
 */
class Memento(id: String, groupId: Option[String] = None) {

  def read: Option[String] = Memento.read(id, groupId)
  def write(memento: String, async: Boolean = false): Option[Future[Any]] = Memento.write(id, memento, groupId, async)
  def clear = Memento.clear(id, groupId)

}

/**
 * The implementation class, provides 'static' access to the memento writing methods.
 */
object Memento extends Configure with Logging {

  def apply(id: String, groupId: Option[String] = None) = new Memento(id, groupId)

  private lazy val mementoFolder = {
    val folder = new File(config.getString(s"memento.folder"))
    if ((!folder.exists() && !folder.isDirectory() && !folder.mkdirs()))
      throw new RuntimeException("Cannot create memento folder, permissions?")
    log.info(s"The memento root folder is ${folder.getAbsolutePath}")
    folder
  }


  def clear(id: String, groupId: Option[String] = None) = {
    val m = groupId match {
      case None => new File(mementoFolder, id)
      case Some(g) => new File(new File(mementoFolder, g), id)
    }

    if (m.exists()) { m.delete }
  }

  /**
   * Read the menento
   */
  def read(id: String, groupId: Option[String] = None): Option[String] = {

    val m = groupId match {
      case None => new File(mementoFolder, id)
      case Some(g) => new File(new File(mementoFolder, g), id)
    }

    if (m.exists()) {
      val source = scala.io.Source.fromFile(m)
      try {
        val lines = source.getLines
        Some(lines.mkString(""))
      } finally { source.close }
    } else {
      None
    }
  }

  /**
   * Write the memento
   */
  def write(id: String, memento: String,
    groupId: Option[String] = None,
    async: Boolean = false): Option[Future[Any]] = {

    def writeImpl {
      val file = groupId match {
        case None => new File(mementoFolder, id)
        case Some(g) => {
          val grouIdFolder = new File(mementoFolder, g)
          if ((!grouIdFolder.exists() && !grouIdFolder.isDirectory() && !grouIdFolder.mkdirs()))
            throw new RuntimeException(s"Cannot create group Id folder ${grouIdFolder}")
          new File(grouIdFolder, id)
        }
      }

      log.debug(s"writing ${memento} to ${file}")
      val out = new PrintWriter(file, "UTF-8")
      try { out.print(memento) }
      finally { out.close }
    }

    if (async) {
      import ExecutionContext.Implicits.global
      Some(Future { writeImpl })
    } else {
      writeImpl
      None
    }

  }

}
