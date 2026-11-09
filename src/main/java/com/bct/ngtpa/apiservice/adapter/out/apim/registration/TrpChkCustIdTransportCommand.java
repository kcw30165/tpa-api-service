package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

/** Feature-neutral typed transport command for TRPChkCustId. */
public record TrpChkCustIdTransportCommand(
    TrpChkCustIdApimRequest request,
    ActorUserRole actorUserRole,
    String correlationId,
    ApimIncidentOwnership incidentOwnership) {
  public TrpChkCustIdTransportCommand {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    Objects.requireNonNull(incidentOwnership, "incidentOwnership must not be null");
  }
}
