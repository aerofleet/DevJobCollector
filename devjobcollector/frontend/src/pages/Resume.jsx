import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ResumeNav from '../components/resume/ResumeNav';
import BasicInfo from '../components/resume/BasicInfo';
import TechStack from '../components/resume/TechStack';
import Projects from '../components/resume/Projects';
import { CareerProfile } from '../components/resume/AnalysisFields';
import Experience from '../components/resume/Experience';
import Attachments from '../components/resume/Attachments';
import BottomBar from '../components/resume/BottomBar';
import { AdditionalList, ResumePhoto, ResumeTextSection } from '../components/resume/AdditionalSections';
import ResumePreview from '../components/resume/ResumePreview';
import { resumeSections, resumeCompletion } from '../components/resume/resumeSections';
import { createResume, getResume, updateResume } from '../api/resumeApi';
import styles from '../styles/Resume.module.css';

const INITIAL_RESUME = {
  basicInfo: {},
  careerProfile: {},
  techStack: [],
  projects: [],
  experience: [],
  education: [], links: [], training: [], activities: [], introduction: '', coverLetter: '',
};

const Resume = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const resumeId = searchParams.get('resumeId');
  const persistedId = useRef(resumeId);
  const locallyCreatedId = useRef(null);
  const [attachmentBusy, setAttachmentBusy] = useState(false);
  const [activeSection, setActiveSection] = useState('basicInfo');
  const [resumeData, setResumeData] = useState(INITIAL_RESUME);
  const [resumeTitle, setResumeTitle] = useState('개발자 이력서');
  const [isLoading, setIsLoading] = useState(Boolean(resumeId));
  const [loadError, setLoadError] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [enabledSections, setEnabledSections] = useState(['introduction', 'links']);
  const completion = useMemo(() => resumeCompletion(resumeData), [resumeData]);
  const changeSection = (key, value) => setResumeData(prev => ({ ...prev, [key]: value }));
  const toggleSection = id => {
    setEnabledSections(prev => prev.includes(id) ? prev.filter(value => value !== id) : [...prev, id]);
  };

  const completionRate = useMemo(() => {
    return Math.round(completion.filter(item => item.complete).length / completion.length * 100);
  }, [completion]);

  useEffect(() => {
    persistedId.current = resumeId;
    if (resumeId && resumeId === locallyCreatedId.current) return undefined;
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
          setEnabledSections(resumeSections.filter(section => section.optional && (section.id === 'photo'
            ? response.content.basicInfo?.photo : response.content[section.id]?.length)).map(section => section.id));
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
    const sectionIds = resumeSections.map(section => section.id);
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
  }, [enabledSections, isLoading]);

  const handleSave = async (analyze = false) => {
    if (!resumeTitle.trim()) {
      window.alert('이력서 제목을 입력해 주세요.');
      return;
    }
    for (const [key, label] of [['education', '학력'], ['experience', '경력'], ['projects', '프로젝트'], ['training', '교육']]) {
      if (resumeData[key]?.some(item => !item.isCurrent && item.startDate && item.endDate && item.startDate > item.endDate)) {
        window.alert(`${label} 종료일은 시작일 이후로 입력해 주세요.`); return;
      }
    }
    setIsSaving(true);
    try {
      const request = { title: resumeTitle, content: resumeData };
      if (persistedId.current) {
        await updateResume(persistedId.current, request);
      } else {
        const created = await createResume(request);
        persistedId.current = String(created.id);
      }
      navigate(analyze ? `/resume-analysis?resumeId=${persistedId.current}` : '/resumes');
    } catch (error) {
      console.error('이력서 저장 실패:', error);
      window.alert('저장에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  const ensureResume = async () => {
    if (persistedId.current) return persistedId.current;
    if (!resumeTitle.trim()) throw new Error('이력서 제목을 입력한 뒤 파일을 추가해 주세요.');
    const created = await createResume({ title: resumeTitle, content: resumeData });
    persistedId.current = String(created.id);
    locallyCreatedId.current = String(created.id);
    navigate(`/resume?resumeId=${created.id}`, { replace: true });
    return created.id;
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
          enabledSections={enabledSections} onToggle={toggleSection} completion={completion}
        />
        <main className={styles.content}>
          <div className={styles.editorIntro}><span>MY CAREER</span><h1>나를 소개하는 이력서</h1><p>경험과 성과를 차근차근 정리해 보세요. 필요한 항목은 왼쪽 항목 편집에서 추가할 수 있어요.</p></div>
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
            {enabledSections.includes('photo') && <ResumePhoto value={resumeData.basicInfo.photo} onBusyChange={setPhotoBusy} onChange={photo => setResumeData(prev => ({ ...prev, basicInfo: { ...prev.basicInfo, photo } }))} />}
            {enabledSections.includes('introduction') && <ResumeTextSection id="introduction" title="간단소개" data={resumeData.introduction} onChange={value => changeSection('introduction', value)} hint="어떤 문제를 해결하는 개발자인지 간결하게 소개해 주세요." />}
            <CareerProfile data={resumeData.careerProfile || {}} onChange={data => setResumeData(prev => ({ ...prev, careerProfile: data }))} />
            <TechStack
              data={resumeData.techStack}
              onChange={(data) => setResumeData((prev) => ({ ...prev, techStack: data }))}
            />
            {enabledSections.includes('links') && <AdditionalList id="links" data={resumeData.links} onChange={value => changeSection('links', value)} />}
            <AdditionalList id="education" data={resumeData.education} onChange={value => changeSection('education', value)} />
            <Experience
              data={resumeData.experience}
              onChange={(data) => setResumeData((prev) => ({ ...prev, experience: data }))}
            />
            <Projects
              data={resumeData.projects}
              onChange={(data) => setResumeData((prev) => ({ ...prev, projects: data }))}
            />
            {['training', 'activities'].filter(id => enabledSections.includes(id)).map(id => <AdditionalList key={id} id={id} data={resumeData[id]} onChange={value => changeSection(id, value)} />)}
            {enabledSections.includes('coverLetter') && <ResumeTextSection id="coverLetter" title="자기소개서" data={resumeData.coverLetter} onChange={value => changeSection('coverLetter', value)} hint="지원 동기와 경험, 앞으로의 목표를 내 이야기로 작성해 주세요." />}
            <Attachments resumeId={resumeId} ensureResume={ensureResume} onBusyChange={setAttachmentBusy} disabled={isSaving} />
          </div>
        </main>
      </div>
      <BottomBar onSave={() => handleSave(false)} onAnalyze={() => handleSave(true)} onPreview={() => setPreviewOpen(true)} isSaving={isSaving || attachmentBusy || photoBusy} completionRate={completionRate} />
      {previewOpen && <ResumePreview title={resumeTitle} data={resumeData} onClose={() => setPreviewOpen(false)} />}
    </div>
  );
};

export default Resume;
