package au.com.dius.pact.model

import RequestPartMismatch._
import ResponsePartMismatch._
import scala.collection.immutable.TreeMap
import au.com.dius.pact.model.JsonDiff.DiffConfig

trait SharedMismatch {
  type Body = Option[String]
  type Headers = Map[String, String]
}

object RequestPartMismatch extends SharedMismatch {
  type Cookies = List[String]
  type Path = String
  type Method = String
  type Query = String
}

object ResponsePartMismatch extends SharedMismatch {
  type Status = Int
}

// Overlapping ADTs.  The body and headers can mismatch for both of them.
sealed trait RequestPartMismatch
sealed trait ResponsePartMismatch 

case class StatusMismatch(expected: Status, actual: Status) extends ResponsePartMismatch
case class HeaderMismatch(expected: Headers, actual: Headers) extends RequestPartMismatch with ResponsePartMismatch
case class BodyTypeMismatch(expected: String, actual: String) extends RequestPartMismatch with ResponsePartMismatch
case class BodyMismatch(expected: Body, actual: Body) extends RequestPartMismatch with ResponsePartMismatch
case class CookieMismatch(expected: Cookies, actual: Cookies) extends RequestPartMismatch
case class PathMismatch(expected: Path, actual: Path) extends RequestPartMismatch
case class MethodMismatch(expected: Method, actual: Method) extends RequestPartMismatch
case class QueryMismatch(expected: Query, actual: Query) extends RequestPartMismatch

trait BodyMatcher {
  def matchBody(expected: Option[String], actual: Option[String], diffConfig: DiffConfig) : List[BodyMismatch]
}

object Matching {
  
  def matchHeaders(expected: Option[Headers], actual: Option[Headers]): Option[HeaderMismatch] = {
    def compareHeaders(e: Map[String, String], a: Map[String, String]): Option[HeaderMismatch] = {
      
      def actuallyFound(kv: (String, String)): Boolean = {
        def compareNormalisedHeaders(expected: String, actual: String): Boolean = {
          def stripWhiteSpaceAfterCommas(in: String): String = in.replaceAll(",[ ]*", ",")
          stripWhiteSpaceAfterCommas(expected) == stripWhiteSpaceAfterCommas(actual)
        }
        a.get(kv._1).fold(false)(compareNormalisedHeaders(_, kv._2))
      }
      
      if (e forall actuallyFound) None 
      else Some(HeaderMismatch(e, a filterKeys e.contains))
    }
    
    def sortedOrEmpty(h: Option[Headers]): Map[String,String] = {
      def sortCaseInsensitive[T](in: Map[String, T]): TreeMap[String, T] = {
        new TreeMap[String, T]()(Ordering.by(_.toLowerCase)) ++ in
      }
      h.fold[Map[String,String]](Map())(sortCaseInsensitive)
    }
      
    compareHeaders(sortedOrEmpty(expected), sortedOrEmpty(actual))
  }

  def matchCookie(expected: Option[Cookies], actual: Option[Cookies]): Option[CookieMismatch] = {
    def compareCookies(e: Cookies, a: Cookies) = {
      if (e forall a.contains) None 
      else Some(CookieMismatch(e, a))
    }
    compareCookies(expected getOrElse Nil, actual getOrElse Nil)
  }

  def matchMethod(expected: Method, actual: Method): Option[MethodMismatch] = {
    if(expected.equalsIgnoreCase(actual)) None
    else Some(MethodMismatch(expected, actual))
  }

  def matchBody(expectedMimeType: String, expected: Body, actualMimeType: String, actual: Body, diffConfig: DiffConfig) = {
    if (expectedMimeType == actualMimeType) {
      if (PactConfig.bodyMatchers.contains(expectedMimeType)) {
        PactConfig.bodyMatchers(expectedMimeType).matchBody(expected, actual, diffConfig)
      } else {
        (expected, actual) match {
          case (None, None) => List()
          case (None, b) => if(diffConfig.structural) { List() } else { List(BodyMismatch(None, b)) }
          case (a, None) => List(BodyMismatch(a, None))
          case (a, b) => if (a == b) List() else List(BodyMismatch(a, b))
        }
      }
    } else {
      List(BodyTypeMismatch(expectedMimeType, actualMimeType))
    }
  }

  def matchPath(expected: Path, actual: Path): Option[PathMismatch] = {
    val pathFilter = "http[s]*://([^/]*)"
    val replacedActual = actual.replaceFirst(pathFilter, "")
    if(expected == replacedActual || replacedActual.matches(expected)) None
    else Some(PathMismatch(expected, replacedActual))
  }
  
  def matchStatus(expected: Int, actual: Int): Option[StatusMismatch] = {
    if(expected == actual) None
    else Some(StatusMismatch(expected, actual))
  }

  def matchQuery(expected: Option[Query], actual: Option[Query]): Option[QueryMismatch] = {
    (expected, actual) match {
      case (None, None) => None
      case (Some(a), None) => Some(QueryMismatch(a, ""))
      case (None, Some(b)) => Some(QueryMismatch("", b))
      case (Some(a), Some(b)) => if (a == b) { None } else { Some(QueryMismatch(a, b)) }
    }
  }
}
