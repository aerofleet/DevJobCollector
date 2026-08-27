import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ResumeNav from '../components/resume/ResumeNav';
import BasicInfo from '../components/resume/BasicInfo';
import TechStack from '../components/resume/TechStack';
import Projects from '../components/resume/Projects';
import Experience from '../components/resume/Experience';
import BottomBar from '../components/resume/BottomBar';
import { createResume, getResume, updateResume } from '../api/resumeApi';
import styles from '../styles/Resume.module.css';

const INITIAL_RESUME = {
  basicInfo: {},
  techStack: [],
  projects: [],
  experience: [],
};

const Resume = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const resumeId = searchParams.get('resumeId');
  const [activeSection, setActiveSection] = useState('basicInfo');
  const [resumeData, setResumeData] = useState(INITIAL_RESUME);
  const [resumeTitle, setResumeTitle] = useState('개발자 이력서');
  const [isLoading, setIsLoading] = useState(Boolean(resumeId));
  const [loadError, setLoadError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const completionRate = useMemo(() => {
    const checks = [
      Boolean(resumeData.basicInfo?.name?.trim()),
      Boolean(resumeData.basicInfo?.email?.trim()),
      resumeData.techStack.length > 0,
      resumeData.projects.length > 0,
      resumeData.experience.length > 0,
    ];
    return Math.round((checks.filter(Boolean).length / checks.length) * 100);
  }, [resumeData]);

  useEffect(() => {
    if (!resumeId) {
      return undefined;
    }

    let cancelled = false;
    const loadResume = async () => {
      setIsLoading(true);
      setLoadError('');
      try {
        const response = await getResume(resumeId);
        if (!cancelled) {
          setResumeTitle(response.title);
          setResumeData({ ...INITIAL_RESUME, ...response.content });
        }
      } catch (error) {
        if (!cancelled && error.response?.status !== 401) {
          setLoadError('이력서를 불러오지 못했습니다. 목록에서 다시 선택해 주세요.');
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    loadResume();
    return () => {
      cancelled = true;
    };
  }, [resumeId]);

  useEffect(() => {
    const sectionIds = ['basicInfo', 'techStack', 'projects', 'experience'];
    const handleScroll = () => {
      for (const id of sectionIds) {
        const element = document.getElementById(id);
        if (!element) {
          continue;
        }
        const rect = element.getBoundingClientRect();
        if (rect.top >= 0 && rect.top <= 220) {
          setActiveSection(id);
          break;
        }
      }
    };

    window.addEventListener('scroll', handleScroll);
    handleScroll();
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleSave = async () => {
    if (!resumeTitle.trim()) {
      window.alert('이력서 제목을 입력해 주세요.');
      return;
    }
    setIsSaving(true);
    try {
      const request = { title: resumeTitle, content: resumeData };
      if (resumeId) {
        await updateResume(resumeId, request);
      } else {
        await createResume(request);
      }
      navigate('/resumes');
    } catch (error) {
      console.error('이력서 저장 실패:', error);
      window.alert('저장에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <main className={styles.state}>이력서를 불러오는 중입니다.</main>;
  }

  if (loadError) {
    return (
      <main className={styles.state}>
        <p>{loadError}</p>
        <button type="button" onClick={() => navigate('/resumes')}>이력서 목록으로</button>
      </main>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.wrapper}>
        <ResumeNav
          activeSection={activeSection}
          completionRate={completionRate}
          setActiveSection={setActiveSection}
        />
        <main className={styles.content}>
          <label className={styles.titleField}>
            <span>이력서 제목</span>
            <input
              value={resumeTitle}
              maxLength={150}
              onChange={(event) => setResumeTitle(event.target.value)}
              placeholder="이력서 제목을 입력하세요"
            />
          </label>
          <div className={styles.card}>
            <BasicInfo
              data={resumeData.basicInfo}
              onChange={(data) => setResumeData((prev) => ({ ...prev, basicInfo: data }))}
            />
            <TechStack
              data={resumeData.techStack}
              onChange={(data) => setResumeData((prev) => ({ ...prev, techStack: data }))}
            />
            <Projects
              data={resumeData.projects}
              onChange={(data) => setResumeData((prev) => ({ ...prev, projects: data }))}
            />
            <Experience
              data={resumeData.experience}
              onChange={(data) => setResumeData((prev) => ({ ...prev, experience: data }))}
            />
          </div>
        </main>
      </div>
      <BottomBar onSave={handleSave} isSaving={isSaving} completionRate={completionRate} />
    </div>
  );
};

export default Resume;
