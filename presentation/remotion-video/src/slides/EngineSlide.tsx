import React from 'react';
import {AbsoluteFill, Img, staticFile, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

export const EngineSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  const imgProgress = spring({fps, frame: frame - 10, config: {damping: 16, stiffness: 60}, durationInFrames: 35});
  const imgX = interpolate(imgProgress, [0, 1], [-300, 0]);
  const imgOpacity = interpolate(imgProgress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});

  return (
    <AbsoluteFill style={{backgroundColor: '#000', display: 'flex', flexDirection: 'row', alignItems: 'center'}}>
      <div style={{
        flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center',
        transform: `translateX(${imgX}px)`, opacity: imgOpacity,
      }}>
        <Img src={staticFile('engine-screenshot.png')} style={{height: 850, objectFit: 'contain'}} />
      </div>

      <div style={{flex: 1, paddingRight: 120}}>
        <FadeIn delay={5}>
          <div style={{fontSize: 22, color: '#EB0028', letterSpacing: 5, fontFamily: 'Arial', marginBottom: 12}}>
            ONE-CLICK SETUP
          </div>
        </FadeIn>
        <FadeIn delay={12} direction="up">
          <div style={{fontSize: 56, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial', lineHeight: 1.3}}>
            버튼 하나로<br/>서버 설치 완료
          </div>
        </FadeIn>
        <FadeIn delay={30} direction="up">
          <div style={{fontSize: 24, color: '#999', fontFamily: 'Arial', lineHeight: 1.8, marginTop: 30}}>
            사양 자동 검사<br/>Ubuntu 24.04 LTS 설치<br/>Dropbear SSH 자동 설정<br/>약 3분 소요
          </div>
        </FadeIn>
      </div>
    </AbsoluteFill>
  );
};
