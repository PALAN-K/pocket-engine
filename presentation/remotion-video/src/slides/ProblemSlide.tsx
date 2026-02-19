import React from 'react';
import {AbsoluteFill, spring, useCurrentFrame, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

export const ProblemSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const numberProgress = spring({
    fps, frame: frame - 5,
    config: {damping: 15, stiffness: 80, mass: 0.5},
    durationInFrames: 35,
  });
  const scale = interpolate(numberProgress, [0, 1], [0.3, 1]);
  const opacity = interpolate(numberProgress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <div style={{
        fontSize: 140, fontWeight: 'bold', color: '#EB0028',
        fontFamily: 'Arial, sans-serif', transform: `scale(${scale})`, opacity,
      }}>
        수억 대
      </div>

      <FadeIn delay={25} direction="up">
        <div style={{
          fontSize: 40, color: '#fff', textAlign: 'center',
          lineHeight: 1.5, fontFamily: 'Arial, sans-serif', marginTop: 20,
        }}>
          전 세계 구형 스마트폰이<br/>서랍 속에 잠들어 있습니다
        </div>
      </FadeIn>

      <FadeIn delay={45} direction="up">
        <div style={{
          fontSize: 22, color: '#555', textAlign: 'center',
          fontFamily: 'Arial, sans-serif', marginTop: 35,
        }}>
          ARM 4-8코어 · 3-4GB RAM · WiFi · 소비전력 5W 미만
        </div>
      </FadeIn>
    </AbsoluteFill>
  );
};
