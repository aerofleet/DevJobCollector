import React, { useEffect, useState } from 'react';
import axios from 'axios';
import JobCard from './components/JobCard';
import 'bootstrap/dist/css/bootstrap.min.css'; // 이 줄이 핵심입니다!

function App() {
  const [jobs, setJobs] = useState([]);

  useEffect(() => {
    axios.get('/api/v1/jobs?page=0&size=12')
      .then(res => setJobs(res.data.content))
      .catch(err => console.error(err));
  }, []);

  return (
    <div className="bg-light min-vh-100">
      {/* Header */}
      <nav className="navbar navbar-expand-lg navbar-white bg-white border-bottom sticky-top shadow-sm">
        <div className="container">
          <a className="navbar-brand fw-bold text-primary" href="/">
            🚀 DevJobCollector
          </a>
        </div>
      </nav>

      {/* Main Container */}
      <div className="container py-5">
        <header className="mb-5 text-center">
          <h1 className="display-5 fw-bold text-dark mb-2">최신 채용 정보</h1>
          <p className="lead text-secondary">공공기관의 엄선된 커리어를 놓치지 마세요.</p>
        </header>

        {/* Card Grid */}
        <div className="row">
          {jobs.length > 0 ? (
            jobs.map(job => <JobCard key={job.id} job={job} />)
          ) : (
            <div className="text-center py-5">
              <div className="spinner-border text-primary" role="status"></div>
              <p className="mt-3">데이터를 불러오는 중입니다...</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;