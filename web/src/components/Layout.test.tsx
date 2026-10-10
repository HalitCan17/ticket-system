import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { fakeApi, renderApp } from '../test/renderApp.tsx';

describe('Layout', () => {
  it('shows the signed-in user and role and logs out', async () => {
    const api = fakeApi({ list: async () => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) });
    const { auth } = renderApp('/tickets', { role: 'AGENT', api });

    const header = screen.getByRole('banner');
    expect(within(header).getByText(/Agent One/)).toBeInTheDocument();
    expect(within(header).getByText(/Destek uzmanı/)).toBeInTheDocument();

    await userEvent.click(within(header).getByRole('button', { name: 'Çıkış' }));
    expect(auth.logout).toHaveBeenCalled();
  });

  it('redirects / to the ticket list and shows 404 for unknown paths', async () => {
    const api = fakeApi({ list: async () => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) });
    const { router } = renderApp('/', { api });
    expect(router.state.location.pathname).toBe('/tickets');

    renderApp('/does-not-exist');
    expect(await screen.findByRole('heading', { name: 'Sayfa bulunamadı' })).toBeInTheDocument();
  });
});
