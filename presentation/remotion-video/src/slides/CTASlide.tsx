import React from 'react';
import {AbsoluteFill, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

export const CTASlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const titleScale = spring({fps, frame: frame - 5, config: {damping: 12, stiffness: 80, mass: 0.7}, durationInFrames: 30});
  const scale = interpolate(titleScale, [0, 1], [0.6, 1]);
  const opacity = interpolate(titleScale, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});

  const lineWidth = interpolate(
    spring({fps, frame: frame - 30, config: {damping: 20}, durationInFrames: 30}),
    [0, 1], [0, 400]
  );

  const pulse = 1 + 0.015 * Math.sin(frame * 0.1);

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <div style={{transform: `scale(${scale * pulse})`, opacity, textAlign: 'center'}}>
        <div style={{fontSize: 72, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial', lineHeight: 1.3}}>
          서랍 속 스마트폰을
        </div>
        <div style={{fontSize: 72, fontWeight: 'bold', color: '#EB0028', fontFamily: 'Arial', lineHeight: 1.3}}>
          깨워주세요
        </div>
      </div>

      <div style={{
        width: lineWidth, height: 3, backgroundColor: '#EB0028',
        marginTop: 35, borderRadius: 2,
      }} />

      <FadeIn delay={40} direction="up">
        <div style={{
          fontSize: 26, color: '#999', textAlign: 'center',
          fontFamily: 'Arial', marginTop: 30, lineHeight: 1.6,
        }}>
          PocketMonitor — Google Play Store<br/>
          PocketEngine — pocket-server-palank.web.app
        </div>
      </FadeIn>

      <FadeIn delay={55} direction="up">
        <div style={{
          fontSize: 22, color: '#3B82F6', textAlign: 'center',
          fontFamily: 'Arial', marginTop: 25,
        }}>
          전 기능 무료 · 광고만으로 운영 · 추가 비용 $0
        </div>
      </FadeIn>
    </AbsoluteFill>
  );
};
