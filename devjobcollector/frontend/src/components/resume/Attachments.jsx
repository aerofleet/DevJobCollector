import { useEffect, useRef, useState } from 'react';
import { CirclePlus, Download, FileText, X } from 'lucide-react';
import authenticatedApi from '../../api/authenticatedApi';
import styles from './Attachments.module.css';

export default function Attachments({ resumeId, ensureResume, onBusyChange, companyId, disabled = false }) {
  const [files, setFiles] = useState([]);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(Boolean(resumeId));
  const [error, setError] = useState('');
  const [loadFailed, setLoadFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const input = useRef(null);
  const readonly = Boolean(companyId);
  const base = (id) => companyId ? `/companies/${companyId}/resumes/${id}/attachments` : `/members/me/resumes/${id}/attachments`;

  useEffect(() => {
    let cancelled = false;
    if (!resumeId) return undefined;
    const path = companyId ? `/companies/${companyId}/resumes/${resumeId}/attachments` : `/members/me/resumes/${resumeId}/attachments`;
    setLoading(true);
    setLoadFailed(false);
    authenticatedApi.get(path).then(({ data }) => {
      if (!cancelled) { setFiles(data); setError(''); }
    }).catch(() => {
      if (!cancelled) { setFiles([]); setLoadFailed(true); setError('첨부파일 목록을 불러오지 못했습니다.'); }
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [resumeId, companyId, reload]);

  const run = async (operation) => {
    setBusy(true);
    onBusyChange?.(true);
    setError('');
    try { await operation(); }
    catch (failure) {
      setError(failure.response?.status === 413 ? '파일당 5MB 이하로 첨부해 주세요.'
        : failure.message?.startsWith('이력서 제목') ? failure.message
          : '첨부파일을 처리하지 못했습니다. 파일 형식·크기와 접근 권한을 확인하고 다시 시도해 주세요.');
    } finally { setBusy(false); onBusyChange?.(false); }
  };

  const upload = (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (!/\.(pdf|png|jpe?g)$/i.test(file.name) || !file.size || file.size > 5 * 1024 * 1024) {
      setError('5MB 이하의 PDF, PNG, JPG 파일을 선택해 주세요.');
      return;
    }
    run(async () => {
      const id = resumeId || await ensureResume();
      const body = new FormData();
      body.append('file', file);
      const { data } = await authenticatedApi.post(base(id), body, { headers: { 'Content-Type': undefined } });
      setFiles((current) => [...current.filter((item) => item.id !== data.id), data]);
      setReload((value) => value + 1);
    });
  };

  const download = (file) => run(async () => {
    const { data } = await authenticatedApi.get(`${base(resumeId)}/${file.id}`, { responseType: 'blob' });
    const url = URL.createObjectURL(data);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.filename;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });

  const remove = (file) => {
    if (!window.confirm(`"${file.filename}" 첨부파일을 삭제하시겠습니까?`)) return;
    run(async () => {
      await authenticatedApi.delete(`${base(resumeId)}/${file.id}`);
      setFiles((current) => current.filter((item) => item.id !== file.id));
    });
  };

  return <section id="attachments" className={styles.section} aria-label="첨부파일">
    <h2>첨부파일</h2>
    {!readonly && <p className={styles.description}>경험을 보여줄 수 있는 포트폴리오 / 경력기술서 등을 첨부해보세요. (PDF를 권장합니다.)</p>}
    {loading ? <p role="status">첨부파일을 불러오는 중입니다.</p> : <ul className={styles.list}>
      {!loadFailed && files.length === 0 && <li className={styles.empty}>첨부파일이 비어있습니다.</li>}
      {files.map((file) => <li key={file.id} className={styles.file}>
        <FileText size={18} aria-hidden="true" />
        <button className={styles.filename} type="button" disabled={busy} onClick={() => download(file)}>
          {file.filename}<Download size={14} aria-hidden="true" />
        </button>
        <span className={styles.size}>{(file.size / 1024 / 1024).toFixed(1)}MB</span>
        {!readonly && <button className={styles.remove} type="button" aria-label={`${file.filename} 삭제`} disabled={busy} onClick={() => remove(file)}><X size={16} /></button>}
      </li>)}
    </ul>}
    {!readonly && <>
      <button className={styles.add} type="button" disabled={disabled || busy || loading || loadFailed || files.length >= 5} onClick={() => input.current?.click()}>
        <CirclePlus size={20} aria-hidden="true" /> 첨부파일 추가
      </button>
      <input ref={input} type="file" hidden aria-label="첨부파일 선택" accept=".pdf,.png,.jpg,.jpeg" onChange={upload} />
      <p className={styles.hint}>PDF, PNG, JPG · 파일당 최대 5MB · 최대 5개<br />첨부파일은 추가·삭제 즉시 저장됩니다.</p>
    </>}
    {busy && <p role="status">첨부파일 처리 중…</p>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {loadFailed && <button type="button" onClick={() => setReload((value) => value + 1)}>첨부파일 다시 불러오기</button>}
  </section>;
}
