package play.api.data.mapping.json4s

import play.api.data.mapping._

import org.json4s._
import org.json4s.native.JsonMethods._

trait DefaultMonoids {
  import play.api.libs.functional.Monoid

  implicit def jsonMonoid = new Monoid[JObject] {
    def append(a1: JObject, a2: JObject) = a1 merge a2
    def identity = JObject()
  }
}

object Writes extends DefaultWrites with DefaultMonoids with GenericWrites[JValue] {

  private def writeObj(j: JValue, n: PathNode) = n match {
    case IdxPathNode(_) => JArray(j :: Nil)
    case KeyPathNode(key) => JObject(key -> j)
  }

  implicit val validationError = Write[ValidationError, JValue] { err =>
    JObject(
      "msg" -> JString(err.message),
      "args" -> err.args.foldLeft(JArray(Nil)) { (arr, arg) =>
        val jv =  (arg match {
          case s: String => JString(s)
          case nb: Int => JInt(nb)
          case nb: Short => JInt(nb)
          case nb: Long => JInt(nb)
          case nb: Double => JDecimal(nb)
          case nb: Float => JDecimal(nb)
          case b: Boolean => JBool(b)
          case js: JValue => js
          case x => JString(x.toString)
        })
        JArray(arr.arr :+ jv)
      })
  }

  implicit def errors(implicit wErrs: WriteLike[Seq[ValidationError], JValue]) = Write[(Path, Seq[ValidationError]), JObject] {
    case (p, errs) =>
      JObject(p.toString -> wErrs.writes(errs))
  }

  implicit def failure[O](implicit w: WriteLike[(Path, Seq[ValidationError]), JObject]) = Write[Failure[(Path, Seq[ValidationError]), O], JObject] {
    case Failure(errs) =>
      errs.map(w.writes).reduce(_ merge _)
  }

  implicit val string: Write[String, JValue] =
    Write(s => JString(s))

  private def tToJs[T] = Write[T, JValue]{
    case i: Int => JInt(BigInt(i.toString))
    case i: Short => JInt(BigInt(i.toString))
    case i: Long => JInt(BigInt(i.toString))
    case i => JDecimal(BigDecimal(i.toString))
  }

  implicit def anyval[T <: AnyVal] = tToJs[T]
  implicit def javanumber[T <: java.lang.Number] = tToJs[T]

  implicit def booleanW = Write[Boolean, JValue](JBool.apply _)

  implicit def seqToJsArray[I](implicit w: WriteLike[I, JValue]): Write[Seq[I], JValue] =
    Write(ss => JArray(ss.map(w.writes _).toList))

  def optionW[I, J](r: => WriteLike[I, J])(implicit w: Path => WriteLike[J, JObject]): Path => Write[Option[I], JObject] =
    super.optionW[I, J, JObject](r, JObject())

  implicit def optionW[I](implicit w: Path => WriteLike[I, JObject]): Path => Write[Option[I], JObject] =
    optionW(Write.zero[I])

  implicit def mapW[I](implicit w: WriteLike[I, JValue]) = Write[Map[String, I], JObject] { m =>
    JObject(m.mapValues(w.writes).toList)
  }

  implicit def writeJson[I](path: Path)(implicit w: WriteLike[I, JValue]): Write[I, JObject] = Write { i =>
    path match {
      case Path(KeyPathNode(x) :: _) \: _ =>
        val ps = path.path.reverse
        val h = ps.head
        val o = writeObj(w.writes(i), h)
        ps.tail.foldLeft(o)(writeObj).asInstanceOf[JObject]
      case _ => throw new RuntimeException(s"path $path is not a path of JsObject") // XXX: should be a compile time error
    }
  }
}
