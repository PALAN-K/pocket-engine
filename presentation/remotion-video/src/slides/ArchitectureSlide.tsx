import React from 'react';
import {AbsoluteFill, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

const Card: React.FC<{
  title: string; items: string[]; accentColor: string;
  tag: string; delay: number; side: 'left' | 'right';
}> = ({title, items, accentColor, tag, delay, side}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const progress = spring({fps, frame: frame - delay, config: {damping: 18, stiffness: 70}, durationInFrames: 30});
  const translateX = interpolate(progress, [0, 1], [side === 'left' ? -200 : 200, 0]);
  const opacity = interpolate(progress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});

  return (
    <div style={{
      background: '#1A1A1A', borderRadius: 16, padding: '40px 35px',
      width: 440, transform: `translateX(${translateX}px)`, opacity,
      borderTop: `5px solid ${accentColor}`,
    }}>
      <div style={{fontSize: 34, fontWeight: 'bold', color: '#fff', marginBottom: 15, fontFamily: 'Arial'}}>{title}</div>
      {items.map((item, i) => (
        <div key={i} style={{fontSize: 20, color: '#999', lineHeight: 2, fontFamily: 'Arial'}}>{item}</div>
      ))}
      <div style={{fontSize: 17, color: accentColor, marginTop: 18, fontFamily: 'Arial'}}>{tag}</div>
    </div>
  );
};

export const ArchitectureSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const arrowProgress = spring({fps, frame: frame - 40, config: {damping: 20}, durationInFrames: 25});
  const arrowWidth = interpolate(arrowProgress, [0, 1], [0, 80]);

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <div style={{position: 'absolute', top: 50, width: '100%', textAlign: 'center'}}>
        <FadeIn delay={0}>
          <span style={{fontSize: 48, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial'}}>
            2-App{' '}
          </span>
          <span style={{fontSize: 48, fontWeight: 'bold', color: '#EB0028', fontFamily: 'Arial'}}>
            Architecture
          </span>
        </FadeIn>
      </div>

      <div style={{display: 'flex', alignItems: 'center', gap: 40, marginTop: 40}}>
        <Card
          title="PocketMonitor" items={['Google Play Store', '디바이스 헬스 모니터링', '서버 상태 대시보드', '일일 안전 리포트', 'AdMob 광고 수익화']}
          accentColor="#3B82F6" tag="Play Store 정책 100% 준수" delay={10} side="left"
        />
        <div style={{display: 'flex', alignItems: 'center'}}>
          <div style={{width: arrowWidth, height: 3, backgroundColor: '#EB0028', borderRadius: 2}} />
          <div style={{
            width: 0, height: 0, borderTop: '8px solid transparent',
            borderBottom: '8px solid transparent', borderLeft: '12px solid #EB0028',
            opacity: arrowProgress,
          }} />
        </div>
        <Card
          title="PocketEngine" items={['사이드로드 (Firebase Hosting)', '원클릭 Ubuntu 24.04 설치', 'Dropbear SSH 서버', '7계층 Keep-Alive', '백그라운드 상시 가동']}
          accentColor="#EB0028" tag="광고 없음 · 완전 무료" delay={20} side="right"
        />
      </div>
    </AbsoluteFill>
  );
};
