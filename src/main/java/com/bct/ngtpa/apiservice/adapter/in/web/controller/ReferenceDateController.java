package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.request.RefreshReferenceDateRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.CleanUpAllReferenceDatesResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.GetAllReferenceDatesResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ReferenceDateEntryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RefreshReferenceDateResponse;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.port.in.CleanUpAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/internal/reference-date")
@RequiredArgsConstructor
public class ReferenceDateController {
    private final RefreshReferenceDateUseCase refreshReferenceDateUseCase;
    private final GetAllReferenceDatesUseCase getAllReferenceDatesUseCase;
    private final CleanUpAllReferenceDatesUseCase cleanUpAllReferenceDatesUseCase;

    @PostMapping(value = {"/refresh"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RefreshReferenceDateResponse> refreshReferenceDate(@Valid @RequestBody RefreshReferenceDateRequest request) {
        return refreshReferenceDateUseCase.execute(new RefreshReferenceDateCommand(request.accountEnv()))
                .map(result -> new RefreshReferenceDateResponse(
                        result.accountEnv(),
                        result.refDate(),
                        result.redisUpdated()));
    }

    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<MutationResponse<GetAllReferenceDatesResponse>> getAllReferenceDates() {
        return getAllReferenceDatesUseCase.execute()
                .map(result -> MutationResponse.success(
                        ApiStatus.SUCCESS,
                        new GetAllReferenceDatesResponse(result.referenceDates().stream()
                                .map(entry -> new ReferenceDateEntryResponse(entry.accountEnv(), entry.refDate()))
                                .toList()),
                        List.of()));
    }

    @DeleteMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<MutationResponse<CleanUpAllReferenceDatesResponse>> cleanUpAllReferenceDates() {
        return cleanUpAllReferenceDatesUseCase.execute()
                .map(result -> MutationResponse.success(
                        ApiStatus.UPDATED,
                        new CleanUpAllReferenceDatesResponse(result.deletedCount()),
                        List.of()));
    }
}
