package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordIdentityCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityStatus;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;

import java.util.List;
import java.util.Map;

public class ForgotPasswordIdentityWebMapper {
	public VerifyForgotPasswordIdentityCommand command(
			ForgotPasswordIdentityCheckRequest request,
			String sessionId,
			String csrfToken,
			String origin,
			String referer,
			String requestId) {
		return new VerifyForgotPasswordIdentityCommand(
				new AuthFlowSessionId(sessionId),
				csrfToken,
				origin,
				referer,
				requestId,
				request == null ? null : request.identityType(),
				request == null ? null : request.identityValue());
	}

	@LogExecution(value = "adapter.in.web.auth.forgotpassword.mapper", logArgs = true)
	public MutationResponse<ForgotPasswordWorkflowResultResponse<Map<String, Object>>> response(
			VerifyForgotPasswordIdentityResult result) {
		if (result.status() == VerifyForgotPasswordIdentityStatus.VERIFIED) {
			return MutationResponse.success(
					ApiStatus.SUCCESS,
					new ForgotPasswordWorkflowResultResponse<>(WorkflowNextStep.ENTER_DETAILS, Map.of()),
					List.of());
		}
		if (result.status() == VerifyForgotPasswordIdentityStatus.INVALID_TYPE) {
			return MutationResponse.failure(
					ApiStatus.VALIDATION_FAILED,
					null,
					List.of(ApiError.form("err.forgot-password.incorrect.idType", null, List.of("identityType"),
							"BFF")));
		}
		if (result.status() == VerifyForgotPasswordIdentityStatus.INCORRECT_ID) {
			return MutationResponse.failure(
					ApiStatus.BUSINESS_REJECTED,
					null,
					List.of(ApiError.form("err.forgot-password.incorrect.id", null, List.of("identityValue"), "BFF")));
		}
		return MutationResponse.failure(
				ApiStatus.SYSTEM_ERROR,
				null,
				List.of(ApiError.form("err.forgot-password.system.unavailable", null, List.of(), "BFF")));
	}
}
