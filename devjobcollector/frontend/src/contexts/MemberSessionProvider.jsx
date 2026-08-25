import React, { useCallback, useMemo, useState } from 'react';
import { getCurrentMember } from '../api/memberApi';
import { MemberSessionContext } from './memberSession';

const MemberSessionProvider = ({ children }) => {
  const [member, setMember] = useState(null);
  const [status, setStatus] = useState('idle');

  const loadMember = useCallback(async () => {
    if (status === 'loading' || status === 'success') {
      return;
    }
    setStatus('loading');
    try {
      const currentMember = await getCurrentMember();
      setMember(currentMember);
      setStatus('success');
    } catch {
      setStatus('error');
    }
  }, [status]);

  const value = useMemo(() => ({ member, status, loadMember }), [member, status, loadMember]);

  return <MemberSessionContext.Provider value={value}>{children}</MemberSessionContext.Provider>;
};

export default MemberSessionProvider;
