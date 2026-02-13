import React from 'react';
import { Calendar, MapPin, Building2, ExternalLink } from 'lucide-react';

const JobCard = ({ job }) => {
  return (
    <div className="col-md-6 col-lg-4 mb-4">
      <div className="card h-100 shadow-sm border-0 rounded-3 overflow-hidden hover-shadow transition">
        <div className="card-body p-4">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <span className="badge rounded-pill bg-primary bg-opacity-10 text-primary px-3 py-2">
              {job.hireType}
            </span>
            <small className="text-muted">{job.sourcePlatform}</small>
          </div>
          
          <h5 className="card-title fw-bold mb-3 text-dark leading-sm" style={{ height: '3rem', overflow: 'hidden' }}>
            {job.title}
          </h5>
          
          <div className="mb-4">
            <div className="d-flex align-items-center text-secondary mb-2">
              <Building2 size={16} className="me-2" />
              <span className="small fw-medium">{job.companyName}</span>
            </div>
            <div className="d-flex align-items-center text-muted">
              <MapPin size={16} className="me-2" />
              <span className="small">{job.location}</span>
            </div>
          </div>
          
          <div className="d-flex justify-content-between align-items-center pt-3 border-top">
            <div>
              <p className="text-uppercase text-muted mb-0" style={{ fontSize: '0.65rem', fontWeight: '800' }}>Deadline</p>
              <div className="d-flex align-items-center text-dark fw-bold small">
                <Calendar size={14} className="me-1 text-primary" />
                {job.endDate}
              </div>
            </div>
            <a 
              href={job.originalUrl} 
              target="_blank" 
              rel="noopener noreferrer"
              className="btn btn-outline-primary btn-sm rounded-circle p-2"
            >
              <ExternalLink size={18} />
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};

export default JobCard;