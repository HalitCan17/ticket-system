import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CommentForm } from './CommentForm.tsx';

describe('CommentForm', () => {
  it('has no visibility choice for customers and always sends EXTERNAL', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<CommentForm staff={false} pending={false} onSubmit={onSubmit} />);

    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Yorum'), ' Windows 11 ');
    await userEvent.click(screen.getByRole('button', { name: 'Yorum ekle' }));

    expect(onSubmit).toHaveBeenCalledWith({ body: 'Windows 11', visibility: 'EXTERNAL' });
    expect(screen.getByLabelText('Yorum')).toHaveValue('');
  });

  it('lets staff choose visibility and defaults to internal', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<CommentForm staff pending={false} onSubmit={onSubmit} />);

    expect(screen.getByRole('radio', { name: 'Dahili (yalnızca ekip)' })).toBeChecked();
    await userEvent.type(screen.getByLabelText('Yorum'), 'Lisans sorunu olabilir');
    await userEvent.click(screen.getByRole('button', { name: 'Yorum ekle' }));
    expect(onSubmit).toHaveBeenLastCalledWith({ body: 'Lisans sorunu olabilir', visibility: 'INTERNAL' });

    await userEvent.click(screen.getByRole('radio', { name: 'Müşteriye açık' }));
    await userEvent.type(screen.getByLabelText('Yorum'), 'Hangi sürüm?');
    await userEvent.click(screen.getByRole('button', { name: 'Yorum ekle' }));
    expect(onSubmit).toHaveBeenLastCalledWith({ body: 'Hangi sürüm?', visibility: 'EXTERNAL' });
  });

  it('keeps the text when sending fails', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('network'));
    render(<CommentForm staff={false} pending={false} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Yorum'), 'önemli');
    await userEvent.click(screen.getByRole('button', { name: 'Yorum ekle' }));

    expect(screen.getByLabelText('Yorum')).toHaveValue('önemli');
  });

  it('cannot send an empty comment', () => {
    render(<CommentForm staff={false} pending={false} onSubmit={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Yorum ekle' })).toBeDisabled();
  });
});
