import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { aTicket } from '../test/fixtures.ts';
import { Rating } from './Rating.tsx';

describe('Rating', () => {
  it('renders nothing when the caller cannot rate and there is no rating', () => {
    const { container } = render(<Rating ticket={aTicket()} pending={false} onRate={vi.fn()} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('lets the requester pick a score and send an optional comment', async () => {
    const onRate = vi.fn();
    render(<Rating ticket={aTicket({ status: 'RESOLVED', canRate: true })} pending={false} onRate={onRate} />);

    const send = screen.getByRole('button', { name: 'Puanı gönder' });
    expect(send).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: '4 – İyi' }));
    expect(screen.getByText('İyi')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Yorum (isteğe bağlı)'), '  Teşekkürler ');
    await userEvent.click(send);

    expect(onRate).toHaveBeenCalledWith(4, 'Teşekkürler');
  });

  it('sends no comment when it is blank', async () => {
    const onRate = vi.fn();
    render(<Rating ticket={aTicket({ status: 'CLOSED', canRate: true })} pending={false} onRate={onRate} />);

    await userEvent.click(screen.getByRole('radio', { name: '1 – Çok kötü' }));
    await userEvent.click(screen.getByRole('button', { name: 'Puanı gönder' }));

    expect(onRate).toHaveBeenCalledWith(1, undefined);
  });

  it('shows an existing rating read-only', () => {
    render(
      <Rating
        ticket={aTicket({
          status: 'CLOSED',
          rating: { score: 5, comment: 'Harika', ratedAt: '2026-10-12T10:00:00Z' },
        })}
        pending={false}
        onRate={vi.fn()}
      />,
    );

    expect(screen.getByRole('region', { name: 'Memnuniyet puanı' })).toHaveTextContent('5/5 · Çok iyi');
    expect(screen.getByText('Harika')).toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });
});
