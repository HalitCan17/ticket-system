import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section>
      <h1>Sayfa bulunamadı</h1>
      <p>
        <Link to="/tickets">Taleplere dön</Link>
      </p>
    </section>
  );
}
