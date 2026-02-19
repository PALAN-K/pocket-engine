import React from 'react';
import {AbsoluteFill, useCurrentFrame, spring, useVideoConfig, interpolate} from 'remotion';
import {FadeIn} from '../components/AnimatedText';

export const OutroSlide: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();

  // Logo circle
  const circleProgress = spring({fps, frame: frame - 5, config: {damping: 14, stiffness: 80}, durationInFrames: 25});
  const circleScale = interpolate(circleProgress, [0, 1], [0.5, 1]);
  const circleOpacity = interpolate(circleProgress, [0, 0.3], [0, 1], {extrapolateRight: 'clamp'});

  // Closing lines converge
  const lineProgress = spring({fps, frame: frame - 20, config: {damping: 25}, durationInFrames: 30});
  const lineWidth = interpolate(lineProgress, [0, 1], [0, 300]);

  // Subtle pulse
  const pulse = frame > 30 ? 1 + 0.012 * Math.sin(frame * 0.08) : 1;

  // Final fade out
  const fadeOut = interpolate(frame, [140, 165], [1, 0], {
    extrapolateLeft: 'clamp', extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill style={{backgroundColor: '#000', justifyContent: 'center', alignItems: 'center', opacity: fadeOut}}>
      {/* Logo */}
      <div style={{
        width: 100, height: 100, borderRadius: 50,
        border: '3px solid #EB0028', display: 'flex',
        justifyContent: 'center', alignItems: 'center',
        transform: `scale(${circleScale * pulse})`, opacity: circleOpacity,
      }}>
        <div style={{fontSize: 42, fontWeight: 'bold', color: '#EB0028', fontFamily: 'Arial'}}>P</div>
      </div>

      {/* Brand */}
      <FadeIn delay={15} direction="up">
        <div style={{
          fontSize: 42, fontWeight: 'bold', color: '#fff',
          fontFamily: 'Arial', letterSpacing: 5, marginTop: 20,
        }}>
          POCKET<span style={{color: '#EB0028'}}>SERVER</span>
        </div>
      </FadeIn>

      {/* Lines */}
      <div style={{display: 'flex', alignItems: 'center', gap: 20, marginTop: 25}}>
        <div style={{width: lineWidth, height: 2, backgroundColor: '#EB0028', borderRadius: 1}} />
        <div style={{width: lineWidth, height: 2, backgroundColor: '#EB0028', borderRadius: 1}} />
      </div>

      {/* Links */}
      <FadeIn delay={35} direction="up">
        <div style={{marginTop: 35, textAlign: 'center'}}>
          <div style={{fontSize: 22, color: '#999', fontFamily: 'Arial', lineHeight: 2}}>
            Play Store에서 <span style={{color: '#fff', fontWeight: 'bold'}}>PocketMonitor</span> 검색
          </div>
          <div style={{fontSize: 18, color: '#666', fontFamily: 'Arial'}}>
            pocket-server-palank.web.app
          </div>
        </div>
      </FadeIn>

      {/* Subscribe CTA */}
      <FadeIn delay={50} direction="up">
        <div style={{
          marginTop: 40, display: 'flex', gap: 25, alignItems: 'center',
        }}>
          <div style={{
            background: '#EB0028', borderRadius: 8, padding: '12px 30px',
            fontSize: 20, color: '#fff', fontWeight: 'bold', fontFamily: 'Arial',
          }}>
            구독
          </div>
          <div style={{
            border: '2px solid #333', borderRadius: 8, padding: '10px 25px',
            fontSize: 20, color: '#999', fontFamily: 'Arial',
          }}>
            좋아요
          </div>
          <div style={{
            border: '2px solid #333', borderRadius: 8, padding: '10px 25px',
            fontSize: 20, color: '#999', fontFamily: 'Arial',
          }}>
            알림 설정
          </div>
        </div>
      </FadeIn>

      {/* Copyright */}
      <FadeIn delay={65} direction="none">
        <div style={{
          position: 'absolute', bottom: 40, fontSize: 14,
          color: '#444', fontFamily: 'Arial',
        }}>
          © 2026 Palank. All rights reserved.
        </div>
      </FadeIn>
    </AbsoluteFill>
  );
};
