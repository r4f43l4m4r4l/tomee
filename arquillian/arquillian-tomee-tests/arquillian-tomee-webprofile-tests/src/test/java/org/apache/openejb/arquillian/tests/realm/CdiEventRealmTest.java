/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.arquillian.tests.realm;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.openejb.jee.WebApp;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Module;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomee.catalina.realm.CdiEventRealm;
import org.apache.tomee.catalina.realm.event.DigestAuthenticationEvent;
import org.apache.tomee.catalina.realm.event.FindSecurityConstraintsEvent;
import org.apache.tomee.catalina.realm.event.GssAuthenticationEvent;
import org.apache.tomee.catalina.realm.event.HasResourcePermissionEvent;
import org.apache.tomee.catalina.realm.event.HasRoleEvent;
import org.apache.tomee.catalina.realm.event.HasUserDataPermissionEvent;
import org.apache.tomee.catalina.realm.event.SslAuthenticationEvent;
import org.apache.tomee.catalina.realm.event.UserPasswordAuthenticationEvent;
import org.ietf.jgss.GSSContext;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.event.Observes;
import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(ApplicationComposer.class)
public class CdiEventRealmTest {

    @Module
    @Classes(cdi = true, innerClassesAsBean = true)
    public WebApp app() {
        return new WebApp();
    }

    @Test
    public void userPassword() {
        final GenericPrincipal gp = getGenericPrincipal(new CdiEventRealm().authenticate("john", "secret"));
        assertEquals("john", gp.getName());
        assertEquals("", gp.getPassword());
        assertEquals(1, gp.getRoles().length);
        assertEquals("admin", gp.getRoles()[0]);
    }

    @Test
    public void digest() {
        final GenericPrincipal gp = getGenericPrincipal(new CdiEventRealm().authenticate("ryan", "md5", "nonce", "nc", "cnonce", "qop", "realm", "md5a2"));
        final String[] actual = gp.getRoles();
        final String[] expected = new String[] {"ryan", "md5", "nonce", "nc", "cnonce", "qop", "realm", "md5a2"};

        Arrays.sort(actual);
        Arrays.sort(expected);

        assertArrayEquals(actual, expected);
    }

    @Test
    public void gss() {
        final GenericPrincipal gp = getGenericPrincipal(new CdiEventRealm().authenticate(mock(GSSContext.class), false));
        assertEquals("gss", gp.getName());
        assertEquals("", gp.getPassword());
        assertEquals(1, gp.getRoles().length);
        assertEquals("dummy", gp.getRoles()[0]);
    }

    @Test
    public void ssl() {
        X509Certificate cert = mock(X509Certificate.class);
        GenericPrincipal expected = new GenericPrincipal("john", "doe", Arrays.asList("test"));
        when(cert.getSubjectDN()).thenReturn(expected);
        final GenericPrincipal gp = getGenericPrincipal(new CdiEventRealm().authenticate(new X509Certificate[] { cert }));
        assertEquals(expected, gp);
        assertEquals("john", gp.getName());
        assertEquals("doe", gp.getPassword());
        assertEquals(1, gp.getRoles().length);
        assertEquals("test", gp.getRoles()[0]);
    }

    @Test
    public void find() {
        final SecurityConstraint[] securityConstraints = new CdiEventRealm().findSecurityConstraints(mock(Request.class), mock(Context.class));
        assertEquals(1, securityConstraints.length);
        assertEquals("awesome", securityConstraints[0].getDisplayName());
    }

    @Test
    public void has() throws IOException {
        new CdiEventRealm().hasResourcePermission(mock(Request.class), mock(Response.class), new SecurityConstraint[0], mock(Context.class));
        new CdiEventRealm().hasRole(mock(Wrapper.class), mock(Principal.class), "admin");
        new CdiEventRealm().hasUserDataPermission(mock(Request.class), mock(Response.class), new SecurityConstraint[0]);

        assertEquals(1, MultiAuthenticator.hasResourcePermission.get());
        assertEquals(1, MultiAuthenticator.hasRole.get());
        assertEquals(1, MultiAuthenticator.hasUserDataPermission.get());
    }

    private GenericPrincipal getGenericPrincipal(Principal principal) {
        assertNotNull(principal);
        assertTrue(GenericPrincipal.class.isInstance(principal));
        return GenericPrincipal.class.cast(principal);
    }

    public static class MultiAuthenticator {

        public static final AtomicInteger hasRole = new AtomicInteger(0);
        public static final AtomicInteger hasResourcePermission = new AtomicInteger(0);
        public static final AtomicInteger hasUserDataPermission = new AtomicInteger(0);

        public void authenticate(@Observes final UserPasswordAuthenticationEvent event) {
            assertEquals("john", event.getUsername());
            assertEquals("secret", event.getCredential());
            event.setPrincipal(new GenericPrincipal(event.getUsername(), "", Arrays.asList("admin")));
        }

        public void authenticate(@Observes final DigestAuthenticationEvent event) {
            final List<String> roles = new ArrayList<>();
            roles.add(event.getCnonce());
            roles.add(event.getDigest());
            roles.add(event.getMd5a2());
            roles.add(event.getNc());
            roles.add(event.getNonce());
            roles.add(event.getQop());
            roles.add(event.getRealm());
            roles.add(event.getUsername());
            event.setPrincipal(new GenericPrincipal(event.getUsername(), "", roles));
        }

        public void authenticate(@Observes final GssAuthenticationEvent event) {
            assertNotNull(event.getGssContext());
            event.setPrincipal(new GenericPrincipal("gss", "", Arrays.asList("dummy")));
        }

        public void authenticate(@Observes final SslAuthenticationEvent event) {
            event.setPrincipal(event.getCerts()[0].getSubjectDN());
        }

        public void findSecurityConstraints(@Observes FindSecurityConstraintsEvent event) {
            SecurityConstraint mock = mock(SecurityConstraint.class);
            when(mock.getDisplayName()).thenReturn("awesome");
            event.addSecurityConstraint(mock);
        }

        public void hasResourcePermission(@Observes HasResourcePermissionEvent event) throws IOException {
            hasResourcePermission.incrementAndGet();
            event.setHasResourcePermission(true);
        }

        public void hasRole(@Observes final HasRoleEvent event) {
            hasRole.incrementAndGet();
            event.setHasRole(true);
        }

        public void hasUserDataPermission(@Observes final HasUserDataPermissionEvent event) throws IOException {
            hasUserDataPermission.incrementAndGet();
            event.setHasUserDataPermission(true);
        }

    }

}
