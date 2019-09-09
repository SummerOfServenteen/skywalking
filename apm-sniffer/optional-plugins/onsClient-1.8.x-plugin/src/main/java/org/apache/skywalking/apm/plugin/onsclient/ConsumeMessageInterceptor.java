/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.onsclient;

import com.aliyun.openservices.ons.api.Message;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;


public class ConsumeMessageInterceptor implements InstanceMethodsAroundInterceptor {
    private static final String CONSUMER_OPERATION_NAME_PREFIX = "MQ/";

    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                                   Class<?>[] argumentsTypes,
                                   MethodInterceptResult result) throws Throwable {
        Message message = (Message) allArguments[0];

        ContextCarrier contextCarrier = getContextCarrierFromMessage(message);
        AbstractSpan span = ContextManager.createEntrySpan(buildOperationName(message.getTopic(),
                message.getTag()), contextCarrier);

        span.setComponent(ComponentsDefine.ROCKET_MQ_CONSUMER);
        byte[] body = message.getBody();
        String messageBody = body == null ? "" : new String(body);
        StringTag mqBody = new StringTag(11,"mq.body");
        mqBody.set(span, messageBody);
        SpanLayer.asMQ(span);
        ContextManager.extract(getContextCarrierFromMessage(message));

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                            Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }

    private ContextCarrier getContextCarrierFromMessage(Message message) {
        ContextCarrier contextCarrier = new ContextCarrier();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(message.getUserProperties(next.getHeadKey()));
        }

        return contextCarrier;
    }

    private String buildOperationName(String topic, String tag) {
        return CONSUMER_OPERATION_NAME_PREFIX + "Consumer/" + topic + "/" + tag;
    }
}
