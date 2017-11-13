// Copyright 2017 Dennis Vriend
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.dnvriend.lambda

import com.amazonaws.services.lambda.runtime.{ Context, RequestStreamHandler }
import java.io.{ InputStream, OutputStream }

trait DynamoDBHandler extends RequestStreamHandler {
  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val request: DynamoDbRequest = DynamoDbRequest.parse(input)
    handle(request, context)
  }

  def handle(request: DynamoDbRequest, ctx: Context): Unit
}