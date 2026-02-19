import React from 'react';
import {AbsoluteFill, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

const rows = [
  {name: 'PocketServer', hw: '$0 (구형폰)', monthly: '$0', yearly: '$0', highlight: true},
  {name: 'VPS (클라우드)', hw: '$0', monthly: '$4-6/월', yearly: '$48-72', highlight: false},
  {name: 'Raspberry Pi', hw: '$50-100', monthly: '~$1', yearly: '$62-112', highlight: false},
];

export const CostSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center'}}>
      <div style={{position: 'absolute', top: 70, width: '100%', textAlign: 'center'}}>
        <FadeIn delay={0}>
          <div style={{fontFamily: 'Arial'}}>
            <span style={{fontSize: 48, fontWeight: 'bold', color: '#fff'}}>비용 비교 — </span>
            <span style={{fontSize: 48, fontWeight: 'bold', color: '#EB0028'}}>연간 기준</span>
          </div>
        </FadeIn>
      </div>

      <div style={{marginTop: 40}}>
        {/* Header */}
        <FadeIn delay={10}>
          <div style={{display: 'flex', gap: 4, marginBottom: 6}}>
            {['솔루션', '하드웨어', '월 비용', '연간 총비용'].map((h, i) => (
              <div key={i} style={{
                width: i === 0 ? 260 : 200, background: '#EB0028',
                padding: '16px 20px', borderRadius: 4,
                fontSize: 20, fontWeight: 'bold', color: '#fff',
                textAlign: 'center', fontFamily: 'Arial',
              }}>{h}</div>
            ))}
          </div>
        </FadeIn>

        {/* Rows */}
        {rows.map((row, i) => {
          const rowDelay = 20 + i * 10;
          const progress = spring({fps, frame: frame - rowDelay, config: {damping: 18}, durationInFrames: 20});
          const opacity = interpolate(progress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});
          const translateY = interpolate(progress, [0, 1], [30, 0]);

          const pulseScale = row.highlight
            ? 1 + 0.03 * Math.sin((frame - 50) * 0.15) * (frame > 50 ? 1 : 0)
            : 1;

          return (
            <div key={i} style={{
              display: 'flex', gap: 4, marginBottom: 4,
              opacity, transform: `translateY(${translateY}px)`,
            }}>
              {[row.name, row.hw, row.monthly, row.yearly].map((cell, j) => (
                <div key={j} style={{
                  width: j === 0 ? 260 : 200,
                  background: row.highlight ? '#1A1A1A' : '#111',
                  padding: '16px 20px', borderRadius: 4,
                  fontSize: j === 3 && row.highlight ? 28 : 20,
                  fontWeight: j === 3 && row.highlight ? 'bold' : 'normal',
                  color: j === 3 && row.highlight ? '#EB0028' : (row.highlight ? '#fff' : '#666'),
                  textAlign: 'center', fontFamily: 'Arial',
                  textDecoration: !row.highlight && (j === 2 || j === 3) ? 'line-through' : 'none',
                  transform: j === 3 && row.highlight ? `scale(${pulseScale})` : 'none',
                }}>
                  {cell}
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};
