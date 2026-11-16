import '../../styles/PageLoading.css';

export default function PageLoading({ message = '페이지를 불러오고 있어요' }) {
  return <main className="page-loading" role="status" aria-live="polite" aria-busy="true">
    <span className="page-loading-spinner" aria-hidden="true" />
    <p>{message}</p>
  </main>;
}
