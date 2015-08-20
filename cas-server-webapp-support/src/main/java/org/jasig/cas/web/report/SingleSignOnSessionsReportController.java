/*
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.cas.web.report;

import org.apache.commons.collections4.Predicate;
import org.jasig.cas.CentralAuthenticationService;
import org.jasig.cas.authentication.Authentication;
import org.jasig.cas.authentication.principal.Principal;
import org.jasig.cas.ticket.Ticket;
import org.jasig.cas.ticket.TicketGrantingTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * SSO Report web controller that produces JSON data for the view.
 *
 * @author Misagh Moayyed
 * @author Dmitriy Kopylenko
 * @since 4.1
 */
@Controller("singleSignOnSessionsReportController")
@RequestMapping("/statistics/ssosessions")
public final class SingleSignOnSessionsReportController {

    private static final String VIEW_SSO_SESSIONS = "monitoring/viewSsoSessions";

    private enum SsoSessionReportOptions {
        ALL("all"),
        PROXIED("all"),
        DIRECT("direct");

        private final String type;

        /**
         * Instantiates a new Sso session report options.
         *
         * @param type the type
         */
        SsoSessionReportOptions(final String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return this.type;
        }
    }
    /**
     * The enum Sso session attribute keys.
     */
    private enum SsoSessionAttributeKeys {
        AUTHENTICATED_PRINCIPAL("authenticated_principal"),
        PRINCIPAL_ATTRIBUTES("principal_attributes"),
        AUTHENTICATION_DATE("authentication_date"),
        TICKET_GRANTING_TICKET("ticket_granting_ticket"),
        AUTHENTICATION_ATTRIBUTES("authentication_attributes"),
        PROXIED_BY("proxied_by"),
        AUTHENTICATED_SERVICES("authenticated_services"),
        IS_PROXIED("is_proxied"),
        NUMBER_OF_USES("number_of_uses");

        private final String attributeKey;

        /**
         * Instantiates a new Sso session attribute keys.
         *
         * @param attributeKey the attribute key
         */
        SsoSessionAttributeKeys(final String attributeKey) {
            this.attributeKey = attributeKey;
        }

        @Override
        public String toString() {
            return this.attributeKey;
        }
    }

    private static final String ROOT_REPORT_ACTIVE_SESSIONS_KEY = "activeSsoSessions";

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleSignOnSessionsReportController.class);


    @Autowired
    private CentralAuthenticationService centralAuthenticationService;

    @Value("${sso.sessions.include.tgt:false}")
    private boolean includeTicketGrantingTicketId;

    /**
     * Instantiates a new Single sign on sessions report resource.
     */
    public SingleSignOnSessionsReportController() {}

    /**
     * Gets sso sessions.
     *
     * @param option the option
     * @return the sso sessions
     */
    private Collection<Map<String, Object>> getActiveSsoSessions(final SsoSessionReportOptions option) {
        final Collection<Map<String, Object>> activeSessions = new ArrayList<>();

        for (final Ticket ticket : getNonExpiredTicketGrantingTickets()) {
            final TicketGrantingTicket tgt = (TicketGrantingTicket) ticket;

            if (option == SsoSessionReportOptions.DIRECT && tgt.getProxiedBy() != null) {
                continue;
            }

            final Authentication authentication = tgt.getAuthentication();
            final Principal principal = authentication.getPrincipal();

            final Map<String, Object> sso = new HashMap<>(SsoSessionAttributeKeys.values().length);
            sso.put(SsoSessionAttributeKeys.AUTHENTICATED_PRINCIPAL.toString(), principal.getId());
            sso.put(SsoSessionAttributeKeys.AUTHENTICATION_DATE.toString(), authentication.getAuthenticationDate());
            sso.put(SsoSessionAttributeKeys.NUMBER_OF_USES.toString(), tgt.getCountOfUses());
            if (this.includeTicketGrantingTicketId) {
                sso.put(SsoSessionAttributeKeys.TICKET_GRANTING_TICKET.toString(), tgt.getId());
            }
            sso.put(SsoSessionAttributeKeys.PRINCIPAL_ATTRIBUTES.toString(), principal.getAttributes());
            sso.put(SsoSessionAttributeKeys.AUTHENTICATION_ATTRIBUTES.toString(), authentication.getAttributes());

            if (option != SsoSessionReportOptions.DIRECT) {
                if (tgt.getProxiedBy() != null) {
                    sso.put(SsoSessionAttributeKeys.IS_PROXIED.toString(), true);
                    sso.put(SsoSessionAttributeKeys.PROXIED_BY.toString(), tgt.getProxiedBy().getId());
                } else {
                    sso.put(SsoSessionAttributeKeys.IS_PROXIED.toString(), false);
                }
            }

            sso.put(SsoSessionAttributeKeys.AUTHENTICATED_SERVICES.toString(), tgt.getServices());

            activeSessions.add(Collections.unmodifiableMap(sso));
        }
        return Collections.unmodifiableCollection(activeSessions);
    }

    /**
     * Gets non expired ticket granting tickets.
     *
     * @return the non expired ticket granting tickets
     */
    private Collection<Ticket> getNonExpiredTicketGrantingTickets() {
        return this.centralAuthenticationService.getTickets(new Predicate() {
            @Override
            public boolean evaluate(final Object ticket) {
                if (ticket instanceof TicketGrantingTicket) {
                    return !((TicketGrantingTicket) ticket).isExpired();
                }
                return false;
            }
        });
    }

    /**
     * Endpoint for getting SSO Sessions in JSON format
     *
     * @param type the type
     * @return the sso sessions
     */
    @RequestMapping(value = "/getSsoSessions", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getSsoSessions(@RequestParam(defaultValue = "ALL") final String type) {
        final Map<String, Object> sessionsMap = new HashMap<>(1);
        final SsoSessionReportOptions option = SsoSessionReportOptions.valueOf(type);
        sessionsMap.put(ROOT_REPORT_ACTIVE_SESSIONS_KEY, getActiveSsoSessions(option));
        return sessionsMap;
    }

    /**
     * Show sso sessions.
     *
     * @return the model and view where json data will be rendered
     * @throws Exception thrown during json processing
     */
    @RequestMapping(method = RequestMethod.GET)
    public ModelAndView showSsoSessions() throws Exception {
        final ModelAndView modelAndView = new ModelAndView(VIEW_SSO_SESSIONS);
        return modelAndView;
    }
}
