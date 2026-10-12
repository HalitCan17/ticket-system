import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TransitionActions } from './TransitionActions.tsx';

describe('TransitionActions', () => {
  it('shows only the transitions the backend allows', () => {
    render(
      <TransitionActions
        status="IN_PROGRESS"
        allowed={['WAITING_FOR_CUSTOMER', 'RESOLVED']}
        role="AGENT"
        pending={false}
        onTransition={vi.fn()}
      />,
    );

    expect(screen.getAllByRole('button').map((b) => b.textContent)).toEqual([
      'Müşteriden bilgi bekle',
      'Çözüldü olarak işaretle',
    ]);
  });

  it('renders nothing without allowed transitions', () => {
    const { container } = render(
      <TransitionActions status="CLOSED" allowed={[]} role="MANAGER" pending={false} onTransition={vi.fn()} />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('asks for confirmation before a customer withdraws a new ticket', async () => {
    const onTransition = vi.fn();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<TransitionActions status="NEW" allowed={['CLOSED']} role="CUSTOMER" pending={false} onTransition={onTransition} />);

    await userEvent.click(screen.getByRole('button', { name: 'Talebi geri çek' }));
    expect(onTransition).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Talebi geri çek' }));
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(onTransition).toHaveBeenCalledWith('CLOSED');
    confirm.mockRestore();
  });

  it('transitions immediately when no note is needed', async () => {
    const onTransition = vi.fn();
    render(<TransitionActions status="NEW" allowed={['IN_PROGRESS']} role="AGENT" pending={false} onTransition={onTransition} />);

    await userEvent.click(screen.getByRole('button', { name: 'İşleme al' }));

    expect(onTransition).toHaveBeenCalledWith('IN_PROGRESS');
  });

  it('requires a resolution note before resolving', async () => {
    const onTransition = vi.fn();
    render(<TransitionActions status="IN_PROGRESS" allowed={['RESOLVED']} role="AGENT" pending={false} onTransition={onTransition} />);

    await userEvent.click(screen.getByRole('button', { name: 'Çözüldü olarak işaretle' }));
    const submit = screen.getAllByRole('button', { name: 'Çözüldü olarak işaretle' })[1]!;
    expect(submit).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Çözüm notu'), '   ');
    expect(submit).toBeDisabled();
    expect(onTransition).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText('Çözüm notu'), 'Profil yeniden oluşturuldu');
    await userEvent.click(submit);

    expect(onTransition).toHaveBeenCalledWith('RESOLVED', 'Profil yeniden oluşturuldu');
  });

  it('asks the customer for an objection reason', async () => {
    const onTransition = vi.fn();
    render(
      <TransitionActions
        status="RESOLVED"
        allowed={['IN_PROGRESS', 'CLOSED']}
        role="CUSTOMER"
        pending={false}
        onTransition={onTransition}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Çözüme itiraz et' }));
    await userEvent.type(screen.getByLabelText('İtiraz gerekçesi'), 'Hâlâ açılmıyor');
    await userEvent.click(screen.getAllByRole('button', { name: 'Çözüme itiraz et' })[1]!);

    expect(onTransition).toHaveBeenCalledWith('IN_PROGRESS', 'Hâlâ açılmıyor');
  });

  it('disables actions while a transition is pending', () => {
    render(<TransitionActions status="RESOLVED" allowed={['CLOSED']} role="CUSTOMER" pending onTransition={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Onayla ve kapat' })).toBeDisabled();
  });
});
