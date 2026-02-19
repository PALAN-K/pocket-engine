import React from 'react';
import {AbsoluteFill, Img, staticFile, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

export const MonitorSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const imgProgress = spring({fps, frame: frame - 10, config: {damping: 16, stiffness: 60}, durationInFrames: 35});
  const imgX = interpolate(imgProgress, [0, 1], [300, 0]);
  const imgOpacity = interpolate(imgProgress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});

  return (
    <AbsoluteFill style={{backgroundColor: '#000', display: 'flex', flexDirection: 'row', alignItems: 'center'}}>
      <div style={{flex: 1, paddingLeft: 120}}>
        <FadeIn delay={5}>
          <div style={{fontSize: 22, color: '#EB0028', letterSpacing: 5, fontFamily: 'Arial', marginBottom: 12}}>
            MONITORING
          </div>
        </FadeIn>
        <FadeIn delay={12} direction="up">
          <div style={{fontSize: 56, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial', lineHeight: 1.3}}>
            디바이스 건강을<br/>한눈에 파악
          </div>
        </FadeIn>
        <FadeIn delay={30} direction="up">
          <div style={{fontSize: 24, color: '#999', fontFamily: 'Arial', lineHeight: 1.8, marginTop: 30}}>
            CPU, 메모리, 온도, 저장공간<br/>실시간 모니터링<br/>건강 점수 0-100 알고리즘
          </div>
        </FadeIn>
      </div>

      <div style={{
        flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center',
        transform: `translateX(${imgX}px)`, opacity: imgOpacity,
      }}>
        <Img src={staticFile('monitor-screenshot.png')} style={{height: 850, objectFit: 'contain'}} />
      </div>
    </AbsoluteFill>
  );
};
