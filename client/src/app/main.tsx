import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import App from './App';
import { GlobalProviders } from './providers';
import '../index.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <BrowserRouter>
      <GlobalProviders>
        <App />
      </GlobalProviders>
    </BrowserRouter>
  </React.StrictMode>,
);
