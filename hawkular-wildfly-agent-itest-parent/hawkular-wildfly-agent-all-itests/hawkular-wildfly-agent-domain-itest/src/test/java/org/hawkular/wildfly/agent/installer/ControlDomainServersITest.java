/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.wildfly.agent.installer;

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.dmrclient.Address;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.wildfly.agent.itest.util.AbstractITest;
import org.hawkular.wildfly.agent.itest.util.WildFlyClientConfig;
import org.jboss.as.controller.client.ModelControllerClient;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ControlDomainServersITest extends AbstractITest {
    public static final String GROUP = "ControlDomainServersITest";

    @Test(groups = { GROUP })
    public void testStopIndividualServer() throws Throwable {
        waitForAccountsAndInventory();

        final String serverToTest = "server-one";

        WildFlyClientConfig clientConfig = getPlainWildFlyClientConfig();

        // sanity check - the server-one server should be running
        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(clientConfig)) {
            assertNodeAttributeEquals(mcc,
                    Address.parse("/host=master/server=" + serverToTest).getAddressNode(),
                    "server-state",
                    "running");
        }

        CanonicalPath wfPath = getHostController(clientConfig);
        Resource agent = getResource(
                "/traversal/f;" + clientConfig.getFeedId() + "/type=rt;"
                + "id=Domain WildFly Server Controller/rl;defines/type=r",
                (r -> r.getId().contains(serverToTest)));

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", "
                + "\"resourcePath\":\"" + agent.getPath().toString() + "\","
                + "\"operationName\":\"Stop\""
                + "}";
        String response = "ExecuteOperationResponse={"
                + "\"operationName\":\"Stop\","
                + "\"resourcePath\":\"" + agent.getPath() + "\","
                + "\"destinationSessionId\":\"{{sessionId}}\","
                + "\"status\":\"OK\","
                + "\"message\":\"Performed [Stop] on a [DMR Node] given by Inventory path ["
                + agent.getPath() + "]\""
                + "}";
        try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                .url(baseGwUri + "/ui/ws")
                .expectWelcome(req)
                .expectGenericSuccess(wfPath.ids().getFeedId())
                .expectText(response, TestWebSocketClient.Answer.CLOSE)
                .expectClose()
                .build()) {
            testClient.validate(10000);
        }

        try (ModelControllerClient mcc = newPlainWildFlyModelControllerClient(clientConfig)) {
            String serverState = getNodeAttribute(mcc,
                    Address.parse("/host=master/server=" + serverToTest).getAddressNode(), "server-state");
            if (!serverState.toLowerCase().startsWith("stop")) { // stopped or stopping
                Assert.fail("Expected server state to be stopping or stopped, but was: " + serverState);
            }
        }
    }
}
