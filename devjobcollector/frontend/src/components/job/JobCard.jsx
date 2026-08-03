import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import TechStackBadge from './TechStackBadge';
import { formatDate, getDaysRemaining } from '../../utils/dateParser';
import { getCompanyInitials, getCompanyLogoUrl } from '../../utils/companyLogo';
import '../../styles/JobCard.css';

const JobCard = ({ job }) => {
  const navigate = useNavigate();
  const [isExpanded, setIsExpanded] = useState(false);
  const [failedLogoUrl, setFailedLogoUrl] = useState(null);

  const handleCardClick = () => {
      navigate(`/job/${job.id}`);      
  };

  const daysRemaining = getDaysRemaining(job.endDate);
  const endDateLabel = job.endDate ? formatDate(job.endDate) : '미등록';
  const logoUrl = getCompanyLogoUrl(job);
  const showLogo = logoUrl && failedLogoUrl !== logoUrl;

  return (
    <div className='job-card' onClick={handleCardClick}>
      {/* 썸네일 영역 */}
      <div className='job-thumbnail'>
        {showLogo ? (
          <div className='company-logo-surface'>
            <img
              src={logoUrl}
              alt={`${job.companyName} 로고`}
              className='company-logo-image'
              loading='lazy'
              onError={() => setFailedLogoUrl(logoUrl)}
            />
          </div>
        ) : (
          <div className='thumbnail-placeholder'>
            <span className='company-initials' aria-hidden='true'>
              {getCompanyInitials(job.companyName)}
            </span>
          </div>
        )}
        {/* D-day 배지 */}
        {daysRemaining !== null && daysRemaining >= 0 && (
          <span className='days-badge'>D-{daysRemaining}</span>
        )}
      </div>
      
      {/* 컨텐츠 영역 */}
      <div className='job-content'>
        {/* 회사명 + 마감일 */}
        <div className='job-company-row'>
          <p className='job-company'>{job.companyName}</p>
          <time className='job-end-date' dateTime={job.endDate || undefined}>
            마감일: {endDateLabel}
          </time>
        </div>

        {/* 고용 카테고리 */}
        <h3  
          className={`job-title ${isExpanded ? 'expanded' : ''}`}
          onMouseEnter={() => setIsExpanded(true)}
          onMouseLeave={() => setIsExpanded(false)}
        >
            {job.title}
        </h3>
        {/* 기술 스택 */}
        <div className="tech-stack">
          {job.techStacks?.map((tech) => (
            <TechStackBadge key={tech.id} tech={tech} />
          ))}
        </div>

        {/* 지역 + 경력 */}
        <div className='job-location-experience'>
          <span>{job.location || '위치미정'}</span>
          <span className='separator'>·</span>
          <span>{job.experience || '경력무관'}</span>
          <span className='separator'>·</span>
          <span>{job.hireType || '경력무관'}</span>
        </div>
      </div>
    </div>
  );
};

export default JobCard;
