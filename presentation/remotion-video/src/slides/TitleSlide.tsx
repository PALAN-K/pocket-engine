import React from 'react';
import {AbsoluteFill, useCurrentFrame, interpolate, spring, useVideoConfig} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

export const TitleSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const lineScale = spring({fps, frame: frame - 20, config: {damping: 20}, durationInFrames: 30});
  const lineWidth = interpolate(lineScale, [0, 1], [0, 200]);

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <FadeIn delay={5} direction="up">
        <div style={{textAlign: 'center', fontFamily: 'Arial, sans-serif'}}>
          <div style={{fontSize: 72, fontWeight: 'bold', color: '#fff', lineHeight: 1.3}}>
            서랍 속 스마트폰이
          </div>
        </div>
      </FadeIn>

      <FadeIn delay={15} direction="up">
        <div style={{textAlign: 'center', fontFamily: 'Arial, sans-serif'}}>
          <div style={{fontSize: 72, fontWeight: 'bold', lineHeight: 1.3}}>
            <span style={{color: '#EB0028'}}>나만의 서버</span>
            <span style={{color: '#fff'}}>가 됩니다</span>
          </div>
        </div>
      </FadeIn>

      <div style={{
        width: lineWidth, height: 3, backgroundColor: '#EB0028',
        marginTop: 30, borderRadius: 2,
      }} />

      <FadeIn delay={35} direction="up">
        <div style={{
          fontSize: 28, color: '#888', textAlign: 'center',
          marginTop: 25, fontFamily: 'Arial, sans-serif',
        }}>
          PocketServer — 구형 스마트폰을 리눅스 서버로 변환하는 원클릭 앱
        </div>
      </FadeIn>
    </AbsoluteFill>
  );
};
