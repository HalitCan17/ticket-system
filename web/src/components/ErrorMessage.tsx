import { ApiError } from '../api/client.ts';
import styles from './ErrorMessage.module.css';

export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return 'Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.';
}

export function ErrorMessage({ error }: { error: unknown }) {
  return (
    <p className={styles.error} role="alert">
      {errorText(error)}
    </p>
  );
}
