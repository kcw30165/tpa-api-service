package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper.TrpSearchRegedMemberMapper;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
@Component public final class ApimForgotPasswordDetailsLookupAdapter implements ForgotPasswordDetailsLookupPort {
    private final TrpSearchRegedMemberTransport transport; private final TrpSearchRegedMemberMapper mapper=new TrpSearchRegedMemberMapper();
    public ApimForgotPasswordDetailsLookupAdapter(TrpSearchRegedMemberTransport transport){this.transport=Objects.requireNonNull(transport);}
    public Mono<ForgotPasswordDetailsLookupResult> verify(ForgotPasswordDetailsLookupCommand c){
        var request=mapper.toRequest(new ForgotPasswordDetailsCommand(null,null,null,null,c.requestId(),c.dateOfBirth(),c.email(),c.mobile(),null),c.searchBy(),c.searchValue(),c.customerId());
        return transport.post(request,c.requestId()).map(e->mapper.toResult(e.errMessage(),e.data(),
                new ForgotPasswordDetailsCommand(null,null,null,null,c.requestId(),c.dateOfBirth(),c.email(),c.mobile(),null)));
    }
}
