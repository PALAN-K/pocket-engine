import React from 'react';
import {AbsoluteFill} from 'remotion';
import {FadeIn, ScaleIn} from '../components/AnimatedText';

const useCases = [
  {title: 'AI 에이전트', desc: 'PicoClaw, OpenClaw\n24시간 자동 응답'},
  {title: '자동화 도구', desc: 'n8n, Dify\n워크플로우 자동화'},
  {title: '웹 서버', desc: '바이브코딩 웹앱\n개인 포트폴리오'},
  {title: '개발 서버', desc: 'Git, Node.js, Python\n테스트 환경'},
  {title: '스마트홈 허브', desc: 'Home Assistant\nIoT 제어'},
  {title: '파일 서버', desc: '개인 클라우드\nNAS 대용'},
];

export const UseCasesSlide: React.FC = () => {
  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <div style={{position: 'absolute', top: 60, width: '100%', textAlign: 'center'}}>
        <FadeIn delay={0}>
          <div style={{fontFamily: 'Arial'}}>
            <span style={{fontSize: 48, fontWeight: 'bold', color: '#fff'}}>무엇이든 </span>
            <span style={{fontSize: 48, fontWeight: 'bold', color: '#EB0028'}}>설치</span>
            <span style={{fontSize: 48, fontWeight: 'bold', color: '#fff'}}>할 수 있습니다</span>
          </div>
        </FadeIn>
      </div>

      <div style={{
        display: 'flex', flexWrap: 'wrap', justifyContent: 'center',
        gap: 20, maxWidth: 1050, marginTop: 50,
      }}>
        {useCases.map((uc, i) => {
          const col = i % 3;
          const row = Math.floor(i / 3);
          const delay = 12 + (row * 3 + col) * 5;
          return (
            <ScaleIn key={i} delay={delay}>
              <div style={{
                background: '#1A1A1A', borderRadius: 14, padding: '28px 20px',
                width: 300, textAlign: 'center',
              }}>
                <div style={{fontSize: 26, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial'}}>
                  {uc.title}
                </div>
                <div style={{fontSize: 16, color: '#888', fontFamily: 'Arial', marginTop: 10, lineHeight: 1.5, whiteSpace: 'pre-line'}}>
                  {uc.desc}
                </div>
              </div>
            </ScaleIn>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};
