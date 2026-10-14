import React from 'react';
import { renderToString } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import App from './App';

export function renderHome() {
  return renderToString(<App Router={MemoryRouter} />);
}
