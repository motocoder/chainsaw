/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.log4j.net;

import org.apache.log4j.chainsaw.receiver.ChainsawReceiver;
import org.apache.log4j.chainsaw.receiver.ChainsawReceiverFactory;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;

/**
 *
 * @author robert
 */
public class PayloadProxyReceiverFactory implements ChainsawReceiverFactory {

    @Override
    public ChainsawReceiver create() {
        return new PayloadProxyReceiver();
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() throws IntrospectionException {
        return new PropertyDescriptor[] {
            new PropertyDescriptor("name", PayloadProxyReceiver.class),
//            new PropertyDescriptor("host", PayloadProxyReceiver.class),
            new PropertyDescriptor("guid", PayloadProxyReceiver.class),
            new PropertyDescriptor("password", PayloadProxyReceiver.class),
        };
    }

    @Override
    public String getReceiverName() {
        return "PayloadProxyReceiver";
    }

    @Override
    public String getReceiverDocumentation() {
        return "<html>The PayloadProxyReceiver has the following parameters:<br/>" + "<ul>"
                + "<li>host - host to connect to</li>"
                + "<li>guid - guid to connect to</li>"
                + "<li>password - password to connect with</li>"
                + "</ul>"
                + "</html>";
    }
}
