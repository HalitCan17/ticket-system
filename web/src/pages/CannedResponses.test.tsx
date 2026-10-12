import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { CannedResponse } from '../api/types.ts';
import { ApiError } from '../api/client.ts';
import { CommentForm } from '../components/CommentForm.tsx';
import { aTicket } from '../test/fixtures.ts';
import { fakeApi, fakeCannedResponsesApi, renderApp } from '../test/renderApp.tsx';

function aTemplate(overrides: Partial<CannedResponse> = {}): CannedResponse {
  return {
    id: 1,
    title: 'Ek bilgi isteği',
    body: 'Merhaba {müşteri}, {talep} için ekran görüntüsü paylaşır mısınız? {ben}',
    visibility: 'EXTERNAL',
    updatedBy: 'manager-1',
    updatedAt: '2026-10-12T10:00:00Z',
    ...overrides,
  };
}

const values = { customer: 'Customer One', ticketId: 7, me: 'Agent One' };

describe('canned responses in the comment form', () => {
  it('inserts the filled template and switches to its suggested visibility', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <CommentForm staff pending={false} onSubmit={onSubmit} templates={[aTemplate()]} placeholders={values} />,
    );
    expect(screen.getByRole('radio', { name: 'Dahili (yalnızca ekip)' })).toBeChecked();

    await userEvent.selectOptions(screen.getByLabelText('Hazır yanıt'), '1');

    expect(screen.getByLabelText('Yorum')).toHaveValue(
      'Merhaba Customer One, #7 için ekran görüntüsü paylaşır mısınız? Agent One',
    );
    expect(screen.getByRole('radio', { name: 'Müşteriye açık' })).toBeChecked();
    expect(screen.getByLabelText('Hazır yanıt')).toHaveValue(''); // ready to pick another one
    expect(onSubmit).not.toHaveBeenCalled(); // inserting never sends
  });

  it('appends below text that is already written', async () => {
    render(
      <CommentForm
        staff
        pending={false}
        onSubmit={vi.fn()}
        templates={[aTemplate({ id: 2, body: 'İkinci satır', visibility: 'INTERNAL' })]}
        placeholders={values}
      />,
    );
    await userEvent.type(screen.getByLabelText('Yorum'), 'İlk satır  ');
    await userEvent.selectOptions(screen.getByLabelText('Hazır yanıt'), '2');

    expect(screen.getByLabelText('Yorum')).toHaveValue('İlk satır\n\nİkinci satır');
    expect(screen.getByRole('radio', { name: 'Dahili (yalnızca ekip)' })).toBeChecked();
  });

  it('is not offered to customers or without templates', () => {
    const { rerender } = render(
      <CommentForm staff={false} pending={false} onSubmit={vi.fn()} templates={[aTemplate()]} />,
    );
    expect(screen.queryByLabelText('Hazır yanıt')).not.toBeInTheDocument();
    rerender(<CommentForm staff pending={false} onSubmit={vi.fn()} templates={[]} />);
    expect(screen.queryByLabelText('Hazır yanıt')).not.toBeInTheDocument();
  });

  it('the ticket page offers templates to staff with the ticket filled in', async () => {
    const canned = fakeCannedResponsesApi({ list: vi.fn().mockResolvedValue([aTemplate()]) });
    renderApp('/tickets/7', {
      role: 'AGENT',
      api: fakeApi({ get: vi.fn().mockResolvedValue(aTicket({ id: 7 })) }),
      canned,
    });

    const select = await screen.findByLabelText('Hazır yanıt');
    await within(select).findByRole('option', { name: 'Ek bilgi isteği' });
    await userEvent.selectOptions(select, '1');
    expect(screen.getByLabelText('Yorum')).toHaveValue(
      'Merhaba Customer One, #7 için ekran görüntüsü paylaşır mısınız? Agent One',
    );
  });

  it('the ticket page does not load templates for customers', async () => {
    const canned = fakeCannedResponsesApi();
    renderApp('/tickets/7', { role: 'CUSTOMER', api: fakeApi({ get: vi.fn().mockResolvedValue(aTicket({ id: 7 })) }), canned });

    await screen.findByRole('heading', { name: /VPN/ });
    expect(canned.list).not.toHaveBeenCalled();
  });
});

describe('CannedResponsesPage', () => {
  it('is linked in the menu for managers only', async () => {
    renderApp('/tickets', { role: 'MANAGER', api: fakeApi({ list: vi.fn().mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) }) });
    expect(await screen.findByRole('link', { name: 'Hazır yanıtlar' })).toHaveAttribute('href', '/canned-responses');
  });

  it('tells agents the page is for managers', async () => {
    const canned = fakeCannedResponsesApi();
    renderApp('/canned-responses', { role: 'AGENT', canned });

    expect(await screen.findByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Hazır yanıtlar' })).not.toBeInTheDocument();
    expect(canned.list).not.toHaveBeenCalled();
  });

  it('lets a manager create, edit and delete templates', async () => {
    const templates = [aTemplate()];
    const canned = fakeCannedResponsesApi({
      list: vi.fn().mockImplementation(() => Promise.resolve([...templates])),
      create: vi.fn().mockImplementation((request) => {
        const created = aTemplate({ id: 9, ...request });
        templates.push(created);
        return Promise.resolve(created);
      }),
      update: vi.fn().mockResolvedValue(aTemplate()),
      remove: vi.fn().mockResolvedValue(undefined),
    });
    renderApp('/canned-responses', { role: 'MANAGER', canned });
    await screen.findByText('Ek bilgi isteği');

    await userEvent.click(screen.getByRole('button', { name: '+ Yeni hazır yanıt' }));
    const form = screen.getByRole('form', { name: 'Yeni hazır yanıt' });
    await userEvent.type(within(form).getByLabelText('Başlık'), ' Şifre sıfırlama ');
    await userEvent.type(within(form).getByLabelText('Metin'), 'Şifreniz sıfırlandı.');
    await userEvent.click(within(form).getByRole('radio', { name: 'Dahili (yalnızca ekip)' }));
    await userEvent.click(within(form).getByRole('button', { name: 'Kaydet' }));

    expect(canned.create).toHaveBeenCalledWith({ title: 'Şifre sıfırlama', body: 'Şifreniz sıfırlandı.', visibility: 'INTERNAL' });
    expect(await screen.findByText('Şifre sıfırlama')).toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();

    const first = screen.getByText('Ek bilgi isteği').closest('li')!;
    await userEvent.click(within(first).getByRole('button', { name: 'Düzenle' }));
    const edit = screen.getByRole('form', { name: 'Hazır yanıtı düzenle' });
    expect(within(edit).getByLabelText('Başlık')).toHaveValue('Ek bilgi isteği');
    await userEvent.clear(within(edit).getByLabelText('Metin'));
    await userEvent.type(within(edit).getByLabelText('Metin'), 'Yeni metin');
    await userEvent.click(within(edit).getByRole('button', { name: 'Kaydet' }));
    expect(canned.update).toHaveBeenCalledWith(1, { title: 'Ek bilgi isteği', body: 'Yeni metin', visibility: 'EXTERNAL' });

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    await userEvent.click(screen.getByRole('button', { name: 'Ek bilgi isteği sil' }));
    expect(canned.remove).toHaveBeenCalledWith(1);
    confirm.mockRestore();
  });

  it('shows a duplicate title error', async () => {
    const canned = fakeCannedResponsesApi({
      create: vi.fn().mockRejectedValue(
        new ApiError({ status: 409, code: 'TITLE_TAKEN', detail: 'A canned response with this title already exists.' }),
      ),
    });
    renderApp('/canned-responses', { role: 'MANAGER', canned });
    await screen.findByText('Henüz hazır yanıt yok.');

    await userEvent.click(screen.getByRole('button', { name: '+ Yeni hazır yanıt' }));
    await userEvent.type(screen.getByLabelText('Başlık'), 'Aynı');
    await userEvent.type(screen.getByLabelText('Metin'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Kaydet' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('form')).toBeInTheDocument(); // the form stays open with the input
  });
});
