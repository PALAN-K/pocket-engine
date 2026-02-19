import React from 'react';
import {AbsoluteFill, useCurrentFrame, interpolate, spring, useVideoConfig} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

export const SolutionSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const titleProgress = spring({fps, frame: frame - 8, config: {damping: 14, stiffness: 90}, durationInFrames: 25});
  const scale = interpolate(titleProgress, [0, 1], [0.5, 1]);
  const opacity = interpolate(titleProgress, [0, 0.4], [0, 1], {extrapolateRight: 'clamp'});

  const lineWidth = interpolate(
    spring({fps, frame: frame - 25, config: {damping: 20}, durationInFrames: 30}),
    [0, 1], [0, 300]
  );

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <FadeIn delay={0} direction="none">
        <div style={{
          fontSize: 24, color: '#EB0028', letterSpacing: 8,
          fontFamily: 'Arial, sans-serif', textAlign: 'center',
        }}>
          SOLUTION
        </div>
      </FadeIn>

      <div style={{
        fontSize: 90, fontWeight: 'bold', color: '#fff',
        fontFamily: 'Arial, sans-serif', marginTop: 10,
        transform: `scale(${scale})`, opacity,
      }}>
        PocketServer
      </div>

      <div style={{
        width: lineWidth, height: 3, backgroundColor: '#EB0028',
        marginTop: 20, borderRadius: 2,
      }} />

      <FadeIn delay={35} direction="up">
        <div style={{
          fontSize: 34, color: '#ccc', textAlign: 'center',
          fontFamily: 'Arial, sans-serif', marginTop: 25, lineHeight: 1.6,
        }}>
          버튼 하나로 구형 스마트폰을<br/>Ubuntu 리눅스 서버로 변환
        </div>
      </FadeIn>
    </AbsoluteFill>
  );
};
