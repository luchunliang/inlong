/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.protocol.transformation;

import org.apache.inlong.sort.protocol.transformation.function.BetweenFunction;
import org.apache.inlong.sort.protocol.transformation.function.MultiValueFilterFunction;
import org.apache.inlong.sort.protocol.transformation.function.SingleValueFilterFunction;

import lombok.Data;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonSubTypes;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * interface for filter functions
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SingleValueFilterFunction.class, name = "singleValueFilter"),
        @JsonSubTypes.Type(value = MultiValueFilterFunction.class, name = "multiValueFilter"),
        @JsonSubTypes.Type(value = BetweenFunction.class, name = "betweenFunction")
})
@Data
public abstract class FilterFunction implements Function {

    @JsonProperty("logicOperator")
    protected LogicOperator logicOperator;
}
