import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client.ts';
import type { CatalogItem, ServiceRequest } from '../api/types.ts';
import { notificationText } from '../components/notificationText.ts';
import { fakeCatalogApi, renderApp } from '../test/renderApp.tsx';
import { keyFromLabel } from './CatalogManagePage.tsx';

function anItem(overrides: Partial<CatalogItem> = {}): CatalogItem {
  return {
    id: 3,
    name: 'Yeni dizüstü bilgisayar',
    description: 'Onaydan sonra hazırlanır.',
    category: 'HARDWARE',
    priority: 'MEDIUM',
    requiresApproval: true,
    active: true,
    fields: [
      { key: 'model', label: 'Model', type: 'SELECT', options: ['Standart', 'Geliştirici'], required: true },
      { key: 'reason', label: 'Gerekçe', type: 'TEXTAREA', options: [], required: true },
    ],
    updatedAt: '2026-10-13T09:00:00Z',
    ...overrides,
  };
}

function aRequest(overrides: Partial<ServiceRequest> = {}): ServiceRequest {
  const item = anItem();
  return {
    id: 12,
    itemId: item.id,
    itemName: item.name,
    requiresApproval: true,
    fields: item.fields,
    answers: { model: 'Standart', reason: 'Eskisi bozuldu' },
    status: 'PENDING_APPROVAL',
    requesterId: 'customer-1',
    requesterName: 'Customer One',
    approverId: null,
    approverName: null,
    decisionComment: null,
    decidedAt: null,
    ticketId: null,
    createdAt: '2026-10-13T09:30:00Z',
    canCancel: false,
    canDecide: false,
    ...overrides,
  };
}

const page = (items: ServiceRequest[]) => ({ items, page: 0, size: 50, totalElements: items.length, totalPages: 1 });

describe('catalog', () => {
  it('lists the items with an approval badge and no management link for customers', async () => {
    renderApp('/catalog', { role: 'CUSTOMER', catalog: fakeCatalogApi({ items: vi.fn().mockResolvedValue([anItem()]) }) });

    const list = await screen.findByRole('list', { name: 'Katalog öğeleri' });
    expect(within(list).getByRole('link', { name: 'Yeni dizüstü bilgisayar' })).toHaveAttribute('href', '/catalog/3');
    expect(within(list).getByText('Onay gerekir')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Kataloğu yönet' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Hizmet kataloğu' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^Onaylar/ })).not.toBeInTheDocument();
  });

  it('builds the form from the fields, sends the answers and shows field errors from the server', async () => {
    const create = vi
      .fn()
      .mockRejectedValueOnce(
        new ApiError({
          status: 400,
          code: 'INVALID_ANSWERS',
          detail: 'Some answers are missing or not valid.',
          errors: [{ field: 'answers.reason', message: 'is required' }],
        }),
      )
      .mockResolvedValue(aRequest());
    const catalog = fakeCatalogApi({
      item: vi.fn().mockResolvedValue(anItem()),
      create,
      request: vi.fn().mockResolvedValue(aRequest()),
    });
    renderApp('/catalog/3', { role: 'CUSTOMER', catalog });

    const form = await screen.findByRole('form', { name: 'Hizmet talebi' });
    await userEvent.selectOptions(within(form).getByLabelText('Model *'), 'Standart');
    await userEvent.type(within(form).getByLabelText('Gerekçe *'), 'x');
    await userEvent.click(within(form).getByRole('button', { name: 'İste' }));
    expect(await within(form).findByText('is required')).toBeInTheDocument();

    await userEvent.click(within(form).getByRole('button', { name: 'İste' }));
    expect(create).toHaveBeenLastCalledWith(3, { model: 'Standart', reason: 'x' });
    expect(await screen.findByRole('heading', { name: 'Hizmet talebi #12: Yeni dizüstü bilgisayar' })).toBeInTheDocument();
  });
});

describe('service requests', () => {
  it('shows answers with labels and lets the requester withdraw a pending request', async () => {
    const cancel = vi.fn().mockResolvedValue(aRequest({ status: 'CANCELLED', canCancel: false }));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const catalog = fakeCatalogApi({ request: vi.fn().mockResolvedValue(aRequest({ canCancel: true })), cancel });
    renderApp('/requests/12', { role: 'CUSTOMER', catalog });

    expect(await screen.findByText('Eskisi bozuldu')).toBeInTheDocument();
    expect(screen.getByText('Gerekçe')).toBeInTheDocument();
    expect(screen.getByText('Onay bekliyor')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Geri çek' }));
    expect(cancel).toHaveBeenCalledWith(12);
    expect(await screen.findByText('Geri çekildi')).toBeInTheDocument();
  });

  it('links the ticket of a fulfilled request and shows the decision', async () => {
    const done = aRequest({
      status: 'FULFILLING',
      ticketId: 88,
      approverName: 'Manager One',
      decisionComment: 'Bütçe uygun',
      decidedAt: '2026-10-13T10:00:00Z',
    });
    renderApp('/requests/12', { role: 'CUSTOMER', catalog: fakeCatalogApi({ request: vi.fn().mockResolvedValue(done) }) });

    expect(await screen.findByRole('link', { name: '#88 numaralı talepte' })).toHaveAttribute('href', '/tickets/88');
    expect(screen.getByText('Bütçe uygun')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Geri çek' })).not.toBeInTheDocument();
  });

  it('lists my requests; staff can switch to everybody', async () => {
    const requests = vi.fn().mockResolvedValue(page([aRequest()]));
    renderApp('/requests', { role: 'AGENT', catalog: fakeCatalogApi({ requests }) });

    expect(await screen.findByRole('link', { name: '#12 Yeni dizüstü bilgisayar' })).toBeInTheDocument();
    expect(requests).toHaveBeenLastCalledWith({ mine: true, size: 50 });
    await userEvent.click(screen.getByRole('button', { name: 'Herkesin' }));
    await vi.waitFor(() => expect(requests).toHaveBeenLastCalledWith({ mine: false, size: 50 }));
  });
});

describe('approvals', () => {
  it('lets a manager approve, and requires a reason to reject', async () => {
    const decide = vi.fn().mockResolvedValue(aRequest({ status: 'FULFILLING' }));
    const catalog = fakeCatalogApi({
      requests: vi.fn().mockResolvedValue(page([aRequest({ canDecide: true })])),
      decide,
      pendingCount: vi.fn().mockResolvedValue({ count: 1 }),
    });
    renderApp('/approvals', { role: 'MANAGER', catalog });

    expect(await screen.findByRole('link', { name: 'Onaylar, 1 bekleyen' })).toBeInTheDocument();
    const form = await screen.findByRole('form', { name: 'Karar #12' });
    expect(within(form).getByRole('button', { name: 'Reddet' })).toBeDisabled();
    await userEvent.type(within(form).getByLabelText('Not (reddederken zorunlu)'), 'Bütçe yok');
    await userEvent.click(within(form).getByRole('button', { name: 'Reddet' }));
    expect(decide).toHaveBeenCalledWith(12, false, 'Bütçe yok');
  });

  it('is only for managers', async () => {
    renderApp('/approvals', { role: 'AGENT' });
    expect(await screen.findByText('Bu sayfa yalnızca yöneticiler içindir.')).toBeInTheDocument();
  });
});

describe('catalog management', () => {
  it('makes field keys from labels', () => {
    expect(keyFromLabel('Kullanıcı sayısı', [])).toBe('kullaniciSayisi');
    expect(keyFromLabel('Model', ['model'])).toBe('model2');
    expect(keyFromLabel('123', [])).toBe('alan123');
  });

  it('creates an item with a choice field', async () => {
    const createItem = vi.fn().mockResolvedValue(anItem({ id: 9 }));
    renderApp('/catalog/manage', { role: 'MANAGER', catalog: fakeCatalogApi({ createItem }) });

    await userEvent.click(await screen.findByRole('button', { name: '+ Yeni öğe' }));
    const form = screen.getByRole('form', { name: 'Yeni öğe' });
    await userEvent.type(within(form).getByLabelText('Ad'), 'Monitör');
    await userEvent.type(within(form).getByLabelText('Açıklama'), 'İkinci ekran');
    await userEvent.click(within(form).getByRole('checkbox', { name: 'Yönetici onayı gerekir' }));
    await userEvent.click(within(form).getByRole('button', { name: '+ Alan ekle' }));
    const field = within(form).getByRole('group', { name: 'Alan 1' });
    await userEvent.type(within(field).getByLabelText('Etiket'), 'Ekran boyutu');
    await userEvent.selectOptions(within(field).getByLabelText('Tür'), 'SELECT');
    await userEvent.type(within(field).getByLabelText('Seçenekler (her satıra bir tane)'), '24{enter}27');
    await userEvent.click(within(form).getByRole('button', { name: 'Kaydet' }));

    expect(createItem).toHaveBeenCalledWith({
      name: 'Monitör',
      description: 'İkinci ekran',
      category: 'OTHER',
      priority: 'MEDIUM',
      requiresApproval: true,
      active: true,
      fields: [{ key: 'ekranBoyutu', label: 'Ekran boyutu', type: 'SELECT', options: ['24', '27'], required: false }],
    });
  });
});

describe('service request notifications', () => {
  it('have their own sentences', () => {
    const base = {
      id: 1, ticketId: null, ticketTitle: null, serviceRequestId: 12, serviceRequestTitle: 'Dizüstü',
      ticketStatus: null, actorId: 'm', actorName: 'Manager One', createdAt: '2026-10-13T09:00:00Z', read: false,
    };
    expect(notificationText({ ...base, type: 'REQUEST_APPROVED' })).toBe('Manager One hizmet talebinizi onayladı');
    expect(notificationText({ ...base, type: 'REQUEST_REJECTED' })).toBe('Manager One hizmet talebinizi reddetti');
    expect(notificationText({ ...base, type: 'APPROVAL_REQUESTED' })).toBe(
      'Manager One onayınızı bekleyen bir hizmet talebi açtı',
    );
  });
});
