import React, { useCallback, useMemo, useRef, useState } from 'react';
import { getCurrentMember } from '../api/memberApi';
import { MemberSessionContext } from './memberSession';

const MemberSessionProvider = ({ children }) => {
  const [member, setMember] = useState(null);
  const [status, setStatus] = useState('idle');
  const inFlight = useRef(false);

  const loadMember = useCallback(async () => {
    if (inFlight.current || status === 'success') {
      return;
    }
    inFlight.current = true;
    setStatus('loading');
    try {
      const currentMember = await getCurrentMember();
      setMember(currentMember);
      setStatus('success');
    } catch {
      setStatus('error');
    } finally {
      inFlight.current = false;
    }
  }, [status]);

  const value = useMemo(() => ({ member, status, loadMember }), [member, status, loadMember]);

  return <MemberSessionContext.Provider value={value}>{children}</MemberSessionContext.Provider>;
};

export default MemberSessionProvider;
