import React, { useCallback, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { getCurrentMember } from '../api/memberApi';
import { MemberSessionContext } from './memberSession';
import { getAccessToken, subscribeAccessToken } from '../utils/accessToken';

const MemberSessionProvider = ({ children }) => {
  const token = useSyncExternalStore(subscribeAccessToken, getAccessToken, () => null);
  const [session, setSession] = useState(null);
  const member = token && session?.token === token ? session.member : null;
  const status = token && session?.token === token ? session.status : 'idle';
  const inFlight = useRef(null);

  const loadMember = useCallback(async () => {
    if (!token || inFlight.current === token || status === 'success') {
      return;
    }
    inFlight.current = token;
    setSession({ token, member: null, status: 'loading' });
    try {
      const currentMember = await getCurrentMember();
      if (getAccessToken() === token) setSession({ token, member: currentMember, status: 'success' });
    } catch {
      if (getAccessToken() === token) setSession({ token, member: null, status: 'error' });
    } finally {
      if (inFlight.current === token) inFlight.current = null;
    }
  }, [status, token]);

  const value = useMemo(() => ({ token, member, status, loadMember }), [token, member, status, loadMember]);

  return <MemberSessionContext.Provider value={value}>{children}</MemberSessionContext.Provider>;
};

export default MemberSessionProvider;
