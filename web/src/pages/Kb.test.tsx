import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { KbArticle, KbArticleSummary, Page } from '../api/types.ts';
import { ApiError } from '../api/client.ts';
import { CommentForm } from '../components/CommentForm.tsx';
import { fakeKbApi, renderApp } from '../test/renderApp.tsx';

function aSummary(overrides: Partial<KbArticleSummary> = {}): KbArticleSummary {
  return {
    id: 1,
    title: 'VPN bağlantısı kurulamıyor',
    excerpt: 'İnternet bağlantınızı doğrulayın.',
    category: 'NETWORK',
    visibility: 'PUBLIC',
    updatedAt: '2026-10-12T10:00:00Z',
    ...overrides,
  };
}

function anArticle(overrides: Partial<KbArticle> = {}): KbArticle {
  return {
    id: 1,
    title: 'VPN bağlantısı kurulamıyor',
    body: '1. Bağlantıyı doğrulayın.\n2. İstemciyi yeniden açın.',
    category: 'NETWORK',
    visibility: 'PUBLIC',
    createdBy: 'agent-1',
    createdAt: '2026-10-12T09:00:00Z',
    updatedBy: 'agent-1',
    updatedAt: '2026-10-12T10:00:00Z',
    helpfulYes: 2,
    helpfulNo: 1,
    myVote: null,
    ...overrides,
  };
}

function pageOf(items: KbArticleSummary[]): Page<KbArticleSummary> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

describe('KbListPage', () => {
  it('lists articles for a customer without write links', async () => {
    const kb = fakeKbApi({ search: vi.fn().mockResolvedValue(pageOf([aSummary()])) });
    renderApp('/kb', { role: 'CUSTOMER', kb });

    const list = await screen.findByRole('list', { name: 'Makaleler' });
    expect(within(list).getByRole('link', { name: 'VPN bağlantısı kurulamıyor' })).toHaveAttribute('href', '/kb/1');
    expect(screen.queryByRole('link', { name: '+ Yeni makale' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Görünürlük')).not.toBeInTheDocument();
  });

  it('searches by text and category, and staff also filter by visibility', async () => {
    const search = vi.fn().mockResolvedValue(pageOf([aSummary({ visibility: 'DRAFT' })]));
    renderApp('/kb', { role: 'AGENT', kb: fakeKbApi({ search }) });

    const list = await screen.findByRole('list', { name: 'Makaleler' });
    expect(within(list).getByText('Taslak')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '+ Yeni makale' })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Ara'), 'vpn');
    await vi.waitFor(() => expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'vpn' })));
    await userEvent.selectOptions(screen.getByLabelText('Kategori'), 'NETWORK');
    await vi.waitFor(() =>
      expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'vpn', category: 'NETWORK' })),
    );
    await userEvent.selectOptions(screen.getByLabelText('Görünürlük'), 'DRAFT');
    await vi.waitFor(() =>
      expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ category: 'NETWORK', visibility: 'DRAFT' })),
    );
  });

  it('says so when nothing matches', async () => {
    renderApp('/kb?q=yok', { role: 'CUSTOMER' });
    expect(await screen.findByText('Aramaya uyan makale yok.')).toBeInTheDocument();
  });
});

describe('KbArticlePage', () => {
  it('shows the article and records a vote that can be changed', async () => {
    const vote = vi.fn().mockResolvedValue(anArticle({ helpfulYes: 3, myVote: true }));
    const kb = fakeKbApi({ get: vi.fn().mockResolvedValue(anArticle()), vote });
    renderApp('/kb/1', { role: 'CUSTOMER', kb });

    expect(await screen.findByRole('heading', { name: 'VPN bağlantısı kurulamıyor' })).toBeInTheDocument();
    expect(screen.getByText(/İstemciyi yeniden açın/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Düzenle' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sil' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Evet (2)' }));

    expect(vote).toHaveBeenCalledWith(1, true);
    expect(await screen.findByRole('button', { name: 'Evet (3)' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('lets staff edit and only managers delete', async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    const kb = fakeKbApi({ get: vi.fn().mockResolvedValue(anArticle()), remove });
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const agent = renderApp('/kb/1', { role: 'AGENT', kb });
    expect(await screen.findByRole('link', { name: 'Düzenle' })).toHaveAttribute('href', '/kb/1/edit');
    expect(screen.queryByRole('button', { name: 'Sil' })).not.toBeInTheDocument();
    agent.unmount();

    renderApp('/kb/1', { role: 'MANAGER', kb });
    await userEvent.click(await screen.findByRole('button', { name: 'Sil' }));
    expect(remove).toHaveBeenCalledWith(1);
  });

  it('shows the error for an article the user may not read', async () => {
    const kb = fakeKbApi({
      get: vi.fn().mockRejectedValue(new ApiError({ status: 404, code: 'KB_ARTICLE_NOT_FOUND', detail: 'Article 9 not found.' })),
    });
    renderApp('/kb/9', { role: 'CUSTOMER', kb });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

describe('KbEditPage', () => {
  it('lets an agent write an article, defaulting to a draft', async () => {
    const create = vi.fn().mockResolvedValue(anArticle({ id: 5, visibility: 'PUBLIC' }));
    renderApp('/kb/new', { role: 'AGENT', kb: fakeKbApi({ create, get: vi.fn().mockResolvedValue(anArticle({ id: 5 })) }) });

    const form = await screen.findByRole('form', { name: 'Makale' });
    expect(within(form).getByLabelText('Görünürlük')).toHaveValue('DRAFT');
    await userEvent.type(within(form).getByLabelText('Başlık'), ' Yeni konu ');
    await userEvent.type(within(form).getByLabelText('İçerik'), 'Adımlar');
    await userEvent.selectOptions(within(form).getByLabelText('Kategori'), 'SOFTWARE');
    await userEvent.selectOptions(within(form).getByLabelText('Görünürlük'), 'PUBLIC');
    await userEvent.click(within(form).getByRole('button', { name: 'Kaydet' }));

    expect(create).toHaveBeenCalledWith({
      title: 'Yeni konu',
      body: 'Adımlar',
      category: 'SOFTWARE',
      visibility: 'PUBLIC',
    });
    expect(await screen.findByRole('heading', { name: 'VPN bağlantısı kurulamıyor' })).toBeInTheDocument();
  });

  it('is not available to customers', async () => {
    renderApp('/kb/new', { role: 'CUSTOMER' });
    expect(await screen.findByText('Bu sayfa yalnızca destek ekibi içindir.')).toBeInTheDocument();
  });
});

describe('knowledge base suggestions on a new ticket', () => {
  it('offers public articles for the title being typed, opening in a new tab', async () => {
    const search = vi.fn().mockResolvedValue(pageOf([aSummary()]));
    renderApp('/tickets/new', { role: 'CUSTOMER', kb: fakeKbApi({ search }) });

    await userEvent.type(await screen.findByLabelText('Başlık'), 'vp');
    expect(search).not.toHaveBeenCalled(); // too short to look up

    await userEvent.type(screen.getByLabelText('Başlık'), 'n');
    const box = await screen.findByRole('complementary', { name: 'Önerilen makaleler' });
    const link = within(box).getByRole('link', { name: 'VPN bağlantısı kurulamıyor' });
    expect(link).toHaveAttribute('href', '/kb/1');
    expect(link).toHaveAttribute('target', '_blank');
    expect(search).toHaveBeenCalledWith({ q: 'vpn', visibility: 'PUBLIC', size: 3 });
  });
});

describe('knowledge base links in the comment form', () => {
  const articles = [
    aSummary({ id: 1, title: 'Herkese açık makale' }),
    aSummary({ id: 2, title: 'İç not makalesi', visibility: 'INTERNAL' }),
    aSummary({ id: 3, title: 'Yarım makale', visibility: 'DRAFT' }),
  ];

  it('inserts the title and link, offering internal articles only on internal comments', async () => {
    render(<CommentForm staff pending={false} onSubmit={vi.fn()} articles={articles} />);

    const picker = screen.getByLabelText('Bilgi bankası makalesi');
    // Internal comment (the staff default): public and internal articles, never drafts.
    expect(within(picker).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Seçin…',
      'Herkese açık makale',
      'İç not makalesi (dahili)',
    ]);

    await userEvent.selectOptions(picker, '1');
    expect(screen.getByLabelText('Yorum')).toHaveValue(`Herkese açık makale: ${window.location.origin}/kb/1`);

    await userEvent.click(screen.getByRole('radio', { name: 'Müşteriye açık' }));
    expect(within(screen.getByLabelText('Bilgi bankası makalesi')).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Seçin…',
      'Herkese açık makale',
    ]);
  });

  it('is not shown to customers', () => {
    render(<CommentForm staff={false} pending={false} onSubmit={vi.fn()} articles={articles} />);
    expect(screen.queryByLabelText('Bilgi bankası makalesi')).not.toBeInTheDocument();
  });
});
