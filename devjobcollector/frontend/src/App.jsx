import React, { useEffect, useState, useRef, useCallback } from 'react';
import { 
  Container, Row, Col, Card, Badge, Form, 
  InputGroup, Alert, Spinner 
} from 'react-bootstrap';
import { Search, Briefcase, MapPin, TrendingUp, Calendar, ExternalLink } from 'lucide-react';
import axios from 'axios';
import 'bootstrap/dist/css/bootstrap.min.css';
import './App.css';

function App() {
  const [jobPosts, setJobPosts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [searchKeyword, setSearchKeyword] = useState('');

  const observer = useRef();
  const pageSize = 12;

  // IntersectionObserver 콜백
  const lastElementRef = useCallback(node => {
    if (loading) return;
    if (observer.current) observer.current.disconnect();
    
    observer.current = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && hasMore) {
        setPage(prevPage => prevPage + 1);
      }
    });
    
    if (node) observer.current.observe(node);
  }, [loading, hasMore]);

  // 데이터 로딩
  const fetchJobs = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      
      const response = await axios.get('/api/v1/jobs', {
        params: { page, size: pageSize }
      });

      console.log('API 응답:', response.data);

      const content = response.data.content || [];
      const isLast = response.data.last;

      setJobPosts(prev => (page === 0 ? content : [...prev, ...content]));
      setTotalElements(response.data.totalElements || 0);
      setHasMore(!isLast && content.length > 0);
      
    } catch (err) {
      console.error('데이터 로딩 에러:', err);
      setError(err.response?.data?.message || '데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  // 검색
  const handleSearch = (e) => {
    e.preventDefault();
    console.log('검색어:', searchKeyword);
    // TODO: 검색 API 구현
    setJobPosts([]);
    setPage(0);
    setHasMore(true);
  };

  // 카드 클릭
  const handleCardClick = (post) => {
    window.open(post.originalUrl, '_blank', 'noopener,noreferrer');
  };

  // 초기 로딩
  if (loading && page === 0 && jobPosts.length === 0) {
    return (
      <Container className="mt-5 text-center">
        <Spinner animation="border" variant="primary" style={{ width: '3rem', height: '3rem' }} />
        <p className="mt-3 text-muted">채용 공고를 불러오는 중...</p>
      </Container>
    );
  }

  return (
    <div className="bg-light min-vh-100 pb-5">
      {/* 네비게이션 */}
      <nav className="navbar navbar-dark bg-primary shadow-sm mb-4 sticky-top">
        <Container>
          <span className="navbar-brand fw-bold">
            <Briefcase className="me-2" size={24} />
            IT Job Collector
          </span>
        </Container>
      </nav>

      <Container>
        {/* 통계 */}
        <Row className="mb-4">
          <Col md={12}>
            <Card className="border-0 shadow-sm p-3 nohover">
              <div className="d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center">
                  <TrendingUp size={24} className="text-primary me-2" />
                  <h5 className="mb-0 fw-bold">
                    전체 채용 공고 <span className="text-primary">{totalElements.toLocaleString()}</span>건
                  </h5>
                </div>
                <div className="text-muted small">
                  현재 {jobPosts.length}개 표시 중
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        {/* 검색바 */}
        <Card className="border-0 shadow-sm mb-5 nohover">
          <Card.Body>
            <Form onSubmit={handleSearch}>
              <InputGroup size="lg">
                <InputGroup.Text className="bg-white border-end-0">
                  <Search size={20} className="text-muted" />
                </InputGroup.Text>
                <Form.Control
                  placeholder="회사명, 직무, 기술 스택으로 검색"
                  className="border-start-0 shadow-none"
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                />
                <button type="submit" className="btn btn-primary">
                  검색
                </button>
              </InputGroup>
            </Form>
          </Card.Body>
        </Card>

        {/* 에러 표시 */}
        {error && (
          <Alert variant="danger" dismissible onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {/* 빈 상태 */}
        {!loading && jobPosts.length === 0 && !error && (
          <div className="text-center py-5">
            <Briefcase size={64} className="text-muted mb-3" />
            <h4 className="text-muted">등록된 채용 공고가 없습니다</h4>
            <p className="text-secondary">나중에 다시 확인해주세요.</p>
          </div>
        )}

        {/* 공고 리스트 */}
        <Row>
          {jobPosts.map((post, index) => {
            const isLastElement = jobPosts.length === index + 1;
            
            return (
              <Col 
                key={`${post.id}-${index}`} 
                xs={12} sm={6} md={6} lg={4} xl={3}
                className="mb-4"
                ref={isLastElement ? lastElementRef : null}
              >
                <Card 
                  className="h-100 border-0 shadow-sm custom-jumpit-card"
                  onClick={() => handleCardClick(post)}
                  role="button"
                >
                  {/* 썸네일 영역 */}
                  <div 
                    className="position-relative overflow-hidden" 
                    style={{ 
                      height: '160px', 
                      backgroundColor: '#f0f2f5', 
                      borderRadius: '12px 12px 0 0' 
                    }}
                  >
                    <div className="d-flex align-items-center justify-content-center h-100 opacity-25">
                      <Briefcase size={48} />
                    </div>
                    <div className="position-absolute top-0 start-0 m-2">
                      <Badge bg="white" className="text-primary shadow-sm px-2 py-1">
                        {post.sourcePlatform}
                      </Badge>
                    </div>
                    <div className="position-absolute top-0 end-0 m-2">
                      <ExternalLink size={16} className="text-white opacity-75" />
                    </div>
                  </div>

                  {/* 카드 본문 */}
                  <Card.Body className="d-flex flex-column p-3">
                    <div className="mb-1 text-primary fw-bold small">
                      {post.companyName}
                    </div>
                    
                    <Card.Title 
                      className="fw-bold fs-6 mb-2 text-truncate-2" 
                      style={{ minHeight: '2.8rem' }}
                      title={post.title}
                    >
                      {post.title}
                    </Card.Title>

                    {/* 기술 스택 */}
                    <div className="mb-3 mt-auto">
                      {post.techStacks && post.techStacks.length > 0 ? (
                        post.techStacks.slice(0, 3).map((tech, i) => (
                          <Badge 
                            key={i} 
                            bg="light" 
                            text="dark" 
                            className="fw-normal border me-1 mb-1 text-secondary" 
                            style={{ fontSize: '0.7rem' }}
                          >
                            {tech}
                          </Badge>
                        ))
                      ) : (
                        <small className="text-muted">기술 스택 정보 없음</small>
                      )}
                      {post.techStacks && post.techStacks.length > 3 && (
                        <Badge 
                          bg="light" 
                          text="muted" 
                          className="fw-normal me-1 mb-1" 
                          style={{ fontSize: '0.7rem' }}
                        >
                          +{post.techStacks.length - 3}
                        </Badge>
                      )}
                    </div>

                    {/* 하단 정보 */}
                    <div 
                      className="pt-2 border-top d-flex justify-content-between align-items-center text-muted" 
                      style={{ fontSize: '0.75rem' }}
                    >
                      <span className="d-flex align-items-center gap-1">
                        <MapPin size={12} />
                        {post.location || '전국'}
                      </span>
                      <span className="text-danger fw-medium d-flex align-items-center gap-1">
                        <Calendar size={12} />
                        {post.endDate}
                      </span>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            );
          })}
        </Row>

        {/* 로딩 표시 (추가 데이터) */}
        {loading && page > 0 && (
          <div className="text-center py-4">
            <Spinner animation="border" variant="primary" />
            <p className="mt-2 text-muted small">더 많은 공고를 불러오는 중...</p>
          </div>
        )}

        {/* 모든 데이터 로드 완료 */}
        {!hasMore && jobPosts.length > 0 && !loading && (
          <div className="text-center py-5">
            <p className="text-muted fw-bold mb-1">✨ 모든 공고를 확인했습니다!</p>
            <p className="text-secondary small">총 {totalElements}개의 채용 공고</p>
          </div>
        )}

        {/* 맨 위로 버튼 */}
        {jobPosts.length > 12 && (
          <button
            className="btn btn-primary rounded-circle position-fixed bottom-0 end-0 m-4 shadow-lg"
            style={{ width: '56px', height: '56px', zIndex: 1000 }}
            onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
            title="맨 위로"
          >
            ↑
          </button>
        )}
      </Container>
    </div>
  );
}

export default App;