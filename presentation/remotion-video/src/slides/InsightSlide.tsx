import React from 'react';
import {AbsoluteFill} from 'remotion';
import {FadeIn, ScaleIn} from '../components/AnimatedText';

export const InsightSlide: React.FC = () => {
  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <FadeIn delay={5} direction="up">
        <div style={{
          fontSize: 56, fontWeight: 'bold', color: '#fff',
          textAlign: 'center', fontFamily: 'Arial, sans-serif',
        }}>
          그 스마트폰은
        </div>
      </FadeIn>

      <ScaleIn delay={18}>
        <div style={{
          fontSize: 80, fontWeight: 'bold', color: '#EB0028',
          textAlign: 'center', fontFamily: 'Arial, sans-serif', marginTop: 10,
        }}>
          충분히 강력합니다
        </div>
      </ScaleIn>

      <FadeIn delay={40} direction="up">
        <div style={{
          fontSize: 30, color: '#999', textAlign: 'center',
          fontFamily: 'Arial, sans-serif', marginTop: 40, lineHeight: 1.5,
        }}>
          AI 에이전트, 웹서버, 자동화 도구를<br/>24시간 돌릴 수 있는 성능
        </div>
      </FadeIn>
    </AbsoluteFill>
  );
};
