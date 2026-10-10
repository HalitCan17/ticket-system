import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { useTicketsApi } from '../api/ApiContext.tsx';
import { ApiError } from '../api/client.ts';
import { PriorityBadge, SlaBadge, StatusBadge } from '../components/Badges.tsx';
import { ErrorMessage } from '../components/ErrorMessage.tsx';

export function TicketDetailPage() {
  const api = useTicketsApi();
  const id = Number(useParams().id);

  const ticket = useQuery({
    queryKey: ['ticket', id],
    queryFn: () => api.get(id),
    enabled: Number.isInteger(id) && id > 0,
  });

  if (!Number.isInteger(id) || id <= 0 || (ticket.error instanceof ApiError && ticket.error.status === 404)) {
    return (
      <section>
        <h1>Talep bulunamadı</h1>
        <Link to="/tickets">Taleplere dön</Link>
      </section>
    );
  }
  if (ticket.isPending) {
    return <p>Yükleniyor…</p>;
  }
  if (ticket.isError) {
    return <ErrorMessage error={ticket.error} />;
  }

  const t = ticket.data;
  return (
    <section>
      <h1>
        #{t.id} {t.title}
      </h1>
      <p>
        <StatusBadge status={t.status} /> <PriorityBadge priority={t.priority} /> <SlaBadge sla={t.sla} />
      </p>
      <p>{t.description}</p>
    </section>
  );
}
