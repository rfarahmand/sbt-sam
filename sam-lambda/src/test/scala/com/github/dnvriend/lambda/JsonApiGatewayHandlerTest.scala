package com.github.dnvriend.lambda

import com.github.dnvriend.lambda.generators.{ ApiGatewayEventGen, ApiGatewayEventGenBody, Generators }
import com.github.dnvriend.ops.AllOps
import com.github.dnvriend.test.TestSpec
import com.github.dnvriend.test.mock.MockContext
import play.api.libs.json.{ JsString, Json }

class JsonApiGatewayHandlerTest extends TestSpec with Generators with AllOps {
  it should "handle api gateway event" in {
    forAll { (event: ApiGatewayEventGen) =>
      val response: Array[Byte] = withOutputStream { os =>
        val handler = new JsonApiGatewayHandler[ApiGatewayEventGenBody] {
          override def handle(value: Option[ApiGatewayEventGenBody], pathParams: Map[String, String], requestParams: Map[String, String], request: HttpRequest, ctx: SamContext): HttpResponse = {
            value shouldBe 'defined
            HttpResponse.ok.withBody(Json.toJson(value.get))
          }
        }
        handler.handleRequest(event.json.toString.toInputStream, os, MockContext())
      }
      (Json.parse(response) \ "body").get shouldBe JsString(s"""{\"test\":\"${event.value}\"}""")
    }
  }

  it should "handle api gateway event with no body" in {
    import JsonReads._
    forAll { (event: ApiGatewayEventGen) =>
      val response: Array[Byte] = withOutputStream { os =>
        val handler = new JsonApiGatewayHandler[Nothing] {
          override def handle(value: Option[Nothing], pathParams: Map[String, String], requestParams: Map[String, String], request: HttpRequest, ctx: SamContext): HttpResponse = {
            value should not be 'defined
            HttpResponse.ok
          }
        }
        handler.handleRequest(event.json.toString.toInputStream, os, MockContext())
      }
      (Json.parse(response) \ "body").get shouldBe JsString("null")
    }
  }
}
