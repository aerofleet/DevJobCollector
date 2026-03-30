package kr.itsdev.auth.common.spi;

import kr.itsdev.auth.common.model.AuthenticatedUser;
import kr.itsdev.auth.common.model.SocialProfile;

public interface SocialUserUpsertService {
    AuthenticatedUser upsert(SocialProfile profile);
}
