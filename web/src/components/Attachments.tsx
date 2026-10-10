import { useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTicketsApi } from '../api/ApiContext.tsx';
import type { Attachment, CommentVisibility } from '../api/types.ts';
import { formatBytes, formatDateTime, userLabel } from '../format.ts';
import { VISIBILITY_LABELS } from '../labels.ts';
import { ErrorMessage, errorText } from './ErrorMessage.tsx';
import styles from './Attachments.module.css';

/** Mirrors the backend's AttachmentTypePolicy / max size; the backend remains the real check. */
export const MAX_FILE_BYTES = 10 * 1024 * 1024;
export const ALLOWED_EXTENSIONS = ['csv', 'docx', 'gif', 'jpeg', 'jpg', 'log', 'pdf', 'png', 'pptx', 'txt', 'webp', 'xlsx', 'zip'];

export function clientSideProblem(file: File): string | null {
  const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return `Bu dosya türü eklenemez. İzin verilenler: ${ALLOWED_EXTENSIONS.join(', ')}.`;
  }
  if (file.size === 0) {
    return 'Dosya boş.';
  }
  if (file.size > MAX_FILE_BYTES) {
    return `Dosya en fazla ${formatBytes(MAX_FILE_BYTES)} olabilir.`;
  }
  return null;
}

interface Props {
  ticketId: number;
  currentUserId: string;
  staff: boolean;
  closed: boolean;
}

export function Attachments({ ticketId, currentUserId, staff, closed }: Props) {
  const api = useTicketsApi();
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [visibility, setVisibility] = useState<CommentVisibility>(staff ? 'INTERNAL' : 'EXTERNAL');
  const [localError, setLocalError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);

  const list = useQuery({ queryKey: ['attachments', ticketId], queryFn: () => api.attachments(ticketId) });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['attachments', ticketId] });

  const upload = useMutation({
    mutationFn: (f: File) => api.uploadAttachment(ticketId, f, staff ? visibility : 'EXTERNAL'),
    onSuccess: () => {
      setFile(null);
      if (fileInput.current) fileInput.current.value = '';
      void refresh();
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.deleteAttachment(ticketId, id),
    onSuccess: () => void refresh(),
    onError: setActionError,
  });

  function choose(selected: File | null) {
    setFile(selected);
    upload.reset();
    setLocalError(selected ? clientSideProblem(selected) : null);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (file && !localError) upload.mutate(file);
  }

  async function download(a: Attachment) {
    setActionError(null);
    try {
      const blob = await api.downloadAttachment(ticketId, a.id);
      // The endpoint needs the bearer token, so a plain link cannot be used.
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = a.fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      setActionError(error);
    }
  }

  function confirmDelete(a: Attachment) {
    if (window.confirm(`"${a.fileName}" silinsin mi?`)) {
      setActionError(null);
      remove.mutate(a.id);
    }
  }

  return (
    <section>
      <h2>Ekler</h2>
      {list.isError && <ErrorMessage error={list.error} />}
      {list.data && list.data.length === 0 && <p className={styles.muted}>Ek yok.</p>}
      {list.data && list.data.length > 0 && (
        <ul className={styles.list}>
          {list.data.map((a) => (
            <li key={a.id} className={styles.item}>
              <div className={styles.info}>
                <button type="button" className={styles.name} onClick={() => void download(a)}>
                  {a.fileName}
                </button>
                <span className={styles.muted}>
                  {formatBytes(a.sizeBytes)} · {userLabel(a.uploaderId, a.uploaderName, currentUserId)} ·{' '}
                  {formatDateTime(a.createdAt)}
                </span>
              </div>
              {a.visibility === 'INTERNAL' && <strong className={styles.internal}>Dahili</strong>}
              {a.canDelete && (
                <button
                  type="button"
                  className="secondary"
                  aria-label={`${a.fileName} sil`}
                  disabled={remove.isPending}
                  onClick={() => confirmDelete(a)}
                >
                  Sil
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {actionError !== null && <ErrorMessage error={actionError} />}

      {closed ? (
        <p className={styles.muted}>Kapalı taleplere dosya eklenemez.</p>
      ) : (
        <form className={styles.form} onSubmit={submit}>
          <label htmlFor="attachment-file">Dosya ekle</label>
          <input
            id="attachment-file"
            ref={fileInput}
            type="file"
            accept={ALLOWED_EXTENSIONS.map((e) => `.${e}`).join(',')}
            onChange={(e) => choose(e.target.files?.[0] ?? null)}
          />
          <p className={styles.hint}>En fazla {formatBytes(MAX_FILE_BYTES)}; resim, PDF, metin/log, CSV, zip, Office.</p>
          {localError && (
            <p className={styles.error} role="alert">
              {localError}
            </p>
          )}
          {upload.isError && (
            <p className={styles.error} role="alert">
              {errorText(upload.error)}
            </p>
          )}
          <div className={styles.footer}>
            {staff && (
              <fieldset className={styles.visibility}>
                <legend>Görünürlük</legend>
                {(['INTERNAL', 'EXTERNAL'] as const).map((v) => (
                  <label key={v}>
                    <input
                      type="radio"
                      name="attachment-visibility"
                      value={v}
                      checked={visibility === v}
                      onChange={() => setVisibility(v)}
                    />
                    {VISIBILITY_LABELS[v]}
                  </label>
                ))}
              </fieldset>
            )}
            <button type="submit" disabled={!file || localError !== null || upload.isPending}>
              {upload.isPending ? 'Yükleniyor…' : 'Yükle'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
