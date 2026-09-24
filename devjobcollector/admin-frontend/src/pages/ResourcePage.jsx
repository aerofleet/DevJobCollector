import { Construction } from 'lucide-react';

const ResourcePage = ({ title, description }) => (
  <section className="dashboard-panel resource-placeholder">
    <Construction size={30} />
    <p className="eyebrow">IMPLEMENTATION QUEUE</p>
    <h2>{title}</h2>
    <p>{description}</p>
    <span>가짜 데이터 없이 실제 관리자 API 계약과 함께 개통합니다.</span>
  </section>
);

export default ResourcePage;
