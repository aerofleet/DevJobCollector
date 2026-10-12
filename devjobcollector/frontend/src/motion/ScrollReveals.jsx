import { useEffect } from 'react';
import { observeReveals } from './reveal';

export default function ScrollReveals() {
  useEffect(observeReveals, []);
  return null;
}
