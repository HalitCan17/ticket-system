import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { AppProviders, routes } from './App.tsx';
import { AuthGate } from './auth/AuthGate.tsx';
import './styles/global.css';

const router = createBrowserRouter(routes);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthGate>
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>
    </AuthGate>
  </StrictMode>,
);
