import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import './theme.css';
import { applyStoredTheme } from './theme.js';
import keycloak from './keycloak.js';
import { isKeycloak } from './auth/config.js';

applyStoredTheme();

function render() {
  createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </React.StrictMode>
  );
}

if (isKeycloak) {
  // SSO: redirect to Keycloak before the app renders.
  keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256' }).then(render);
} else {
  // native + none render immediately; native guards its own routes in App.
  render();
}
