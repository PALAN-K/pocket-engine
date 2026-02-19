import React from 'react';
import {AbsoluteFill, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

const layers = [
  {num: '1', label: 'Foreground Service', desc: 'OS 프로세스 보호'},
  {num: '2', label: 'Wake Lock', desc: '화면 꺼져도 CPU 유지'},
  {num: '3', label: 'WiFi Lock', desc: '네트워크 상시 연결'},
  {num: '4', label: 'Battery Optimization Exempt', desc: 'Doze 모드 우회'},
  {num: '5', label: 'Auto Restart', desc: '종료 시 자동 재시작'},
  {num: '6', label: 'Boot Receiver', desc: '재부팅 시 자동 시작'},
  {num: '7', label: 'Manufacturer Optimization', desc: '삼성/샤오미/화웨이 대응'},
];

export const KeepAliveSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <div style={{position: 'absolute', top: 60, width: '100%', textAlign: 'center'}}>
        <FadeIn delay={0}>
          <div style={{fontFamily: 'Arial'}}>
            <span style={{fontSize: 48, fontWeight: 'bold', color: '#EB0028'}}>7</span>
            <span style={{fontSize: 48, fontWeight: 'bold', color: '#fff'}}>계층 Keep-Alive 시스템</span>
          </div>
        </FadeIn>
      </div>

      <div style={{display: 'flex', flexDirection: 'column', gap: 10, marginTop: 50}}>
        {layers.map((layer, i) => {
          const layerDelay = 15 + i * 6;
          const progress = spring({fps, frame: frame - layerDelay, config: {damping: 16, stiffness: 80}, durationInFrames: 20});
          const translateX = interpolate(progress, [0, 1], [-400, 0]);
          const opacity = interpolate(progress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});

          return (
            <div key={i} style={{
              display: 'flex', alignItems: 'center', gap: 18,
              background: '#1A1A1A', borderRadius: 10, padding: '14px 30px',
              width: 750, transform: `translateX(${translateX}px)`, opacity,
            }}>
              <div style={{
                width: 38, height: 38, borderRadius: 19,
                backgroundColor: '#EB0028', display: 'flex',
                justifyContent: 'center', alignItems: 'center',
                fontSize: 18, color: '#fff', fontWeight: 'bold', fontFamily: 'Arial',
                flexShrink: 0,
              }}>
                {layer.num}
              </div>
              <div style={{fontSize: 22, color: '#fff', fontFamily: 'Arial', fontWeight: 'bold', width: 380}}>
                {layer.label}
              </div>
              <div style={{fontSize: 17, color: '#666', fontFamily: 'Arial'}}>
                {layer.desc}
              </div>
            </div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};
