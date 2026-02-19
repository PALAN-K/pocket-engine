import React from 'react';
import {AbsoluteFill} from 'remotion';
import {FadeIn, ScaleIn} from '../components/AnimatedText';

const TempCard: React.FC<{
  temp: string; label: string; desc: string;
  color: string; delay: number;
}> = ({temp, label, desc, color, delay}) => (
  <ScaleIn delay={delay}>
    <div style={{
      background: '#1A1A1A', borderRadius: 16, padding: '40px 30px',
      width: 320, textAlign: 'center',
    }}>
      <div style={{fontSize: 64, fontWeight: 'bold', color, fontFamily: 'Arial'}}>{temp}</div>
      <div style={{fontSize: 28, fontWeight: 'bold', color: '#fff', fontFamily: 'Arial', marginTop: 8}}>{label}</div>
      <div style={{fontSize: 18, color: '#999', fontFamily: 'Arial', marginTop: 12, lineHeight: 1.6}}>{desc}</div>
    </div>
  </ScaleIn>
);

export const SafetySlide: React.FC = () => {
  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <div style={{position: 'absolute', top: 80, width: '100%', textAlign: 'center'}}>
        <FadeIn delay={0}>
          <div style={{fontFamily: 'Arial'}}>
            <span style={{fontSize: 56, fontWeight: 'bold', color: '#fff'}}>안전이 </span>
            <span style={{fontSize: 56, fontWeight: 'bold', color: '#EB0028'}}>최우선</span>
            <span style={{fontSize: 56, fontWeight: 'bold', color: '#fff'}}>입니다</span>
          </div>
        </FadeIn>
      </div>

      <div style={{display: 'flex', gap: 40, marginTop: 60}}>
        <TempCard temp="40°C" label="정상" desc="서버 정상 가동" color="#22C55E" delay={15} />
        <TempCard temp="45°C" label="주의" desc="푸시 알림 경고" color="#F59E0B" delay={25} />
        <TempCard temp="50°C" label="자동 정지" desc={'서버 즉시 중단\n온도 복귀 시 재시작'} color="#EB0028" delay={35} />
      </div>
    </AbsoluteFill>
  );
};
