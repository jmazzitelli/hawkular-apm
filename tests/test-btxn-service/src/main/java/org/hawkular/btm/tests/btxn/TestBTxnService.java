/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.btm.tests.btxn;

import static io.undertow.Handlers.path;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.hawkular.btm.api.model.btxn.BusinessTransaction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

/**
 * This class represents a test business transaction service.
 *
 * @author gbrown
 */
public class TestBTxnService {

    /**  */
    private static final String HAWKULAR_BTM_TEST_BTXNSERVICE_HOST = "hawkular-btm.test.btxnservice.host";

    /**  */
    private static final String HAWKULAR_BTM_TEST_BTXNSERVICE_PORT = "hawkular-btm.test.btxnservice.port";

    /**  */
    private static final String HAWKULAR_BTM_TEST_BTXNSERVICE_SHUTDOWN = "hawkular-btm.test.btxnservice.shutdown";

    /**  */
    private static final int DEFAULT_SHUTDOWN_TIMER = 30000;

    private static final Logger log = Logger.getLogger(TestBTxnService.class.getName());

    private Undertow server = null;

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final TypeReference<java.util.List<BusinessTransaction>> BUSINESS_TXN_LIST =
            new TypeReference<java.util.List<BusinessTransaction>>() {
    };

    private List<BusinessTransaction> businessTransactions = new ArrayList<BusinessTransaction>();

    private int port=8080;
    private String host="localhost";
    private int shutdown=DEFAULT_SHUTDOWN_TIMER;

    {
        if (System.getProperties().containsKey(HAWKULAR_BTM_TEST_BTXNSERVICE_HOST)) {
            host = System.getProperty(HAWKULAR_BTM_TEST_BTXNSERVICE_HOST);
        }
        if (System.getProperties().containsKey(HAWKULAR_BTM_TEST_BTXNSERVICE_PORT)) {
            port = Integer.parseInt(System.getProperty(HAWKULAR_BTM_TEST_BTXNSERVICE_PORT));
        }
        if (System.getProperties().containsKey(HAWKULAR_BTM_TEST_BTXNSERVICE_SHUTDOWN)) {
            shutdown = Integer.parseInt(System.getProperty(HAWKULAR_BTM_TEST_BTXNSERVICE_SHUTDOWN));
        }
    }

    /**
     * Main for the test app.
     *
     * @param args The arguments
     */
    public static void main(String[] args) {
        TestBTxnService main = new TestBTxnService();
        main.run();
    }

    public void run() {
        log.info("************** STARTED TEST BTXN SERVICE: host="+host+" port="+port);
        // Create shutdown thread, just in case hangs
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    try {
                        wait(shutdown);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                log.severe("************** ABORTING TEST BTXN SERVICE");
                System.exit(1);
            }
        });
        t.setDaemon(true);
        t.start();

        server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(path().addPrefixPath("hawkular/btm/shutdown", new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        log.info("Shutdown called");

                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                        exchange.getResponseSender().send("ok");
                        shutdown();
                    }
                }).addPrefixPath("hawkular/btm/transactions", new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        if (exchange.isInIoThread()) {
                            exchange.dispatch(this);
                            return;
                        }

                        log.info("Transactions request received: " + exchange);

                        if (exchange.getRequestMethod() == Methods.POST) {
                            exchange.startBlocking();

                            java.io.InputStream is = exchange.getInputStream();
                            byte[] b = new byte[is.available()];
                            is.read(b);
                            is.close();

                            List<BusinessTransaction> btxns = mapper.readValue(new String(b), BUSINESS_TXN_LIST);

                            businessTransactions.addAll(btxns);

                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("");
                        } else if (exchange.getRequestMethod() == Methods.GET) {
                            // TODO: Currently returns all - support proper query
                            String btxns = mapper.writeValueAsString(businessTransactions);
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                            exchange.getResponseSender().send(btxns);
                        }
                    }
                })).build();

        server.start();
    }

    public void shutdown() {
        log.info("************ TEST BTXN SERVICE EXITING");
        server.stop();

        System.exit(0);
    }
}
