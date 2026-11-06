package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import org.springframework.web.server.ServerWebExchange;

/**
 * Request-local web observation that a completed technical response already has a terminal owner.
 */
public final class TerminalIncidentHttpObservation {
  static final String ATTRIBUTE_KEY = TerminalIncidentHttpObservation.class.getName();
  private static final TerminalIncidentHttpObservation OWNED =
      new TerminalIncidentHttpObservation();

  private TerminalIncidentHttpObservation() {}

  public static void markOwned(ServerWebExchange exchange) {
    exchange.getAttributes().put(ATTRIBUTE_KEY, OWNED);
  }

  public static boolean isOwned(ServerWebExchange exchange) {
    return exchange.getAttribute(ATTRIBUTE_KEY) == OWNED;
  }
}
