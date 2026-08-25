import { createContext, useContext } from 'react';

export const MemberSessionContext = createContext(null);

export const useMemberSession = () => {
  const context = useContext(MemberSessionContext);
  if (!context) {
    throw new Error('useMemberSession must be used inside MemberSessionProvider');
  }
  return context;
};
