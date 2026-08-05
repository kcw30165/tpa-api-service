package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordDetailsRequest(String dateOfBirth, String email, String mobile) {
	@Override
	public String toString() {
		return "ForgotPasswordDetailsRequest[dateOfBirth=<redacted>, email=<redacted>, mobile=<redacted>]";
	}
}
