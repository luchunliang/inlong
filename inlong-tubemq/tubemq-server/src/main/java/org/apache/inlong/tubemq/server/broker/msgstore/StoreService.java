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

package org.apache.inlong.tubemq.server.broker.msgstore;

import org.apache.inlong.tubemq.server.broker.offset.topicpub.TopicPubInfo;
import org.apache.inlong.tubemq.server.broker.utils.TopicPubStoreInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Store service interface.
 */
public interface StoreService {

    void start();

    void close();

    Set<String> removeTopicStore();

    Collection<MessageStore> getMessageStoresByTopic(String topic);

    MessageStore getOrCreateMessageStore(String topic,
            int partition) throws Throwable;

    Map<String, Map<Integer, TopicPubStoreInfo>> getTopicPublishInfos(Set<String> topicSet);

    // Add the current storage offset values to
    // the consumption partition records of the specified consumption group
    // include maximum and minimum, and consume lag
    Map<String, TopicPubInfo> getTopicPublishInfos();
}
