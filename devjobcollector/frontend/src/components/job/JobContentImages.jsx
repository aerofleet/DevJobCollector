import { useState } from 'react';

function sameOriginImage(value, originalUrl) {
  try {
    const image = new URL(value);
    const source = new URL(originalUrl);
    return image.protocol === 'https:' && source.protocol === 'https:' && image.origin === source.origin
      && !image.username && !image.password && !image.hash && /\.(?:png|jpe?g|webp)$/i.test(image.pathname);
  } catch { return false; }
}

function ContentImage({ url, index, originalUrl }) {
  const [failed, setFailed] = useState(false);
  return failed
    ? <p className="job-content-empty">이미지를 불러오지 못했습니다. <a href={originalUrl} target="_blank" rel="noopener noreferrer">원문에서 확인하기</a></p>
    : <img src={url} alt={`채용 공고 원문 이미지 ${index + 1}`} loading="lazy" referrerPolicy="no-referrer"
      onError={() => setFailed(true)} />;
}

export default function JobContentImages({ urls, originalUrl }) {
  const safe = [...new Set((urls || []).filter(url => sameOriginImage(url, originalUrl)))];
  if (!safe.length) return null;
  return <section className="detail-section job-content-images"><h3>공고 원문 이미지</h3>
    <p className="detail-source-note">기업 원문에 게시된 이미지입니다. 최신 내용은 원문을 확인해 주세요.</p>
    {safe.map((url, index) => <ContentImage key={url} url={url} index={index} originalUrl={originalUrl} />)}
  </section>;
}
