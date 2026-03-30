package kr.itsdev.auth.common.spi;

import kr.itsdev.auth.common.model.AuthenticatedUser;

public interface TokenIssueService {
    String issueAccessToken(AuthenticatedUser user);
}
