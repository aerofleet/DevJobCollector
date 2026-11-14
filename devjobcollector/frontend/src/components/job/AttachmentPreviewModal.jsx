import React, { lazy, Suspense } from 'react';
import { getPreviewType } from '../../utils/jobFileUrl';

const HwpPreviewModal = lazy(() => import('./HwpPreviewModal'));
const PdfPreviewModal = lazy(() => import('./PdfPreviewModal'));
const ImagePreviewModal = lazy(() => import('./ImagePreviewModal'));

export default function AttachmentPreviewModal(props) {
  const type = getPreviewType(props.file);
  const Component = type === 'HWP' ? HwpPreviewModal : type === 'PDF' ? PdfPreviewModal : ImagePreviewModal;
  return <Suspense fallback={<p role="status">미리보기를 준비하고 있습니다.</p>}><Component {...props} /></Suspense>;
}
