import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client.ts';
import type { Attachment } from '../api/types.ts';
import { aTicket } from '../test/fixtures.ts';
import { fakeApi, renderApp } from '../test/renderApp.tsx';
import { clientSideProblem, MAX_FILE_BYTES } from './Attachments.tsx';

function anAttachment(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: 1,
    fileName: 'ekran.png',
    contentType: 'image/png',
    sizeBytes: 34 * 1024,
    visibility: 'EXTERNAL',
    uploaderId: 'cust-1',
    uploaderName: 'Customer One',
    createdAt: '2026-10-11T09:00:00Z',
    canDelete: true,
    ...overrides,
  };
}

function file(name: string, size = 10, type = 'application/octet-stream'): File {
  const f = new File(['x'.repeat(Math.min(size, 10))], name, { type });
  Object.defineProperty(f, 'size', { value: size });
  return f;
}

describe('clientSideProblem', () => {
  it('accepts allowed files and explains rejections', () => {
    expect(clientSideProblem(file('rapor.PDF'))).toBeNull();
    expect(clientSideProblem(file('setup.exe'))).toMatch(/Bu dosya türü eklenemez/);
    expect(clientSideProblem(file('noext'))).toMatch(/Bu dosya türü eklenemez/);
    expect(clientSideProblem(file('empty.txt', 0))).toBe('Dosya boş.');
    expect(clientSideProblem(file('big.zip', MAX_FILE_BYTES + 1))).toMatch(/en fazla 10 MB/);
  });
});

describe('Attachments on the ticket page', () => {
  const createObjectURL = vi.fn(() => 'blob:fake');
  const revokeObjectURL = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists attachments with size, uploader and internal badge', async () => {
    const api = fakeApi({
      get: vi.fn().mockResolvedValue(aTicket()),
      attachments: vi.fn().mockResolvedValue([
        anAttachment(),
        anAttachment({ id: 2, fileName: 'notlar.log', visibility: 'INTERNAL', uploaderId: 'agent-1', uploaderName: 'Agent One', canDelete: false }),
      ]),
    });
    renderApp('/tickets/1', { role: 'AGENT', api });

    const own = (await screen.findByRole('button', { name: 'ekran.png' })).closest('li')!;
    expect(within(own).getByText(/34 KB · Customer One/)).toBeInTheDocument();
    expect(within(own).queryByText('Dahili')).not.toBeInTheDocument();

    const internal = screen.getByRole('button', { name: 'notlar.log' }).closest('li')!;
    expect(within(internal).getByText('Dahili')).toBeInTheDocument();
    expect(within(internal).queryByRole('button', { name: /sil/ })).not.toBeInTheDocument();
  });

  it('uploads as external for customers', async () => {
    const uploadAttachment = vi.fn().mockResolvedValue(anAttachment());
    const attachments = vi.fn().mockResolvedValue([]);
    const api = fakeApi({ get: vi.fn().mockResolvedValue(aTicket()), uploadAttachment, attachments });
    renderApp('/tickets/1', { role: 'CUSTOMER', api });

    const input = await screen.findByLabelText('Dosya ekle');
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    const picked = file('log.txt');
    await userEvent.upload(input, picked);
    await userEvent.click(screen.getByRole('button', { name: 'Yükle' }));

    expect(uploadAttachment).toHaveBeenCalledWith(1, picked, 'EXTERNAL');
    await vi.waitFor(() => expect(attachments).toHaveBeenCalledTimes(2));
  });

  it('lets staff pick visibility, defaulting to internal', async () => {
    const uploadAttachment = vi.fn().mockResolvedValue(anAttachment());
    renderApp('/tickets/1', {
      role: 'AGENT',
      api: fakeApi({ get: vi.fn().mockResolvedValue(aTicket()), uploadAttachment }),
    });

    await userEvent.upload(await screen.findByLabelText('Dosya ekle'), file('log.txt'));
    const form = screen.getByLabelText('Dosya ekle').closest('form')!;
    expect(within(form).getByRole('radio', { name: 'Dahili (yalnızca ekip)' })).toBeChecked();
    await userEvent.click(within(form).getByRole('button', { name: 'Yükle' }));

    expect(uploadAttachment).toHaveBeenCalledWith(1, expect.any(File), 'INTERNAL');
  });

  it('blocks disallowed files before sending', async () => {
    const uploadAttachment = vi.fn();
    renderApp('/tickets/1', { api: fakeApi({ get: vi.fn().mockResolvedValue(aTicket()), uploadAttachment }) });

    await userEvent.upload(await screen.findByLabelText('Dosya ekle'), file('virus.exe'), { applyAccept: false });

    expect(screen.getByRole('alert')).toHaveTextContent(/Bu dosya türü eklenemez/);
    expect(screen.getByRole('button', { name: 'Yükle' })).toBeDisabled();
    expect(uploadAttachment).not.toHaveBeenCalled();
  });

  it('shows the backend reason when an upload is rejected', async () => {
    const uploadAttachment = vi.fn().mockRejectedValue(
      new ApiError({ status: 400, code: 'UNSUPPORTED_FILE_TYPE', detail: 'The file content does not match its .png extension.' }),
    );
    renderApp('/tickets/1', { api: fakeApi({ get: vi.fn().mockResolvedValue(aTicket()), uploadAttachment }) });

    await userEvent.upload(await screen.findByLabelText('Dosya ekle'), file('fake.png'));
    await userEvent.click(screen.getByRole('button', { name: 'Yükle' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('does not match its .png extension');
  });

  it('downloads through the API with the original file name', async () => {
    const downloadAttachment = vi.fn().mockResolvedValue(new Blob(['png']));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    renderApp('/tickets/1', {
      api: fakeApi({
        get: vi.fn().mockResolvedValue(aTicket()),
        attachments: vi.fn().mockResolvedValue([anAttachment({ id: 5 })]),
        downloadAttachment,
      }),
    });

    await userEvent.click(await screen.findByRole('button', { name: 'ekran.png' }));

    expect(downloadAttachment).toHaveBeenCalledWith(1, 5);
    await vi.waitFor(() => expect(click).toHaveBeenCalled());
    const link = click.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toBe('ekran.png');
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:fake');
  });

  it('deletes after confirmation', async () => {
    const deleteAttachment = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    renderApp('/tickets/1', {
      api: fakeApi({
        get: vi.fn().mockResolvedValue(aTicket()),
        attachments: vi.fn().mockResolvedValue([anAttachment({ id: 5 })]),
        deleteAttachment,
      }),
    });

    const button = await screen.findByRole('button', { name: 'ekran.png sil' });
    await userEvent.click(button);
    expect(deleteAttachment).not.toHaveBeenCalled();

    await userEvent.click(button);
    expect(deleteAttachment).toHaveBeenCalledWith(1, 5);
  });

  it('has no upload form on closed tickets', async () => {
    renderApp('/tickets/1', { api: fakeApi({ get: vi.fn().mockResolvedValue(aTicket({ status: 'CLOSED' })) }) });

    expect(await screen.findByText('Kapalı taleplere dosya eklenemez.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Dosya ekle')).not.toBeInTheDocument();
  });
});
