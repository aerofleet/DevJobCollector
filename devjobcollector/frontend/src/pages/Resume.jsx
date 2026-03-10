import React, { useEffect, useMemo, useState } from 'react';
import ResumeNav from '../components/resume/ResumeNav';
import BasicInfo from '../components/resume/BasicInfo';
import TechStack from '../components/resume/TechStack';
import Projects from '../components/resume/Projects';
import Experience from '../components/resume/Experience';
import BottomBar from '../components/resume/BottomBar';
import { saveResume } from '../api/resumeApi';
import styles from '../styles/Resume.module.css';

const INITIAL_RESUME = {
  basicInfo: {},
  techStack: [],
  projects: [],
  experience: [],
};

const Resume = () => {
  const [activeSection, setActiveSection] = useState('basicInfo');
  const [resumeData, setResumeData] = useState(INITIAL_RESUME);
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
    setIsSaving(true);
    try {
      await saveResume(resumeData);
      window.alert('저장되었습니다.');
    } catch (error) {
      console.error('이력서 저장 실패:', error);
      window.alert('저장에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.wrapper}>
        <ResumeNav
          activeSection={activeSection}
          completionRate={completionRate}
          setActiveSection={setActiveSection}
        />
        <main className={styles.content}>
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
